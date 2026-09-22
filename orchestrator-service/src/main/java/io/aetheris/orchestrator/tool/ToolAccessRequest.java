package io.aetheris.orchestrator.tool;

import io.aetheris.orchestrator.policy.OperationMode;

import java.util.Map;

public record ToolAccessRequest(
        String agentId,
        String toolId,
        OperationMode mode,
        boolean billable,
        boolean sendsDataOffDevice,
        Map<String, String> metadata
) {
    public ToolAccessRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
