package io.aetheris.orchestrator.policy;

import io.aetheris.orchestrator.rules.OwnerRuleEntity;
import io.aetheris.orchestrator.rules.OwnerRuleService;
import io.aetheris.orchestrator.rules.RuleEffect;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class OwnerRuleCompilerService {

    private final OwnerPolicyService basePolicy;
    private final OwnerRuleService rules;

    public OwnerRuleCompilerService(OwnerPolicyService basePolicy, OwnerRuleService rules) {
        this.basePolicy = basePolicy;
        this.rules = rules;
    }

    public CompiledPolicyDecision evaluate(PolicyEvaluationRequest request) {
        PolicyDecision base = basePolicy.evaluate(request.baseRequest());
        List<String> reasons = new ArrayList<>();
        List<String> matched = new ArrayList<>();
        reasons.add(base.reason());

        if (!base.allowed()) {
            return new CompiledPolicyDecision(false, false, false, false, reasons, matched);
        }

        boolean requiresApproval = base.requiresApproval();
        boolean forceLocal = false;
        boolean forceZeroCost = false;

        for (OwnerRuleEntity rule : rules.activeRules()) {
            if (!scopeMatches(rule.getScope(), request.scope()) || !conditionMatches(rule.getConditionExpression(), request)) {
                continue;
            }

            matched.add(rule.getRuleKey());
            reasons.add("Owner rule " + rule.getRuleKey() + " matched: " + rule.getDescription());

            if (rule.getEffect() == RuleEffect.DENY) {
                return new CompiledPolicyDecision(false, false, forceLocal, forceZeroCost, reasons, matched);
            }
            if (rule.getEffect() == RuleEffect.REQUIRE_APPROVAL) requiresApproval = true;
            if (rule.getEffect() == RuleEffect.FORCE_LOCAL) forceLocal = true;
            if (rule.getEffect() == RuleEffect.FORCE_ZERO_COST) forceZeroCost = true;
        }

        if (forceZeroCost && request.billable()) {
            reasons.add("A matched Owner Rule forces zero-cost execution and the proposed action is billable.");
            return new CompiledPolicyDecision(false, false, forceLocal, true, reasons, matched);
        }
        if (forceLocal && request.sendsDataOffDevice()) {
            reasons.add("A matched Owner Rule forces local execution and the proposed action sends data off-device.");
            return new CompiledPolicyDecision(false, false, true, forceZeroCost, reasons, matched);
        }

        return new CompiledPolicyDecision(true, requiresApproval, forceLocal, forceZeroCost, reasons, matched);
    }

    private boolean scopeMatches(String ruleScope, String actionScope) {
        String normalized = ruleScope == null ? "global" : ruleScope.trim();
        return normalized.equals("*")
                || normalized.equalsIgnoreCase("global")
                || normalized.equalsIgnoreCase(actionScope);
    }

    private boolean conditionMatches(String expression, PolicyEvaluationRequest request) {
        if (expression == null || expression.isBlank() || expression.equalsIgnoreCase("always")) return true;

        String[] clauses = expression.split("\\s*(?:&&|;)\\s*");
        for (String rawClause : clauses) {
            if (rawClause.isBlank()) continue;
            boolean notEquals = rawClause.contains("!=");
            String operator = notEquals ? "!=" : "=";
            int index = rawClause.indexOf(operator);
            if (index <= 0) return false;

            String key = rawClause.substring(0, index).trim();
            String expected = rawClause.substring(index + operator.length()).trim();
            String actual = valueFor(key, request);
            if (actual == null) return false;

            boolean equals = matchesExpected(actual, expected);
            if (notEquals ? equals : !equals) return false;
        }
        return true;
    }

    private boolean matchesExpected(String actual, String expected) {
        if (expected.equals("*")) return true;
        for (String candidate : expected.split(",")) {
            if (actual.equalsIgnoreCase(candidate.trim())) return true;
        }
        return false;
    }

    private String valueFor(String rawKey, PolicyEvaluationRequest request) {
        String key = rawKey.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "agent", "agentid" -> request.agentId();
            case "action" -> request.action();
            case "scope" -> request.scope();
            case "risk", "risklevel" -> request.riskLevel() == null ? "MEDIUM" : request.riskLevel().name();
            case "mode" -> request.mode() == null ? OperationMode.BALANCED.name() : request.mode().name();
            case "billable" -> Boolean.toString(request.billable());
            case "offdevice", "sendsdataoffdevice" -> Boolean.toString(request.sendsDataOffDevice());
            default -> {
                if (key.startsWith("metadata.")) {
                    yield request.metadata().get(key.substring("metadata.".length()));
                }
                yield null;
            }
        };
    }
}
