package io.aetheris.orchestrator.connector.action;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateConnectorActionRequest(
        @NotNull UUID connectionId,
        @NotNull ConnectorActionKind actionKind,
        @NotBlank String idempotencyKey,
        @NotBlank String targetRef,
        @NotBlank String summary,
        ConnectorActionExecutionMode executionMode
) {
}
