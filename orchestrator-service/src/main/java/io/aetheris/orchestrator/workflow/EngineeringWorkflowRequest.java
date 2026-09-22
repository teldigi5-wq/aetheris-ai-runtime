package io.aetheris.orchestrator.workflow;

import io.aetheris.orchestrator.policy.OperationMode;
import jakarta.validation.constraints.NotBlank;

public record EngineeringWorkflowRequest(
        @NotBlank String title,
        @NotBlank String command,
        OperationMode mode
) {
}
