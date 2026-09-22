package io.aetheris.orchestrator.model;

import java.util.UUID;

public record ModelExecutionResponse(
        ModelExecutionStatus status,
        String provider,
        String model,
        String text,
        String detail,
        UUID auditId
) {
}
