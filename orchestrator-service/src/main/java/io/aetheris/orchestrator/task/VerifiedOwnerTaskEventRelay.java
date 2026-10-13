package io.aetheris.orchestrator.task;

import io.aetheris.orchestrator.security.OwnerBoundResourcePolicy;
import io.aetheris.orchestrator.security.RuntimeAccessTokenVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Isolated, signed-owner task event relay for a future governed SSE adapter.
 *
 * This is NOT a Spring component, servlet endpoint, WebSocket, or task-event
 * publisher hook. The existing /tasks, /missions and /live HTTP quarantine
 * remains active. Only a server-internal caller may invoke onJournalChanged
 * AFTER TaskEventJournalService persisted a real event. The relay retrieves
 * events from the database itself; it accepts NO caller-supplied TaskEvent.
 *
 * Every lease is short-lived, bounded, owner-specific and read-only.
 * Events are sanitized and marked as recorded, NOT proof of actual outcomes.
 *
 * Revocation hooks are preparatory: external token revocation, distributed
 * logout, project membership, reconnect and streaming are NOT wired.
 */
public final class VerifiedOwnerTaskEventRelay {
    public static final int MAX_SUBSCRIPTIONS = 32;
    public static final int MAX_EVENTS_PER_DISPATCH = 100;
    public static final Duration MAX_LEASE = Duration.ofSeconds(60);

    private final TaskRepository tasks;
    private final TaskEventJournalService journal;
    private final RuntimeAccessTokenVerifier verifier;
    private final Clock clock;
    private final Map<UUID, List<Lease>> subscriptions = new HashMap<>();

    public VerifiedOwnerTaskEventRelay(TaskRepository tasks,
                                        TaskEventJournalService journal,
                                        RuntimeAccessTokenVerifier verifier) {
        this(tasks, journal, verifier, Clock.systemUTC());
    }

