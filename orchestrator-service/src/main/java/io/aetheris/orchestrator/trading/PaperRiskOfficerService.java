package io.aetheris.orchestrator.trading;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Owner-governed paper risk officer, NOT an exchange execution authority.
 * The deterministic capital budget does the actual paper notional sizing;
 * no LLM score can override its stop/exposure/daily-loss caps.
 */
@Service
public class PaperRiskOfficerService {
    private final PaperRiskPolicyRepository paperPolicies;
    private final PaperPositionRepository positions;
    private final TradingIntelligenceService intelligence;
    private final PaperDrawdownGuardService drawdownGuard;

    public PaperRiskOfficerService(PaperRiskPolicyRepository paperPolicies,
                                   PaperPositionRepository positions,
                                   TradingIntelligenceService intelligence,
                                   PaperDrawdownGuardService drawdownGuard) {
        this.paperPolicies = paperPolicies;
        this.positions = positions;
        this.intelligence = intelligence;
        this.drawdownGuard = drawdownGuard;
    }

    public PaperRiskPolicyEntity policy() {
        return paperPolicies.findById("default")
                .orElseGet(() -> paperPolicies.save(new PaperRiskPolicyEntity("default")));
    }

    public PaperRiskPolicyEntity update(PaperRiskPolicyRequest request) {
        PaperRiskPolicyEntity current = policy();
        current.update(request);
        return paperPolicies.save(current);
    }

    public PaperRiskDecision evaluate(PaperAccountEntity account, TradeSignalEntity signal,
                                      double simulatedFillPrice) {
        if (account == null) {
            return new PaperRiskDecision(false, List.of("Paper account unavailable"), 0, 0, 0, 0, 0);
        }
        // The trading account's daily UTC boundary must be evaluated before
        // taking the snapshot; Stage45 still forbids real money operation.
        account.ensureDay();
        PaperDrawdownGuardService.OpeningDecision circuit = drawdownGuard.forNewPaperExposure(account);
        if (!circuit.allowed()) {
            return new PaperRiskDecision(false,
                    List.of("Paper drawdown circuit blocks new exposure: " + circuit.reason()),
                    0, 0, 0, 0, 0);
        }
        PaperCapitalBudgetService.Quote budget = PaperCapitalBudgetService.assess(
                account, signal, simulatedFillPrice, intelligence.policy(), policy(),
                positions.findByStatus(PaperPositionStatus.OPEN));
        return new PaperRiskDecision(
                budget.allowed(), budget.denialReasons(),
                budget.projectedGrossExposure(), budget.projectedCorrelatedExposure(),
                budget.approvedNotional(), budget.estimatedLossAtStop(),
                budget.remainingDailyLossBudget());
    }
}
