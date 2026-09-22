package io.aetheris.orchestrator.policy;

import java.util.List;

public record CompiledPolicyDecision(
        boolean allowed,
        boolean requiresApproval,
        boolean forceLocal,
        boolean forceZeroCost,
        List<String> reasons,
        List<String> matchedRuleKeys
) {
    public CompiledPolicyDecision {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        matchedRuleKeys = matchedRuleKeys == null ? List.of() : List.copyOf(matchedRuleKeys);
    }
}
