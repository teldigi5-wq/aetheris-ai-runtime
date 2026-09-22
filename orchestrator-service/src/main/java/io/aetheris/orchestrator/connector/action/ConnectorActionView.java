package io.aetheris.orchestrator.connector.action;

import io.aetheris.orchestrator.connector.ConnectorProvider;

import java.time.Instant;
import java.util.UUID;

public record ConnectorActionView(
        UUID id,
        UUID connectionId,
        ConnectorProvider provider,
        ConnectorActionKind actionKind,
        ConnectorActionExecutionMode executionMode,
        ConnectorActionStatus status,
        String idempotencyKey,
        String targetRef,
        String summary,
        UUID taskId,
        UUID approvalId,
        String actionType,
        String externalReference,
        Instant createdAt,
        Instant executedAt,
        boolean replay
) {
}
