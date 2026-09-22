package io.aetheris.orchestrator.task;

import jakarta.validation.constraints.NotNull;

public record TaskTransitionRequest(
        @NotNull TaskState state,
        String agentId,
        String message
) {
}
