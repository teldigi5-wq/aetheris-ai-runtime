package io.aetheris.orchestrator.policy;

import io.aetheris.orchestrator.agent.RiskLevel;

public record ActionRequest(
        String agentId,
        String action,
        RiskLevel riskLevel,
        boolean billable,
        boolean sendsDataOffDevice,
        OperationMode mode
) {
}
