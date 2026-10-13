package io.aetheris.orchestrator.task;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Inert, non-Spring-registered transaction boundary for future trusted
 * mission-journal notifications. This class never reads a journal, token,
 * secret, task payload, or HTTP request. It stages only task UUIDs.
 *
 * A persisted journal event MUST NOT be announced before commit. Neither
 * a rollback nor a non-transactional call can trigger the notifier.
 *
 * Not wired to TaskService, legacy SSE, a controller or Syntra. A future
 * audited internal-only producer must call stageAfterJournalAppend() after
 * TaskEventJournalService.append() and within the same transaction.
 */
public final class OwnerTaskPostCommitGate {
    public static final int MAX_TASK_IDS_PER_TRANSACTION = 128;

    public enum StageResult {
        REGISTERED,
        COALESCED,
        DENIED_NO_TRANSACTION,
        DENIED_CAPACITY,
        DENIED_INVALID_TASK
    }

    private final Consumer<UUID> committedTaskNotification;

    public OwnerTaskPostCommitGate(Consumer<UUID> committedTaskNotification) {
        this.committedTaskNotification = Objects.requireNonNull(committedTaskNotification);
    }

    public StageResult stageAfterJournalAppend(UUID taskId) {
        if (taskId == null) return StageResult.DENIED_INVALID_TASK;
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return StageResult.DENIED_NO_TRANSACTION;
        }

        Pending pending = null;
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            if (synchronization instanceof Pending candidate && candidate.gate == this) {
                pending = candidate;
                break;
            }
        }
        if (pending == null) {
            pending = new Pending(this);
            TransactionSynchronizationManager.registerSynchronization(pending);
        }
        return pending.add(taskId);
    }

    /**
     * Transaction-scoped identity-based de-duplication. No state is stored in
     * a singleton/global map or leaked across transactions or threads.
     */
    private static final class Pending implements TransactionSynchronization {
        private final OwnerTaskPostCommitGate gate;
        private final Set<UUID> taskIds = new LinkedHashSet<>();
        private Pending(OwnerTaskPostCommitGate gate) { this.gate = gate; }

        StageResult add(UUID id) {
            if (taskIds.contains(id)) return StageResult.COALESCED;
            if (taskIds.size() >= MAX_TASK_IDS_PER_TRANSACTION)
                return StageResult.DENIED_CAPACITY;
            taskIds.add(id);
            return StageResult.REGISTERED;
        }

        @Override
        public void afterCompletion(int status) {
            try {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    for (UUID taskId : taskIds) {
                        try {
                            gate.committedTaskNotification.accept(taskId);
                        } catch (RuntimeException ignored) {
                            // No task contents, tokens, errors, or exception
                            // messages are allowed to leak into caller logs.
                            // Durable outbox/replay is a separate requirement.
                        }
                    }
                }
            } finally {
                taskIds.clear();
            }
        }
    }
}
