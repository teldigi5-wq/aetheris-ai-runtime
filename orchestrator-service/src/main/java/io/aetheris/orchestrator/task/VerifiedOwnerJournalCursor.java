package io.aetheris.orchestrator.task;

import io.aetheris.orchestrator.security.OwnerBoundResourcePolicy;
import io.aetheris.orchestrator.security.RuntimeAccessTokenVerifier;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Internal, bounded owner-only journal cursor reader, NOT an HTTP endpoint,
 * SSE listener, scheduler, native IPC command or authorization bypass.
 *
 * JWT verification + stored task ownership precede all journal queries.
 * A supplied cursor UUID is looked up in the *selected owned task* before
 * any keyset query. No caller-provided timestamp or arbitrary owner ID.
 *
 * Ordering is (eventAt, journalUUID), not database insertion/commit order.
 * Backdated events inserted after an already issued cursor may be skipped.
 * This is an honest bounded replay primitive, NOT a lossless outbox cursor.
 */
public final class VerifiedOwnerJournalCursor {
    public static final int MAX_PAGE_SIZE = 100;
    private final TaskRepository tasks;
    private final TaskEventJournalRepository journal;
    private final RuntimeAccessTokenVerifier tokens;

    public VerifiedOwnerJournalCursor(TaskRepository tasks,
                                      TaskEventJournalRepository journal,
                                      RuntimeAccessTokenVerifier tokens) {
        this.tasks = Objects.requireNonNull(tasks);
        this.journal = Objects.requireNonNull(journal);
        this.tokens = Objects.requireNonNull(tokens);
    }

    public CursorPage page(String signedAccessToken, UUID selectedTaskId,
                           UUID afterEventId, int requestedLimit) {
        // Validate caller before even checking selected task or cursor.
        var owner = tokens.verify(signedAccessToken, RuntimeAccessTokenVerifier.READ_SCOPE);
        if (selectedTaskId == null || requestedLimit < 1 || requestedLimit > MAX_PAGE_SIZE) {
            throw unavailable();
        }
        TaskEntity task = tasks.findById(selectedTaskId)
                .orElseThrow(VerifiedOwnerJournalCursor::unavailable);
        if (!OwnerBoundResourcePolicy.permitted(task.getOwnerUserId(),
                task.getOwnerProjectId(), owner.identityAccountId(), null)) {
            throw unavailable();
        }

        // Never trust a timestamp from the client. Cursor must be an actual
        // event UUID in the already authorized task.
        final List<TaskJournalCursorRow> rows;
        if (afterEventId == null) {
            rows = journal.firstCursorPage(selectedTaskId,
                    PageRequest.of(0, requestedLimit + 1));
        } else {
            var cursor = journal.cursorForTask(afterEventId, selectedTaskId)
                    .orElseThrow(VerifiedOwnerJournalCursor::unavailable);
            rows = journal.cursorPageAfter(selectedTaskId, cursor.eventAt(),
                    cursor.eventId(), PageRequest.of(0, requestedLimit + 1));
        }

        boolean more = rows.size() > requestedLimit;
        List<TaskJournalCursorRow> slice = rows.subList(0, Math.min(rows.size(), requestedLimit));
        List<RecordedUpdate> updates = slice.stream()
                .map(VerifiedOwnerJournalCursor::sanitize)
                .toList();
        UUID nextEventId = slice.isEmpty()
                ? afterEventId : slice.get(slice.size() - 1).eventId();

        return new CursorPage(List.copyOf(updates), nextEventId, more);
    }

    private static RecordedUpdate sanitize(TaskJournalCursorRow row) {
        String agent = row.agentId();
        if (agent != null && (agent.length() > 80 ||
                !agent.matches("[a-z0-9][a-z0-9-]{0,79}"))) agent = null;
        return new RecordedUpdate(row.eventId(), row.taskId(),
                row.eventAt(), row.state(), agent,
                "PERSISTED_JOURNAL_NOT_VERIFIED_OUTCOME");
    }

    private static NoSuchElementException unavailable() {
        return new NoSuchElementException("TASK_NOT_AVAILABLE");
    }

    /**
     * Database journal UUID is a replay position, not an access token.
     * The owner JWT and task owner must be rechecked on every page.
     */
    public record CursorPage(List<RecordedUpdate> events,
                             UUID nextEventId, boolean hasMore) {}

    /** No event message, private commands, metadata, JWT or personal IDs. */
    public record RecordedUpdate(UUID eventId, UUID taskId, Instant eventAt,
                                 TaskState state, String agentId,
                                 String provenance) {}
}
