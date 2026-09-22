package io.aetheris.orchestrator.mcp;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record McpDiscoveryResult(
        UUID serverId,
        boolean healthy,
        String protocolVersion,
        Set<String> capabilities,
        List<String> tools,
        String detail,
        UUID auditId
) {
    public McpDiscoveryResult {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
