package io.aetheris.orchestrator.workflow;

import java.util.List;

public record WorkflowExecutionResult(
        EngineeringWorkflowEntity workflow,
        List<VerificationEvidenceEntity> evidence,
        String detail
) {
    public WorkflowExecutionResult {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
