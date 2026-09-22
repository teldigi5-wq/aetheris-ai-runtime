package io.aetheris.orchestrator.tool;

import io.aetheris.orchestrator.policy.CompiledPolicyDecision;

public record ToolAccessDecision(
        ToolDescriptor tool,
        CompiledPolicyDecision policy
) {
}
