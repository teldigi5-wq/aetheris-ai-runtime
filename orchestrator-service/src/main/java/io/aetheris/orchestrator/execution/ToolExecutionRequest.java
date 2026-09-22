package io.aetheris.orchestrator.execution;

import io.aetheris.orchestrator.policy.OperationMode;

import java.util.Map;
import java.util.UUID;

public record ToolExecutionRequest(
        UUID taskId,
        String agentId,
        String toolId,
        OperationMode mode,
        Map<String, Object> parameters
) {
    public ToolExecutionRequest {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
