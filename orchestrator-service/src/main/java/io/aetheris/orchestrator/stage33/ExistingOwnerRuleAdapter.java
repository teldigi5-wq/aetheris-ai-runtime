package io.aetheris.orchestrator.stage33;

import io.aetheris.orchestrator.policy.CompiledPolicyDecision;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.rules.OwnerRuleEntity;
import io.aetheris.orchestrator.rules.RuleEffect;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Adapts the existing persisted owner-rule/compiler result into Stage 33's
 * ephemeral governance representation. Stage 33 does not persist a second
 * owner-rule source and deliberately does not re-parse condition expressions.
 */
final class ExistingOwnerRuleAdapter {

    List<OwnerGovernanceRule> normalize(
            CompiledPolicyDecision compiled,
            List<OwnerRuleEntity> activeRules,
            GovernanceAction action) {

        if (compiled == null) throw new IllegalArgumentException("compiled owner-policy decision is required");
        if (action == null) throw new IllegalArgumentException("governance action is required");

        List<OwnerGovernanceRule> normalized = new ArrayList<>();
        List<OwnerRuleEntity> rules = activeRules == null ? List.of() : activeRules;
        Set<String> matchedKeys = new HashSet<>(compiled.matchedRuleKeys());

        // Only rules already reported as matched by the canonical compiler are
        // projected into Stage 33. This avoids a second condition-expression evaluator.
        for (OwnerRuleEntity rule : rules) {
            if (!rule.isEnabled() || !matchedKeys.contains(rule.getRuleKey())) continue;
            normalized.add(new OwnerGovernanceRule(
                    rule.getRuleKey(),
                    action.scope(),
                    action.action(),
                    map(rule.getEffect()),
                    clampPriority(rule.getPriority()),
                    rule.getDescription()));
        }

        // Detect only structurally identical ALLOW/DENY rules in an applicable
        // scope. The affected scope fails closed until the owner resolves the conflict.
        if (hasStructuralAllowDenyConflict(rules, action.scope())) {
            normalized.add(new OwnerGovernanceRule(
                    "existing-owner-rule-conflict",
                    action.scope(),
                    action.action(),
                    GovernanceRuleEffect.BLOCK,
                    1000,
                    "Existing owner rules contain an equal-priority ALLOW/DENY conflict"));
        }

        boolean legacyPrivateBaseDenial = !compiled.allowed()
                && compiled.matchedRuleKeys().isEmpty()
                && action.mode() == OperationMode.PRIVATE
                && action.sendsDataOffDevice()
                && action.dataClassification().protectedData();

        // The legacy PRIVATE base-policy denial is intentionally deferred to the
        // stricter Stage 33 exact-action/exact-scope approval gate. Other canonical
        // compiler denials remain hard blocks.
        if (!compiled.allowed() && !legacyPrivateBaseDenial) {
            normalized.add(synthetic(
                    "compiled-owner-policy-deny",
                    GovernanceRuleEffect.BLOCK,
                    action,
                    compiled.reasons()));
        }
        if (compiled.requiresApproval()) {
            normalized.add(synthetic(
                    "compiled-owner-policy-approval",
                    GovernanceRuleEffect.REQUIRE_APPROVAL,
                    action,
                    compiled.reasons()));
        }
        if (compiled.forceLocal()) {
            normalized.add(synthetic(
                    "compiled-owner-policy-local",
                    GovernanceRuleEffect.FORCE_LOCAL,
                    action,
                    compiled.reasons()));
        }
        if (compiled.forceZeroCost()) {
            normalized.add(synthetic(
                    "compiled-owner-policy-zero-cost",
                    GovernanceRuleEffect.FORCE_ZERO_COST,
                    action,
                    compiled.reasons()));
        }

        return List.copyOf(normalized);
    }

    boolean hasStructuralAllowDenyConflict(List<OwnerRuleEntity> activeRules, String actionScope) {
        if (activeRules == null || activeRules.isEmpty()) return false;
        Map<String, EnumSet<RuleEffect>> effects = new HashMap<>();
        for (OwnerRuleEntity rule : activeRules) {
            if (!rule.isEnabled() || !scopeApplies(rule.getScope(), actionScope)) continue;
            String key = clampPriority(rule.getPriority()) + "|"
                    + normalize(rule.getScope()) + "|"
                    + normalize(rule.getConditionExpression());
            effects.computeIfAbsent(key, ignored -> EnumSet.noneOf(RuleEffect.class)).add(rule.getEffect());
        }
        return effects.values().stream().anyMatch(set -> set.contains(RuleEffect.ALLOW) && set.contains(RuleEffect.DENY));
    }

    private OwnerGovernanceRule synthetic(
            String id,
            GovernanceRuleEffect effect,
            GovernanceAction action,
            List<String> reasons) {
        return new OwnerGovernanceRule(
                id,
                action.scope(),
                action.action(),
                effect,
                1000,
                reasons == null || reasons.isEmpty() ? id : String.join(" | ", reasons));
    }

    private GovernanceRuleEffect map(RuleEffect effect) {
        return switch (effect) {
            case ALLOW -> GovernanceRuleEffect.ALLOW;
            case DENY -> GovernanceRuleEffect.BLOCK;
            case REQUIRE_APPROVAL -> GovernanceRuleEffect.REQUIRE_APPROVAL;
            case FORCE_LOCAL -> GovernanceRuleEffect.FORCE_LOCAL;
            case FORCE_ZERO_COST -> GovernanceRuleEffect.FORCE_ZERO_COST;
        };
    }

    private boolean scopeApplies(String ruleScope, String actionScope) {
        String normalizedRule = normalize(ruleScope);
        String normalizedAction = normalize(actionScope);
        return normalizedRule.equals("*") || normalizedRule.equals("global") || normalizedRule.equals(normalizedAction);
    }

    private int clampPriority(int priority) {
        return Math.max(0, Math.min(priority, 1000));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
