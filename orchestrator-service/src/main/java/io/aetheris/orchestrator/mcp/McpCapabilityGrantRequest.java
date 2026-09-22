package io.aetheris.orchestrator.mcp;

public record McpCapabilityGrantRequest(
        String agentId,
        String capability,
        String dataClass
) {
}
