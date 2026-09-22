package io.aetheris.orchestrator.executive;

public record OvernightSignal(
        String source,
        ExecutiveSignalType type,
        String subject,
        String detail,
        boolean trustedLowRisk,
        int signups,
        String verificationStatus,
        int verificationRuns
) {}
