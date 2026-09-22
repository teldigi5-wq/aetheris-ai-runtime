package io.aetheris.orchestrator.stage33;

import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.stage32.EmergencyMode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class CrossSystemGovernanceEngine {
    private final GovernanceLifecycle lifecycle;
    private final ScopedApprovalService approvals;

    public CrossSystemGovernanceEngine() {
        this(new GovernanceLifecycle(), new ScopedApprovalService());
    }

    public CrossSystemGovernanceEngine(GovernanceLifecycle lifecycle, ScopedApprovalService approvals) {
        this.lifecycle = lifecycle;
        this.approvals = approvals;
    }

    public GovernanceDecision preflight(
            GovernanceAction action,
            List<OwnerGovernanceRule> rules,
            ApprovalGrant approval,
            boolean simulationPassed,
            EmergencyMode emergencyMode,
            Instant now) {

        List<String> reasons = new ArrayList<>();
        List<OwnerGovernanceRule> matched = (rules == null ? List.<OwnerGovernanceRule>of() : rules).stream()
                .filter(rule -> rule.matches(action))
                .sorted(Comparator.comparingInt(OwnerGovernanceRule::priority).reversed().thenComparing(OwnerGovernanceRule::ruleId))
                .toList();
        List<String> matchedIds = matched.stream().map(OwnerGovernanceRule::ruleId).toList();

        // Stage 32 emergency control outranks ordinary orchestration.
        EmergencyMode emergency = emergencyMode == null ? EmergencyMode.NORMAL : emergencyMode;
        if (emergency != EmergencyMode.NORMAL) {
            reasons.add("Stage 32 emergency control is active: " + emergency);
            return decision(GovernanceDisposition.BLOCKED, escalateRisk(action), false, false, lifecycle.through(GovernancePhase.CHECK_RULES), matchedIds, reasons);
        }

        // Platform hard controls cannot be weakened by model output or owner ALLOW rules.
        if (action.mode() == OperationMode.ZERO_COST && (action.billable() || action.estimatedCostUsd() > 0.0)) {
            reasons.add("ZERO_COST mode hard-blocks billable endpoints and non-zero external cost");
            return decision(GovernanceDisposition.BLOCKED, escalateRisk(action), false, false, lifecycle.through(GovernancePhase.CHECK_RULES), matchedIds, reasons);
        }
        if (action.liveMoney()) {
            reasons.add("Live-money execution remains outside the Stage 33 trusted execution boundary");
            return decision(GovernanceDisposition.BLOCKED, RiskLevel.CRITICAL, false, false, lifecycle.through(GovernancePhase.CHECK_RULES), matchedIds, reasons);
        }

        Set<GovernanceRuleEffect> effects = matched.stream().map(OwnerGovernanceRule::effect).collect(() -> EnumSet.noneOf(GovernanceRuleEffect.class), EnumSet::add, EnumSet::addAll);
        boolean conflict = matched.stream().anyMatch(a -> matched.stream().anyMatch(b ->
                a.priority() == b.priority() && a.effect() == GovernanceRuleEffect.ALLOW && b.effect() == GovernanceRuleEffect.BLOCK));
        if (conflict) {
            reasons.add("Conflicting owner rules at the same priority fail closed");
            return decision(GovernanceDisposition.BLOCKED, escalateRisk(action), false, false, lifecycle.through(GovernancePhase.CHECK_RULES), matchedIds, reasons);
        }
        if (effects.contains(GovernanceRuleEffect.BLOCK)) {
            reasons.add("A matched structured owner rule blocks this action");
            return decision(GovernanceDisposition.BLOCKED, escalateRisk(action), false, false, lifecycle.through(GovernancePhase.CHECK_RULES), matchedIds, reasons);
        }
        if (effects.contains(GovernanceRuleEffect.FORCE_ZERO_COST) && (action.billable() || action.estimatedCostUsd() > 0.0)) {
            reasons.add("A matched owner rule forces zero-cost execution");
            return decision(GovernanceDisposition.BLOCKED, escalateRisk(action), false, false, lifecycle.through(GovernancePhase.CHECK_RULES), matchedIds, reasons);
        }
        if (effects.contains(GovernanceRuleEffect.FORCE_LOCAL) && action.sendsDataOffDevice()) {
            reasons.add("A matched owner rule forces local-only execution");
            return decision(GovernanceDisposition.BLOCKED, escalateRisk(action), false, false, lifecycle.through(GovernancePhase.CHECK_RULES), matchedIds, reasons);
        }

        RiskLevel risk = escalateRisk(action);
        boolean privateOverrideRequired = action.mode() == OperationMode.PRIVATE
                && action.sendsDataOffDevice()
                && action.dataClassification().protectedData();
        boolean approvalRequired = privateOverrideRequired
                || action.publicEffect() || action.financial() || action.destructive()
                || action.privileged() || action.irreversible()
                || risk == RiskLevel.HIGH || risk == RiskLevel.CRITICAL
                || effects.contains(GovernanceRuleEffect.REQUIRE_APPROVAL);
        boolean simulationRequired = action.financial() || action.destructive() || action.privileged() || action.irreversible()
                || (action.sideEffect() && (risk == RiskLevel.HIGH || risk == RiskLevel.CRITICAL))
                || effects.contains(GovernanceRuleEffect.REQUIRE_SIMULATION);
        boolean verificationRequired = action.sideEffect() || action.publicEffect() || action.financial()
                || action.destructive() || action.privileged() || action.irreversible()
                || effects.contains(GovernanceRuleEffect.REQUIRE_VERIFICATION);

        if (simulationRequired && !simulationPassed) {
            reasons.add("Risk policy requires a successful simulation or preview before approval/execution");
            return decision(GovernanceDisposition.SIMULATION_REQUIRED, risk, false, verificationRequired, lifecycle.through(GovernancePhase.SIMULATE_PREVIEW), matchedIds, reasons);
        }

        boolean approvalConsumed = false;
        if (approvalRequired) {
            ApprovalValidation validation = approvals.validateAndConsume(approval, action, now, privateOverrideRequired);
            if (!validation.valid()) {
                reasons.add(validation.reason());
                return decision(GovernanceDisposition.APPROVAL_REQUIRED, risk, false, verificationRequired, lifecycle.through(GovernancePhase.APPROVE_WHEN_REQUIRED), matchedIds, reasons);
            }
            approvalConsumed = validation.consumed();
            reasons.add(validation.reason());
        }

        if (privateOverrideRequired) reasons.add("PRIVATE-mode off-device use is allowed only for this explicitly approved action/scope");
        reasons.add("Preflight governance gates passed; this is execution eligibility, not proof of execution");
        return new GovernanceDecision(
                GovernanceDisposition.ALLOW,
                risk,
                true,
                verificationRequired,
                approvalConsumed,
                lifecycle.through(GovernancePhase.APPROVE_WHEN_REQUIRED),
                matchedIds,
                reasons);
    }

    private GovernanceDecision decision(GovernanceDisposition disposition, RiskLevel risk, boolean approvalConsumed, boolean verificationRequired,
                                        List<GovernancePhase> phases, List<String> matchedRules, List<String> reasons) {
        return new GovernanceDecision(disposition, risk, false, verificationRequired, approvalConsumed, phases, matchedRules, reasons);
    }

    private RiskLevel escalateRisk(GovernanceAction action) {
        RiskLevel risk = action.declaredRisk();
        if (action.liveMoney() || action.destructive() || action.irreversible()) return max(risk, RiskLevel.CRITICAL);
        if (action.financial() || action.privileged() || action.publicEffect()) return max(risk, RiskLevel.HIGH);
        if (action.sideEffect()) return max(risk, RiskLevel.MEDIUM);
        return risk;
    }

    private RiskLevel max(RiskLevel left, RiskLevel right) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }
}