    // Package-scoped to support deterministic time/expiry testing.
    VerifiedOwnerTaskEventRelay(TaskRepository tasks, TaskEventJournalService journal,
                                 RuntimeAccessTokenVerifier verifier, Clock clock) {
        this.tasks = Objects.requireNonNull(tasks);
        this.journal = Objects.requireNonNull(journal);
        this.verifier = Objects.requireNonNull(verifier);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * JWT verification occurs before DB lookup. Only the token's verified
     * numeric owner (NOT a request header, task ID or user-controlled value)
     * determines access. Project-bound and unowned tasks are denied.
     */
    public Lease subscribe(String signedAccessToken, UUID taskId,
                           Consumer<RecordedTaskProgress> sink) {
        var principal = verifier.verify(signedAccessToken, RuntimeAccessTokenVerifier.READ_SCOPE);
        if (taskId == null || sink == null) throw unavailable();
        TaskEntity task = tasks.findById(taskId)
                .orElseThrow(VerifiedOwnerTaskEventRelay::unavailable);
        if (!OwnerBoundResourcePolicy.permitted(task.getOwnerUserId(),
                task.getOwnerProjectId(), principal.identityAccountId(), null)) {
            throw unavailable();
        }
        Instant now = clock.instant();
        Instant endsAt = now.plus(MAX_LEASE);
        if (principal.expiresAt().isBefore(endsAt)) endsAt = principal.expiresAt();
        if (!endsAt.isAfter(now)) throw unavailable();

        // A new subscription sees only later journaled updates. Historical
        // playback must go through the separately authenticated history reader.
        List<TaskEvent> previous = journal.history(taskId);
        Instant lastSeen = previous.stream()
                .filter(e -> e != null && taskId.equals(e.taskId()))
                .map(TaskEvent::timestamp)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(Instant.MIN);

        synchronized (this) {
            if (subscriptionCount() >= MAX_SUBSCRIPTIONS) throw unavailable();
            Lease lease = new Lease(taskId, principal.identityAccountId(),
                    endsAt, lastSeen, sink);
            subscriptions.computeIfAbsent(taskId, ignored -> new ArrayList<>()).add(lease);
            return lease;
        }
    }

    /**
     * Called only by a future trusted post-commit journal adapter.
     * A task UUID is a lookup key, NOT an authorization grant. No sensitive
     * message/metadata is forwarded and no unpersisted payload is accepted.
     */
    public void onJournalChanged(UUID taskId) {
        if (taskId == null) return;
        List<Lease> listeners;
        synchronized (this) {
            listeners = List.copyOf(subscriptions.getOrDefault(taskId, List.of()));
        }
        if (listeners.isEmpty()) return;

        TaskEntity task = tasks.findById(taskId).orElse(null);
        // No journal query if the task disappeared from DB.
        if (task == null) {
            listeners.forEach(Lease::close);
            return;
        }
        List<TaskEvent> history = journal.history(taskId);
        // History is chronological, at most 200 items. Dispatch at most 100.
        for (Lease lease : listeners) {
            lease.deliver(task, history, clock.instant());
        }
    }

    /** Optional future logout/emergency-stop hook; does not itself alter JWTs. */
    public void revokeOwner(long verifiedOwnerId) {
        List<Lease> owned = new ArrayList<>();
        synchronized (this) {
            for (List<Lease> leases : subscriptions.values()) {
                for (Lease lease : leases) {
                    if (lease.ownerId == verifiedOwnerId) owned.add(lease);
                }
            }
        }
        owned.forEach(Lease::close);
    }

    public synchronized int activeSubscriptions() {
        return subscriptionCount();
    }

    private int subscriptionCount() {
        int count = 0;
        for (List<Lease> current : subscriptions.values()) count += current.size();
        return count;
    }

    private synchronized void forget(Lease lease) {
        List<Lease> current = subscriptions.get(lease.taskId);
        if (current == null) return;
        current.remove(lease);
        if (current.isEmpty()) subscriptions.remove(lease.taskId);
    }

    private static NoSuchElementException unavailable() {
        return new NoSuchElementException("TASK_NOT_AVAILABLE");
    }

    private static RecordedTaskProgress sanitize(TaskEvent source) {
        if (source == null || source.taskId() == null ||
                source.timestamp() == null || source.state() == null) return null;
        String agent = source.agentId();
        if (agent != null && (agent.length() > 80 ||
                !agent.matches("[a-z0-9][a-z0-9-]{0,79}"))) agent = null;
        return new RecordedTaskProgress(source.taskId(), source.timestamp(),
                source.state(), agent,
                "PERSISTED_JOURNAL_NOT_VERIFIED_OUTCOME");
    }

    /**
     * Minimal owner-facing event. No message, command, metadata or token.
     */
    public record RecordedTaskProgress(UUID taskId, Instant timestamp,
                                       TaskState state, String agentId,
                                       String provenance) {}

    public final class Lease implements AutoCloseable {
        private final UUID taskId;
        private final long ownerId;
        private final Instant expiresAt;
        private final Consumer<RecordedTaskProgress> sink;
        private Instant lastSeen;
        private boolean closed;

        private Lease(UUID taskId, long ownerId, Instant expiresAt,
                      Instant lastSeen, Consumer<RecordedTaskProgress> sink) {
            this.taskId = taskId;
            this.ownerId = ownerId;
            this.expiresAt = expiresAt;
            this.lastSeen = lastSeen;
            this.sink = sink;
        }

        private synchronized void deliver(TaskEntity stored, List<TaskEvent> events, Instant now) {
            if (closed) return;
            if (!now.isBefore(expiresAt) ||
                    !OwnerBoundResourcePolicy.permitted(stored.getOwnerUserId(),
                            stored.getOwnerProjectId(), ownerId, null)) {
                close();
                return;
            }

            int sent = 0;
            for (TaskEvent event : events) {
                if (closed || sent >= MAX_EVENTS_PER_DISPATCH) break;
                if (event == null || !taskId.equals(event.taskId()) ||
                        event.timestamp() == null ||
                        !event.timestamp().isAfter(lastSeen)) continue;

                RecordedTaskProgress safe = sanitize(event);
                if (safe == null) continue;
                try {
                    sink.accept(safe);
                    lastSeen = event.timestamp();
                    sent++;
                } catch (RuntimeException failedConsumer) {
                    close();
                    break; // never leak callback messages, exceptions or tokens
                }
            }
        }

        public synchronized boolean isClosed() { return closed; }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            forget(this);
        }
    }
}
