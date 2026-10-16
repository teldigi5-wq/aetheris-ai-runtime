package io.aetheris.orchestrator.trading;

import java.util.List;

/**
 * Auditable, strictly PAPER-ONLY risk decision. Estimated stop loss assumes
 * the hypothetical stop fills exactly, and is not a loss guarantee.
 */
public record PaperRiskDecision(
        boolean allowed,
        List<String> reasons,
        double projectedGrossExposure,
        double projectedCorrelatedExposure,
        double approvedNotional,
        double estimatedLossAtStop,
        double remainingDailyLossBudget) {
    public PaperRiskDecision {
        reasons = List.copyOf(reasons);
    }
}
