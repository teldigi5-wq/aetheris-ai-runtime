package io.aetheris.orchestrator.task;

import io.aetheris.orchestrator.agent.AgentCatalogService;
import io.aetheris.orchestrator.agent.AgentDefinition;
import io.aetheris.orchestrator.policy.OperationMode;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class DirectExecutionAuthorityService {
    private final TaskService tasks;
    private final AgentCatalogService agents;

    public DirectExecutionAuthorityService(TaskService tasks, AgentCatalogService agents) {
        this.tasks = tasks;
        this.agents = agents;
    }

    public TaskEntity requireRunningSpecialist(UUID taskId, String agentId, String requiredTool) {
        if (taskId == null) throw new IllegalStateException("Direct execution requires a task context");
        String normalizedAgent = normalize(agentId, "agentId");
        String normalizedTool = normalize(requiredTool, "requiredTool").toLowerCase(Locale.ROOT);

        TaskEntity task = tasks.getRequired(taskId);
        AgentDefinition agent = agents.getRequired(normalizedAgent);
        if (task.getState() != TaskState.RUNNING) {
            throw new IllegalStateException("Direct execution requires a RUNNING task; current state is " + task.getState());
        }
        if (!Objects.equals(normalizeNullable(task.getActiveAgentId()), normalizedAgent)) {
            throw new IllegalStateException("Direct execution agent " + normalizedAgent
                    + " is not the task's active specialist " + normalizeNullable(task.getActiveAgentId()));
        }
        boolean toolAllowed = agent.allowedTools().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(normalizedTool::equals);
        if (!toolAllowed) {
            throw new IllegalStateException("Active specialist " + normalizedAgent
                    + " is not authorized for direct tool family " + normalizedTool);
        }
        return task;
    }

    public TaskEntity requireRunningSpecialist(UUID taskId, String agentId, String requiredTool, OperationMode claimedMode) {
        TaskEntity task = requireRunningSpecialist(taskId, agentId, requiredTool);
        if (claimedMode == null) throw new IllegalStateException("Direct execution requires the task operation mode");
        if (task.getMode() != claimedMode) {
            throw new IllegalStateException("Direct execution mode mismatch: task=" + task.getMode() + ", request=" + claimedMode);
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
