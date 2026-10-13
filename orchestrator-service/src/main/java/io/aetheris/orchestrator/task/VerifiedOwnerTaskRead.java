package io.aetheris.orchestrator.task;

import io.aetheris.orchestrator.security.OwnerBoundResourcePolicy;
import io.aetheris.orchestrator.security.RuntimeAccessTokenVerifier;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Signed-owner-scoped task **read** foundation, independent of HTTP endpoints.
 *
 * Until authenticated project-membership grants exist, only owner-global
 * resources (ownerProjectId == null) can be read. Merely knowing a task UUID,
 * providing an owner header or having orchestrator:read scope is insufficient.
 *
 * This class is deliberately not a Spring bean / controller: the HTTP task,
 * mission and SSE route quarantine MUST remain active until all indirect
 * authorization and subscriber boundaries are independently verified.
 */
public final class VerifiedOwnerTaskRead {
    private final TaskRepository tasks;
    private final RuntimeAccessTokenVerifier tokens;

    public VerifiedOwnerTaskRead(TaskRepository tasks, RuntimeAccessTokenVerifier tokens) {
        this.tasks = Objects.requireNonNull(tasks);
        this.tokens = Objects.requireNonNull(tokens);
    }

    public TaskStatusView get(String signedAccessToken, UUID taskId) {
        var owner = tokens.verify(signedAccessToken, RuntimeAccessTokenVerifier.READ_SCOPE);
        if (taskId == null) throw unavailable();
        TaskEntity task = tasks.findById(taskId).orElseThrow(VerifiedOwnerTaskRead::unavailable);
        if (!OwnerBoundResourcePolicy.permitted(
                task.getOwnerUserId(), task.getOwnerProjectId(),
                owner.identityAccountId(), null)) {
            throw unavailable();
        }
        return view(task);
    }

    /**
     * Strictly bounded owner-global list. No arbitrary project IDs, tasks
     * with missing owner, another user's tasks or private commandText.
     */
    public List<TaskStatusView> recent(String signedAccessToken) {
        var owner = tokens.verify(signedAccessToken, RuntimeAccessTokenVerifier.READ_SCOPE);
        return tasks.findTop50ByOwnerUserIdAndOwnerProjectIdIsNullOrderByUpdatedAtDesc(
                        owner.identityAccountId())
                .stream()
                .filter(task -> OwnerBoundResourcePolicy.permitted(
                        task.getOwnerUserId(), task.getOwnerProjectId(),
                        owner.identityAccountId(), null))
                .limit(50)
                .map(VerifiedOwnerTaskRead::view)
                .toList();
    }

    private static NoSuchElementException unavailable() {
        return new NoSuchElementException("TASK_NOT_AVAILABLE");
    }

    private static TaskStatusView view(TaskEntity task) {
        return new TaskStatusView(task.getId(), task.getState(),
                task.getActiveAgentId(), task.getUpdatedAt());
    }

    /** Minimal read-only UI candidate, excludes raw commands, owner email,
     * task metadata and external untrusted payloads. */
    public record TaskStatusView(UUID taskId, TaskState state,
                                 String agentId, Instant updatedAt) {}
}
