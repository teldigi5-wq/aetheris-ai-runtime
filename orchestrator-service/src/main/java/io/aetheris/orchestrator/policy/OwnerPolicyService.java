package io.aetheris.orchestrator.policy;

import io.aetheris.orchestrator.agent.RiskLevel;
import org.springframework.stereotype.Service;

@Service
public class OwnerPolicyService {

    public PolicyDecision evaluate(ActionRequest request) {
        OperationMode mode = request.mode() == null ? OperationMode.BALANCED : request.mode();
        RiskLevel riskLevel = request.riskLevel() == null ? RiskLevel.MEDIUM : request.riskLevel();

        if (mode == OperationMode.ZERO_COST && request.billable()) {
            return PolicyDecision.deny("ZERO_COST mode blocks billable providers and paid actions.");
        }

        if (mode == OperationMode.PRIVATE && request.sendsDataOffDevice()) {
            return PolicyDecision.deny("PRIVATE mode blocks actions that send protected context off-device.");
        }

        if (riskLevel == RiskLevel.CRITICAL) {
            return PolicyDecision.requireApproval("Critical actions always require explicit owner approval.");
        }

        if (riskLevel == RiskLevel.HIGH) {
            return PolicyDecision.requireApproval("High-impact actions require owner approval by default.");
        }

        return PolicyDecision.allow("Action is within the current deterministic policy envelope.");
    }
}
