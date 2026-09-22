package io.aetheris.orchestrator.connector.adapter;

import io.aetheris.orchestrator.executive.ExecutiveSignalType;

import java.time.Instant;

public record GenericSignedWebhookPayload(
        ExecutiveSignalType type,
        String subject,
        String detail,
        boolean trustedLowRisk,
        int signups,
        String verificationStatus,
        int verificationRuns,
        Instant occurredAt
) {}
