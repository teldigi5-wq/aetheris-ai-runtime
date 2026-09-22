package io.aetheris.orchestrator.mcp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record McpToolInvocationRequest(
        @NotNull UUID serverId,
        UUID taskId,
        @NotBlank String agentId,
        @NotBlank String capability,
        @NotBlank String dataClass,
        @NotBlank String toolName,
        Map<String, Object> arguments
) {
    public McpToolInvocationRequest {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
