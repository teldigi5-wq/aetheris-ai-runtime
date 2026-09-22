package io.aetheris.orchestrator.model;

import io.aetheris.orchestrator.agent.ModelClass;
import io.aetheris.orchestrator.policy.OperationMode;

import java.util.UUID;

public record ModelExecutionRequest(
        UUID taskId,
        String agentId,
        String prompt,
        String model,
        ModelClass modelClass,
        OperationMode mode,
        boolean protectedData,
        boolean allowPaid
) {
}
