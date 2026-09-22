package io.aetheris.orchestrator.stage33;

import java.util.List;
import java.util.Set;

public final class GovernanceVerificationService {
    private final GovernanceLifecycle lifecycle = new GovernanceLifecycle();

    public GovernanceExecutionRecord finalizeExecution(
            GovernanceDecision preflight,
            ExecutionObservation observation,
            VerificationReceipt receipt) {

        if (preflight == null || preflight.disposition() != GovernanceDisposition.ALLOW || !preflight.executionEligible()) {
            return new GovernanceExecutionRecord(
                    ExecutionTruthStatus.BLOCKED,
                    preflight == null ? List.of() : preflight.completedPhases(),
                    Set.of(),
                    "Execution was not eligible under Stage 33 governance");
        }
        if (observation == null || !observation.attempted()) {
            return new GovernanceExecutionRecord(
                    ExecutionTruthStatus.UNVERIFIED,
                    preflight.completedPhases(),
                    Set.of(),
                    "No execution observation exists; EXECUTE is not marked complete and success cannot be claimed");
        }
        if (receipt == null || !receipt.verified() || receipt.verifier().isBlank() || receipt.evidenceRefs().isEmpty()) {
            return new GovernanceExecutionRecord(
                    ExecutionTruthStatus.UNVERIFIED,
                    lifecycle.through(GovernancePhase.VERIFY),
                    receipt == null ? Set.of() : receipt.evidenceRefs(),
                    "Execution was observed but remains explicitly unverified; success cannot be claimed");
        }

        boolean success = observation.completed() && receipt.success();
        return new GovernanceExecutionRecord(
                success ? ExecutionTruthStatus.VERIFIED_SUCCESS : ExecutionTruthStatus.VERIFIED_FAILURE,
                lifecycle.through(GovernancePhase.REPORT),
                receipt.evidenceRefs(),
                success ? "Execution succeeded and was independently evidenced" : "Verification evidence records a failed or incomplete execution");
    }
}
