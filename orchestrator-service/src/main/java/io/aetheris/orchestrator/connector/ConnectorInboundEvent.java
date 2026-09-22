package io.aetheris.orchestrator.connector;

import io.aetheris.orchestrator.executive.ExecutiveSignalType;

import java.time.Instant;
import java.util.UUID;

public record ConnectorInboundEvent(
        UUID connectionId,
        String externalEventId,
        ExecutiveSignalType type,
        String subject,
        String detail,
        boolean trustedLowRisk,
        boolean deliveryVerified,
        int signups,
        String verificationStatus,
        int verificationRuns,
        Instant occurredAt
) {}
