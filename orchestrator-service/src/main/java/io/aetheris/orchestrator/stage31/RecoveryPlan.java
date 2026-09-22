package io.aetheris.orchestrator.stage31;

public record RecoveryPlan(
        String actionId,
        RecoveryDisposition disposition,
        String reason,
        boolean executed) {}
