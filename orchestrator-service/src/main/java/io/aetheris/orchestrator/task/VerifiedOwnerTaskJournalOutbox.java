package io.aetheris.orchestrator.task;

import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Manual, isolated, database-backed task journal outbox.
 *
 * NOT a @Service, scheduler, controller, servlet or native desktop command.
 * Production TaskEventStreamService and ownerless HTTP routes are unchanged
 * and remain quarantined. The owning task's journal row and outbox ID must
 * be written within the SAME caller-supplied, real Spring transaction.
 *
 * A successful callback is named HANDOFF_ATTEMPTED: it is not evidence that
 * a desktop, SSE client or real-world action received/completed anything.
 * Event state and message content cannot be used as verified task evidence.
 */
public final class VerifiedOwnerTaskJournalOutbox {
    public static final int MAX_ATTEMPTS = 5;
    public static final Duration LEASE_DURATION = Duration.ofSeconds(30);
    public static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

    public enum DrainResult {
        NOTHING_READY, HANDOFF_ATTEMPTED, RETRY_SCHEDULED, PARKED, STALE_CLAIM
    }

    /**
     * An opaque database lease. Not an authentication credential or task
     * access grant; it cannot be used to read any owner/project data.
     */
    public record Claim(UUID outboxId, UUID journalEventId,
                        UUID taskId, UUID leaseToken, int attempt) {}

    private final TaskRepository tasks;
    private final TaskEventJournalService journal;
    private final TaskJournalOutboxRepository outbox;
    private final TransactionTemplate requiresNew;
    private final Clock clock;

    public VerifiedOwnerTaskJournalOutbox(TaskRepository tasks,
            TaskEventJournalService journal, TaskJournalOutboxRepository outbox,
            PlatformTransactionManager manager) {
        this(tasks, journal, outbox, manager, Clock.systemUTC());
    }

