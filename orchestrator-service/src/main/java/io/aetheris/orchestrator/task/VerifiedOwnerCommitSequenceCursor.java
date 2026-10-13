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
 * Owner-verified, sequenced-only journal catch-up. NOT a bean or HTTP API.
 *
 * Sequence comes from a parent TaskEntity row locked until commit by the
 * atomic outbox writer. No per-task sequence is allocated on legacy append().
 *
 * Monotonicity applies to journal+outbox writes through that locked writer
 * only. This reader cannot claim lossless replay for legacy journal writes,
 * record deletion, direct DB writes, or a process that bypasses the writer.
 */
public final class VerifiedOwnerCommitSequenceCursor {
    public static final int MAX_PAGE = 100;
    private final TaskRepository tasks;
    private final TaskEventJournalRepository events;
    private final RuntimeAccessTokenVerifier verifier;

    public VerifiedOwnerCommitSequenceCursor(TaskRepository tasks,
            TaskEventJournalRepository events, RuntimeAccessTokenVerifier verifier) {
        this.tasks = Objects.requireNonNull(tasks);
        this.events = Objects.requireNonNull(events);
        this.verifier = Objects.requireNonNull(verifier);
    }

    public SequencePage page(String signedAccessToken, UUID taskId,
                             UUID afterEventId, int limit) {
        var owner = verifier.verify(signedAccessToken, RuntimeAccessTokenVerifier.READ_SCOPE);
        if (taskId == null || limit < 1 || limit > MAX_PAGE) throw unavailable();
        TaskEntity task = tasks.findById(taskId)
                .orElseThrow(VerifiedOwnerCommitSequenceCursor::unavailable);
        if (!OwnerBoundResourcePolicy.permitted(task.getOwnerUserId(),
                task.getOwnerProjectId(), owner.identityAccountId(), null)) throw unavailable();

        long after = 0L;
        if (afterEventId != null) {
            var cursor = events.sequencedCursorForTask(afterEventId, taskId)
                    .orElseThrow(VerifiedOwnerCommitSequenceCursor::unavailable);
            if (cursor.sequence() == null || cursor.sequence() < 1) throw unavailable();
            after = cursor.sequence();
        }
        List<TaskJournalSequencedRow> raw = events.sequencedPageAfter(taskId,
                after, PageRequest.of(0, limit + 1));
        boolean hasMore = raw.size() > limit;
        var selected = raw.subList(0, Math.min(limit, raw.size()));
        var safe = selected.stream().map(VerifiedOwnerCommitSequenceCursor::redact).toList();
        UUID next = selected.isEmpty() ? afterEventId : selected.get(selected.size() - 1).eventId();
        return new SequencePage(List.copyOf(safe), next, hasMore,
                "OUTBOX_SEQUENCED_ONLY_LEGACY_NOT_INCLUDED");
    }

    private static SafeEvent redact(TaskJournalSequencedRow row) {
        String agent = row.agentId();
        if (agent != null && (agent.length() > 80 ||
                !agent.matches("[a-z0-9][a-z0-9-]{0,79}"))) agent = null;
        return new SafeEvent(row.eventId(), row.taskId(), row.sequence(),
                row.eventAt(), row.state(), agent,
                "PERSISTED_JOURNAL_NOT_VERIFIED_OUTCOME");
    }

    private static NoSuchElementException unavailable() {
        return new NoSuchElementException("TASK_NOT_AVAILABLE");
    }

    public record SequencePage(List<SafeEvent> events, UUID nextEventId,
                               boolean hasMore, String coverage) {}
    public record SafeEvent(UUID eventId, UUID taskId, long sequence,
                            Instant timestamp, TaskState state, String agentId,
                            String provenance) {}
}
