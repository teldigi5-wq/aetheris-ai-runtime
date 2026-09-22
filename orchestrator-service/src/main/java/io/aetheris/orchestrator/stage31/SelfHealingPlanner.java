package io.aetheris.orchestrator.stage31;

public class SelfHealingPlanner {

    /**
     * Plans recovery eligibility only. Stage 31 deliberately has no host-side executor here.
     */
    public RecoveryPlan plan(RecoveryAction action) {
        if (action.financial()
                || action.destructive()
                || action.kind() == RecoveryKind.FINANCIAL_ACTION
                || action.kind() == RecoveryKind.DELETE_USER_DATA
                || action.kind() == RecoveryKind.SECURITY_CONTROL_CHANGE) {
            return new RecoveryPlan(action.actionId(), RecoveryDisposition.BLOCKED,
                    "Stage 31 blocks financial, destructive, user-data, and security-control autonomy.", false);
        }
        if (!action.reversible()
                || !action.preApproved()
                || !action.withinApprovedBounds()
                || action.privileged()) {
            return new RecoveryPlan(action.actionId(), RecoveryDisposition.OWNER_APPROVAL_REQUIRED,
                    "Recovery is outside the reversible pre-approved non-privileged boundary.", false);
        }
        return new RecoveryPlan(action.actionId(), RecoveryDisposition.AUTO_ELIGIBLE,
                "Eligible for a separately governed bounded executor; this planner does not execute it.", false);
    }
}
