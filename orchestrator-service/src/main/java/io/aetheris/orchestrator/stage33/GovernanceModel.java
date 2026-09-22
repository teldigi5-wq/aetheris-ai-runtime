package io.aetheris.orchestrator.stage33;

import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.policy.OperationMode;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class GovernanceModel {
    private GovernanceModel() {}
}

enum GovernancePhase {
    UNDERSTAND,
    PLAN,
    CHECK_RULES,
    ASSESS_RISK,
    SIMULATE_PREVIEW,
    APPROVE_WHEN_REQUIRED,
    EXECUTE,
    VERIFY,
    RECORD,
    LEARN,
    REPORT
}

enum GovernanceDisposition {
    BLOCKED,
    SIMULATION_REQUIRED,
    APPROVAL_REQUIRED,
    ALLOW
}

enum DataClassification {
    PUBLIC,
    INTERNAL,
    PRIVATE,
    SECRET;

    boolean protectedData() { return this == PRIVATE || this == SECRET; }
}

enum GovernanceRuleEffect {
    BLOCK,
    FORCE_ZERO_COST,
    FORCE_LOCAL,
    REQUIRE_SIMULATION,
    REQUIRE_APPROVAL,
    REQUIRE_VERIFICATION,
    ALLOW
}

record GovernanceAction(
        String actionId,
        String actorId,
        String system,
        String action,
        String scope,
        OperationMode mode,
        RiskLevel declaredRisk,
        DataClassification dataClassification,
        boolean billable,
        double estimatedCostUsd,
        boolean sendsDataOffDevice,
        boolean publicEffect,
        boolean financial,
        boolean liveMoney,
        boolean destructive,
        boolean privileged,
        boolean irreversible,
        boolean sideEffect) {

    GovernanceAction {
        actionId = require(actionId, "actionId");
        actorId = require(actorId, "actorId");
        system = require(system, "system");
        action = require(action, "action");
        scope = require(scope, "scope");
        mode = mode == null ? OperationMode.BALANCED : mode;
        declaredRisk = declaredRisk == null ? RiskLevel.MEDIUM : declaredRisk;
        dataClassification = dataClassification == null ? DataClassification.INTERNAL : dataClassification;
        if (!Double.isFinite(estimatedCostUsd) || estimatedCostUsd < 0) throw new IllegalArgumentException("estimatedCostUsd must be finite and non-negative");
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}

record OwnerGovernanceRule(
        String ruleId,
        String scope,
        String actionPattern,
        GovernanceRuleEffect effect,
        int priority,
        String reason) {

    OwnerGovernanceRule {
        ruleId = Objects.requireNonNull(ruleId, "ruleId").trim();
        scope = scope == null || scope.isBlank() ? "global" : scope.trim();
        actionPattern = actionPattern == null || actionPattern.isBlank() ? "*" : actionPattern.trim();
        effect = Objects.requireNonNull(effect, "effect");
        reason = reason == null ? "" : reason.trim();
        if (ruleId.isBlank()) throw new IllegalArgumentException("ruleId is required");
        if (priority < 0 || priority > 1000) throw new IllegalArgumentException("priority must be between 0 and 1000");
    }

    boolean matches(GovernanceAction request) {
        boolean scopeMatch = scope.equals("*") || scope.equalsIgnoreCase("global") || scope.equalsIgnoreCase(request.scope());
        boolean actionMatch = actionPattern.equals("*") || actionPattern.equalsIgnoreCase(request.action());
        return scopeMatch && actionMatch;
    }
}

record ApprovalGrant(
        String tokenId,
        String approverId,
        String actionId,
        String scope,
        Instant issuedAt,
        Instant expiresAt,
        boolean oneTime,
        boolean privateModeOverride) {

    ApprovalGrant {
        tokenId = Objects.requireNonNull(tokenId, "tokenId").trim();
        approverId = Objects.requireNonNull(approverId, "approverId").trim();
        actionId = Objects.requireNonNull(actionId, "actionId").trim();
        scope = Objects.requireNonNull(scope, "scope").trim();
        issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (tokenId.isBlank() || approverId.isBlank() || actionId.isBlank() || scope.isBlank()) throw new IllegalArgumentException("approval identity and scope fields are required");
        if (!expiresAt.isAfter(issuedAt)) throw new IllegalArgumentException("approval expiry must be after issue time");
    }
}

record ApprovalValidation(boolean valid, boolean consumed, String reason) {}

record GovernanceDecision(
        GovernanceDisposition disposition,
        RiskLevel effectiveRisk,
        boolean executionEligible,
        boolean verificationRequired,
        boolean approvalConsumed,
        List<GovernancePhase> completedPhases,
        List<String> matchedRules,
        List<String> reasons) {

    GovernanceDecision {
        disposition = Objects.requireNonNull(disposition, "disposition");
        effectiveRisk = Objects.requireNonNull(effectiveRisk, "effectiveRisk");
        completedPhases = List.copyOf(completedPhases == null ? List.of() : completedPhases);
        matchedRules = List.copyOf(matchedRules == null ? List.of() : matchedRules);
        reasons = List.copyOf(reasons == null ? List.of() : reasons);
        if (executionEligible && disposition != GovernanceDisposition.ALLOW) throw new IllegalArgumentException("only ALLOW decisions can be execution eligible");
    }
}

enum ExecutionTruthStatus {
    BLOCKED,
    UNVERIFIED,
    VERIFIED_SUCCESS,
    VERIFIED_FAILURE
}

record ExecutionObservation(boolean attempted, boolean completed, String outcomeRef) {
    ExecutionObservation {
        outcomeRef = outcomeRef == null ? "" : outcomeRef.trim();
    }
}

record VerificationReceipt(boolean verified, boolean success, String verifier, Set<String> evidenceRefs) {
    VerificationReceipt {
        verifier = verifier == null ? "" : verifier.trim();
        evidenceRefs = Set.copyOf(evidenceRefs == null ? Set.of() : evidenceRefs);
    }
}

record GovernanceExecutionRecord(
        ExecutionTruthStatus status,
        List<GovernancePhase> completedPhases,
        Set<String> evidenceRefs,
        String reason) {
    GovernanceExecutionRecord {
        status = Objects.requireNonNull(status, "status");
        completedPhases = List.copyOf(completedPhases == null ? List.of() : completedPhases);
        evidenceRefs = Set.copyOf(evidenceRefs == null ? Set.of() : evidenceRefs);
        reason = reason == null ? "" : reason;
    }
}
