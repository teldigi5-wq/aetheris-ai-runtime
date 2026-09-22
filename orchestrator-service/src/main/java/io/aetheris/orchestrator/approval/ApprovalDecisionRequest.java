package io.aetheris.orchestrator.approval;

public record ApprovalDecisionRequest(
        boolean approved,
        String note
) {
}
