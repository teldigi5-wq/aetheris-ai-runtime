package io.aetheris.orchestrator.mcp;

public record McpHealthUpdateRequest(boolean healthy, String detail) {
}