    // Deterministic clock for tests. No time is inferred from untrusted event.
    VerifiedOwnerTaskJournalOutbox(TaskRepository tasks,
            TaskEventJournalService journal, TaskJournalOutboxRepository outbox,
            PlatformTransactionManager manager, Clock clock) {
        this.tasks = Objects.requireNonNull(tasks);
        this.journal = Objects.requireNonNull(journal);
        this.outbox = Objects.requireNonNull(outbox);
        this.clock = Objects.requireNonNull(clock);
        this.requiresNew = new TransactionTemplate(Objects.requireNonNull(manager));
        this.requiresNew.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Must be invoked *within* the same transaction that writes the journal.
     * No partial journal/outbox write can commit independently.
     * Only owner-global tasks are eligible until project binding is audited.
     */
    public UUID appendInCurrentTransaction(TaskEvent event) {
        if (event == null || event.taskId() == null ||
                event.timestamp() == null || event.state() == null ||
                !TransactionSynchronizationManager.isActualTransactionActive() ||
                !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("TRUSTED_TRANSACTION_REQUIRED");
        }
        TaskEntity stored = tasks.findById(event.taskId())
                .orElseThrow(VerifiedOwnerTaskJournalOutbox::unavailable);
        if (!eligible(stored)) throw unavailable();

        UUID journalId = journal.appendReturningId(event);
        outbox.save(new TaskJournalOutboxEntity(
                UUID.randomUUID(), journalId, event.taskId(), storageNow()));
        return journalId;
    }

    /**
     * Database PESSIMISTIC_WRITE claim, committed separately before any
     * external/internal callback. Expired IN_FLIGHT work becomes eligible
     * again after a crash. Lease nonce protects against stale acknowledgements.
     */
    public Claim claimReady() {
        return requiresNew.execute(status -> {
            Instant now = storageNow();
            var rows = outbox.claimable(TaskJournalOutboxEntity.State.PENDING,
                    TaskJournalOutboxEntity.State.IN_FLIGHT, now, PageRequest.of(0, 1));
            if (rows.isEmpty()) return null;
            TaskJournalOutboxEntity row = rows.get(0);
            if (row.getAttempts() >= MAX_ATTEMPTS) {
                row.park();
                return null;
            }
            UUID nonce = UUID.randomUUID();
            row.claim(now, now.plus(LEASE_DURATION), nonce);
            return new Claim(row.getId(), row.getJournalEventId(), row.getTaskId(),
                    nonce, row.getAttempts());
        });
    }

    public boolean acknowledgeInternalHandoff(Claim claim) {
        if (claim == null) return false;
        return Boolean.TRUE.equals(requiresNew.execute(status -> {
            var row = outbox.findById(claim.outboxId()).orElse(null);
            if (row == null || !row.ownsLease(claim.leaseToken(), storageNow()))
                return false;
            row.handoffAttempted();
            return true;
        }));
    }

    /**
     * Backoff is bounded; after MAX_ATTEMPTS the record is PARKED for manual
     * review. No exception messages or callback payloads are persisted.
     */
    public boolean retryOrPark(Claim claim) {
        if (claim == null) return false;
        return Boolean.TRUE.equals(requiresNew.execute(status -> {
            var row = outbox.findById(claim.outboxId()).orElse(null);
            if (row == null || !row.ownsLease(claim.leaseToken(), clock.instant()))
                return false;
            if (row.getAttempts() >= MAX_ATTEMPTS) {
                row.park();
            } else {
                long seconds = Math.min(MAX_BACKOFF.getSeconds(),
                        1L << Math.min(row.getAttempts(), 6));
                row.scheduleRetry(storageNow().plusSeconds(seconds));
            }
            return true;
        }));
    }

    /**
     * One bounded manual delivery attempt, no hidden background work.
     * The callback should invoke a reviewed owner-scoped relay, NOT the
     * preexisting global SSE broadcaster.
     */
    public DrainResult dispatchOnce(Consumer<UUID> ownerScopedCallback) {
        Objects.requireNonNull(ownerScopedCallback);
        Claim claim = claimReady();
        if (claim == null) return DrainResult.NOTHING_READY;
        // Recheck owner/project from database after claim. Unauthorized
        // records are parked, never forwarded to a subscriber.
        TaskEntity task = tasks.findById(claim.taskId()).orElse(null);
        if (task == null || !eligible(task)) {
            return parkUnauthorized(claim) ? DrainResult.PARKED : DrainResult.STALE_CLAIM;
        }
        try {
            ownerScopedCallback.accept(claim.taskId());
            return acknowledgeInternalHandoff(claim) ?
                    DrainResult.HANDOFF_ATTEMPTED : DrainResult.STALE_CLAIM;
        } catch (RuntimeException ignored) {
            // Suppress sensitive callback exceptions; no private log output.
            if (!retryOrPark(claim)) return DrainResult.STALE_CLAIM;
            return claim.attempt() >= MAX_ATTEMPTS ?
                    DrainResult.PARKED : DrainResult.RETRY_SCHEDULED;
        }
    }

    private boolean parkUnauthorized(Claim claim) {
        return Boolean.TRUE.equals(requiresNew.execute(status -> {
            var row = outbox.findById(claim.outboxId()).orElse(null);
            if (row == null || !row.ownsLease(claim.leaseToken(), clock.instant()))
                return false;
            row.park();
            return true;
        }));
    }

    /**
     * PostgreSQL/H2 TIMESTAMP(6) commonly stores microseconds. Normalize
     * enqueue and lease comparisons to avoid a newly committed row being
     * just ahead of its own unrounded in-memory clock by one microsecond.
     */
    private Instant storageNow() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static boolean eligible(TaskEntity task) {
        return task.getOwnerUserId() != null && task.getOwnerUserId() > 0L &&
                task.getOwnerProjectId() == null;
    }

    private static NoSuchElementException unavailable() {
        return new NoSuchElementException("TASK_NOT_AVAILABLE");
    }
}
