package io.aetheris.orchestrator.task;

import io.aetheris.orchestrator.security.OwnerBoundResourcePolicy;
import io.aetheris.orchestrator.security.RuntimeAccessTokenVerifier;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Verified per-owner TASK EVENT HISTORY and trusted-source projection.
 *
 * Deliberately not a Spring bean/controller/SSE listener. The existing
 * ownerless HTTP task/mission/global stream quarantine remains unchanged.
 *
 * A JWT read scope proves a caller ID but NOT a task's ownership. Check the
 * owner stored on the TaskEntity before touching the journal at all.
 * Project-bound and legacy ownerless resources remain blocked.
 */
public final class VerifiedOwnerTaskTimeline {
    public static final int MAX_HISTORY_EVENTS = 100;
    private static final int MAX_AGENT_ID_CHARS = 80;

    private final TaskRepository tasks;
    private final TaskEventJournalService journal;
    private final RuntimeAccessTokenVerifier verifier;

    public VerifiedOwnerTaskTimeline(TaskRepository tasks,
                                      TaskEventJournalService journal,
                                      RuntimeAccessTokenVerifier verifier) {
        this.tasks = Objects.requireNonNull(tasks);
        this.journal = Objects.requireNonNull(journal);
        this.verifier = Objects.requireNonNull(verifier);
    }

    /** No raw TaskEvent message, metadata, command, account ID or JWT leaves. */
    public List<TimelineUpdate> recentHistory(String signedAccessToken, UUID taskId) {
        verifyOwnedTask(signedAccessToken, taskId);
        List<TaskEvent> source = journal.history(taskId);
        // History is chronological and already bounded at 200 by the journal.
        // The owner-facing projection is independently capped at 100.
        int start = Math.max(0, source.size() - MAX_HISTORY_EVENTS);
        return source.subList(start, source.size()).stream()
                .filter(event -> event != null && taskId.equals(event.taskId()))
                .map(event -> sanitize(event, Provenance.PERSISTED_JOURNAL_NOT_VERIFIED_OUTCOME))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * A potential future internal event handoff (not network/SSE exposure).
     * Re-verifies signature and current DB task ownership on every call,
     * rather than trusting the event's supplied task ID or owner text.
     */
    public TimelineUpdate inspectSignedOwnerEvent(String signedAccessToken,
                                                   UUID selectedTaskId, TaskEvent event) {
        verifyOwnedTask(signedAccessToken, selectedTaskId);
        if (event == null || !selectedTaskId.equals(event.taskId())) {
            throw unavailable();
        }
        TimelineUpdate view = sanitize(event, Provenance.UNTRUSTED_EVENT_ARGUMENT);
        if (view == null) throw unavailable();
        return view;
    }

    private TaskEntity verifyOwnedTask(String signedAccessToken, UUID taskId) {
        var principal = verifier.verify(signedAccessToken, RuntimeAccessTokenVerifier.READ_SCOPE);
        if (taskId == null) throw unavailable();
        TaskEntity task = tasks.findById(taskId).orElseThrow(VerifiedOwnerTaskTimeline::unavailable);
        if (!OwnerBoundResourcePolicy.permitted(task.getOwnerUserId(),
                task.getOwnerProjectId(), principal.identityAccountId(), null)) {
            throw unavailable();
        }
        return task;
    }

    private static NoSuchElementException unavailable() {
        // Foreign, ownerless, project-scoped, absent and foreign-event routes
        // share a single non-enumerating diagnostic.
        return new NoSuchElementException("TASK_NOT_AVAILABLE");
    }

    private static TimelineUpdate sanitize(TaskEvent event, Provenance provenance) {
        if (event.taskId() == null || event.timestamp() == null || event.state() == null)
            return null;
        String agent = event.agentId();
        if (agent != null && (agent.length() > MAX_AGENT_ID_CHARS ||
                !agent.matches("[a-z0-9][a-z0-9-]{0,79}"))) {
            agent = null;
        }
        return new TimelineUpdate(event.taskId(), event.timestamp(), event.state(), agent, provenance);
    }

    public enum Provenance {
        // A saved event is evidence of a recorded state, not proof the tool
        // action really occurred or that its reported completion was correct.
        PERSISTED_JOURNAL_NOT_VERIFIED_OUTCOME,
        // No untrusted input can be labeled as a successful live agent action.
        UNTRUSTED_EVENT_ARGUMENT
    }

    /** Safe DTO: message, metadata and tokens are NEVER fields here. */
    public record TimelineUpdate(UUID taskId, Instant timestamp, TaskState state,
                                 String agentId, Provenance provenance) {}
}
