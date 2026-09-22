package io.aetheris.orchestrator.execution;

import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.task.TaskEntity;
import io.aetheris.orchestrator.task.TaskService;
import io.aetheris.orchestrator.task.TaskState;
import io.aetheris.orchestrator.tool.SpecialistToolAuthorizationService;
import io.aetheris.orchestrator.tool.ToolDescriptor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class ToolExecutionAuthorityService {

    private static final Set<TaskState> READ_ONLY_STATES = Set.of(TaskState.RUNNING, TaskState.VERIFYING);
    private static final Set<TaskState> MUTATING_STATES = Set.of(TaskState.RUNNING);

    private final TaskService tasks;
    private final SpecialistToolAuthorizationService specialistTools;

    public ToolExecutionAuthorityService(
            TaskService tasks,
            SpecialistToolAuthorizationService specialistTools) {
        this.tasks = tasks;
        this.specialistTools = specialistTools;
    }

    public TaskEntity requireExecution(ToolExecutionRequest request, ToolDescriptor tool) {
        if (request == null) throw new IllegalArgumentException("Tool execution request is required");
        if (tool == null) throw new IllegalArgumentException("Tool descriptor is required");
        UUID taskId = request.taskId();
        if (taskId == null) throw new IllegalStateException("Tool execution requires a durable task context");

        String agentId = normalize(request.agentId(), "agentId");
        TaskEntity task = tasks.getRequired(taskId);
        if (!Objects.equals(normalizeNullable(task.getActiveAgentId()), agentId)) {
            throw new IllegalStateException("Tool execution agent " + agentId
                    + " is not the task's active specialist " + normalizeNullable(task.getActiveAgentId()));
        }

        SpecialistToolAuthorizationService.Decision specialistDecision = specialistTools.evaluate(agentId, tool);
        if (!specialistDecision.allowed()) {
            throw new IllegalStateException(specialistDecision.reason());
        }

        OperationMode claimedMode = request.mode() == null ? task.getMode() : request.mode();
        if (task.getMode() != claimedMode) {
            throw new IllegalStateException("Tool execution mode mismatch: task=" + task.getMode()
                    + ", request=" + claimedMode);
        }

        Set<TaskState> allowedStates = tool.readOnly() ? READ_ONLY_STATES : MUTATING_STATES;
        if (!allowedStates.contains(task.getState())) {
            throw new IllegalStateException("Tool " + tool.id() + " cannot execute while task state is "
                    + task.getState() + "; allowed states=" + allowedStates);
        }
        return task;
    }

    private String normalize(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? "none" : value.trim();
    }
}
