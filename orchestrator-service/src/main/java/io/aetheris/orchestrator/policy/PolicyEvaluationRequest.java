package io.aetheris.orchestrator.policy;

import io.aetheris.orchestrator.agent.RiskLevel;

import java.util.Map;

public record PolicyEvaluationRequest(
        String agentId,
        String action,
        String scope,
        RiskLevel riskLevel,
        boolean billable,
        boolean sendsDataOffDevice,
        OperationMode mode,
        Map<String, String> metadata
) {
    public PolicyEvaluationRequest {
        scope = scope == null || scope.isBlank() ? "global" : scope.trim();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public ActionRequest baseRequest() {
        return new ActionRequest(agentId, action, riskLevel, billable, sendsDataOffDevice, mode);
    }
}
