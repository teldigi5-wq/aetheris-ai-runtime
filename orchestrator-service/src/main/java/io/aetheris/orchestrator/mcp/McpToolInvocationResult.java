package io.aetheris.orchestrator.mcp;

import java.util.Map;
import java.util.UUID;

public record McpToolInvocationResult(
        boolean success,
        UUID serverId,
        String toolName,
        Map<String, Object> result,
        String detail,
        UUID invocationId
) {
}
