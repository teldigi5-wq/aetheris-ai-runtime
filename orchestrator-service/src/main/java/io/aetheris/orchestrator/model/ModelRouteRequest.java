package io.aetheris.orchestrator.model;

import io.aetheris.orchestrator.agent.ModelClass;
import io.aetheris.orchestrator.policy.OperationMode;

public record ModelRouteRequest(
        ModelClass modelClass,
        OperationMode mode,
        boolean protectedData,
        boolean allowPaid
) {
}
