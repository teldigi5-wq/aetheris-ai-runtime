package io.aetheris.orchestrator.runtime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EnqueueWorkItemRequest(
        @NotNull UUID taskId,
        @NotBlank String workflowType,
        String payloadJson,
        int maxAttempts
) {
}
