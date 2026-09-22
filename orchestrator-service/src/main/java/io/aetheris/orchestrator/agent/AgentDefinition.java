package io.aetheris.orchestrator.agent;

import java.util.List;

public record AgentDefinition(
        String id,
        String displayName,
        String division,
        String mission,
        List<String> capabilities,
        List<String> allowedTools,
        RiskLevel riskLevel,
        ModelClass preferredModelClass,
        boolean approvalRequiredForHighImpactActions
) {
    public AgentDefinition {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
    }
}
