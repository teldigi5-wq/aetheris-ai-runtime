package io.aetheris.orchestrator.policy;

public record PolicyDecision(
        boolean allowed,
        boolean requiresApproval,
        String reason
) {
    public static PolicyDecision allow(String reason) {
        return new PolicyDecision(true, false, reason);
    }

    public static PolicyDecision requireApproval(String reason) {
        return new PolicyDecision(true, true, reason);
    }

    public static PolicyDecision deny(String reason) {
        return new PolicyDecision(false, false, reason);
    }
}
