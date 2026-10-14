package io.aetheris.orchestrator.task;

import io.aetheris.orchestrator.security.OwnerBoundResourcePolicy;
import io.aetheris.orchestrator.security.RuntimeAccessTokenVerifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Isolated, per-owner durable replay/ack primitive.
 *
 * NOT a Spring bean/controller, Tauri IPC handler, SSE listener, scheduler or
 * production session grant. Server-verifies a signed JWT on every operation;
 * never trusts request headers, a checkpoint UUID alone, or client-provided
 * account/project IDs. Only owner-global sequenced tasks are eligible.
 *
 * Acknowledgement means a caller DECLARED a journal event as received. No
 * proof of desktop display, tool completion, or downstream real-world work.
 */
public final class VerifiedOwnerDeliveryCheckpoint {
    public static final int MAX_PAGE = 100;

    private final TaskRepository tasks;
    private final TaskEventJournalRepository journal;
    private final OwnerTaskCheckpointRepository checkpoints;
    private final RuntimeAccessTokenVerifier verifier;
    private final TransactionTemplate writeTransaction;

    public VerifiedOwnerDeliveryCheckpoint(TaskRepository tasks,
            TaskEventJournalRepository journal,
            OwnerTaskCheckpointRepository checkpoints,
            RuntimeAccessTokenVerifier verifier,
            PlatformTransactionManager manager) {
        this.tasks = Objects.requireNonNull(tasks);
        this.journal = Objects.requireNonNull(journal);
        this.checkpoints = Objects.requireNonNull(checkpoints);
        this.verifier = Objects.requireNonNull(verifier);
        writeTransaction = new TransactionTemplate(Objects.requireNonNull(manager));
        writeTransaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    public CheckpointHandle open(String signedToken, UUID taskId) {
        long ownerId = verifiedOwner(signedToken, RuntimeAccessTokenVerifier.WRITE_SCOPE);
        requireOwnerTask(taskId, ownerId);
        OwnerTaskCheckpointEntity record = new OwnerTaskCheckpointEntity(
                UUID.randomUUID(), ownerId, taskId, Instant.now());
        checkpoints.saveAndFlush(record);
        return new CheckpointHandle(record.getId(), taskId, 0L,
                "CLIENT_DECLARED_ACK_NOT_ATTESTED");
    }

    /**
     * Re-check both signed account identity and durable task ownership on
     * every reconnect/page. SQL returns only state/time/agent/sequence fields.
     * No event message or serialized metadata is loaded.
     */
    public ReplayBatch resume(String signedToken, UUID checkpointId, int limit) {
        long ownerId = verifiedOwner(signedToken, RuntimeAccessTokenVerifier.READ_SCOPE);
        if (limit < 1 || limit > MAX_PAGE || checkpointId == null) throw unavailable();
        OwnerTaskCheckpointEntity checkpoint = requireCheckpoint(checkpointId, ownerId);
        requireOwnerTask(checkpoint.getTaskId(), ownerId);

        List<TaskJournalSequencedRow> rows = journal.sequencedPageAfter(
                checkpoint.getTaskId(), checkpoint.getLastSequence(),
                PageRequest.of(0, limit + 1));
        boolean more = rows.size() > limit;
        List<SafeUpdate> safe = rows.subList(0, Math.min(limit, rows.size())).stream()
                .map(VerifiedOwnerDeliveryCheckpoint::redact)
                .toList();
        return new ReplayBatch(checkpointId, checkpoint.getLastSequence(),
                List.copyOf(safe), more, "OUTBOX_SEQUENCED_ONLY_ACK_NOT_ATTESTED");
    }

    /**
     * Internal-only live delivery page for a short-lived, already verified
     * principal. Re-checks expiration/read scope AND current database owner
     * on each poll. The HTTP adapter must never serialize this principal.
     *
     * A connection-local cursor avoids repeating already-sent frames, while
     * the durable checkpoint is never advanced without a client ACK. After
     * reconnect the client intentionally replays unacknowledged frames.
     */
    public ReplayBatch streamPage(
            RuntimeAccessTokenVerifier.VerifiedPrincipal principal,
            UUID checkpointId, long connectionLastSent, int limit) {
        if (principal == null || !principal.hasScope(RuntimeAccessTokenVerifier.READ_SCOPE)
                || !Instant.now().isBefore(principal.expiresAt())) {
            throw new RuntimeAccessTokenVerifier.RejectedToken();
        }
        if (checkpointId == null || connectionLastSent < 0
                || limit < 1 || limit > 20) throw unavailable();
        OwnerTaskCheckpointEntity checkpoint =
                requireCheckpoint(checkpointId, principal.identityAccountId());
        requireOwnerTask(checkpoint.getTaskId(), principal.identityAccountId());
        long since = Math.max(checkpoint.getLastSequence(), connectionLastSent);
        List<TaskJournalSequencedRow> rows = journal.sequencedPageAfter(
                checkpoint.getTaskId(), since, PageRequest.of(0, limit + 1));
        boolean more = rows.size() > limit;
        List<SafeUpdate> events = rows.subList(0, Math.min(rows.size(), limit))
                .stream().map(VerifiedOwnerDeliveryCheckpoint::redact).toList();
        return new ReplayBatch(checkpointId, checkpoint.getLastSequence(),
                List.copyOf(events), more, "OUTBOX_SEQUENCED_ONLY_ACK_NOT_ATTESTED");
    }

    /**
     * ACK must be for precisely the next sequenced event in this task.
     * A forged jump can never skip pending events. A repeated ACK of the
     * exact last event is idempotent; older, foreign and future IDs fail.
     * Database row lock serializes ACK writers across JVMs/instances.
     */
    public Acknowledgement acknowledge(String signedToken, UUID checkpointId,
                                       UUID eventId) {
        long ownerId = verifiedOwner(signedToken, RuntimeAccessTokenVerifier.WRITE_SCOPE);
        if (checkpointId == null || eventId == null) throw unavailable();
        return writeTransaction.execute(status -> {
            OwnerTaskCheckpointEntity checkpoint = checkpoints.lockById(checkpointId)
                    .orElseThrow(VerifiedOwnerDeliveryCheckpoint::unavailable);
            if (!Objects.equals(checkpoint.getOwnerUserId(), ownerId)) throw unavailable();
            requireOwnerTask(checkpoint.getTaskId(), ownerId);

            TaskJournalSequencedRow event = journal.sequencedCursorForTask(
                    eventId, checkpoint.getTaskId())
                    .orElseThrow(VerifiedOwnerDeliveryCheckpoint::unavailable);
            if (event.sequence() == null || event.sequence() < 1) throw unavailable();
            if (event.sequence() == checkpoint.getLastSequence()
                    && eventId.equals(checkpoint.getLastEventId())) {
                return Acknowledgement.DUPLICATE;
            }
            if (checkpoint.getLastSequence() == Long.MAX_VALUE ||
                    event.sequence() != checkpoint.getLastSequence() + 1L) {
                return Acknowledgement.OUT_OF_ORDER;
            }
            checkpoint.acknowledge(event.sequence(), eventId, Instant.now());
            return Acknowledgement.ADVANCED;
        });
    }

    private long verifiedOwner(String signedToken, String requiredScope) {
        return verifier.verify(signedToken, requiredScope).identityAccountId();
    }

    private OwnerTaskCheckpointEntity requireCheckpoint(UUID checkpointId, long ownerId) {
        OwnerTaskCheckpointEntity checkpoint = checkpoints.findById(checkpointId)
                .orElseThrow(VerifiedOwnerDeliveryCheckpoint::unavailable);
        if (!Objects.equals(checkpoint.getOwnerUserId(), ownerId)) throw unavailable();
        return checkpoint;
    }

    private TaskEntity requireOwnerTask(UUID taskId, long ownerId) {
        if (taskId == null) throw unavailable();
        TaskEntity task = tasks.findById(taskId)
                .orElseThrow(VerifiedOwnerDeliveryCheckpoint::unavailable);
        if (!OwnerBoundResourcePolicy.permitted(task.getOwnerUserId(),
                task.getOwnerProjectId(), ownerId, null)) throw unavailable();
        return task;
    }

    private static SafeUpdate redact(TaskJournalSequencedRow row) {
        String agent = row.agentId();
        if (agent != null && (agent.length() > 80 ||
                !agent.matches("[a-z0-9][a-z0-9-]{0,79}"))) agent = null;
        return new SafeUpdate(row.eventId(), row.taskId(), row.sequence(),
                row.eventAt(), row.state(), agent,
                "PERSISTED_JOURNAL_NOT_VERIFIED_OUTCOME");
    }

    private static NoSuchElementException unavailable() {
        return new NoSuchElementException("TASK_NOT_AVAILABLE");
    }

    public enum Acknowledgement { ADVANCED, DUPLICATE, OUT_OF_ORDER }
    public record CheckpointHandle(UUID checkpointId, UUID taskId,
                                   long lastAcknowledged, String status) {}
    public record ReplayBatch(UUID checkpointId, long lastAcknowledged,
                              List<SafeUpdate> events, boolean hasMore,
                              String provenance) {}
    public record SafeUpdate(UUID eventId, UUID taskId, long sequence,
                             Instant eventAt, TaskState state, String agentId,
                             String provenance) {}
}
