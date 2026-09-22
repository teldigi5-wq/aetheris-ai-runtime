package io.aetheris.orchestrator.approval;

import io.aetheris.orchestrator.agent.RiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateApprovalRequest(
        @NotNull UUID taskId,
        @NotBlank String actionType,
        @NotBlank String summary,
        @NotNull RiskLevel riskLevel
) {
}
