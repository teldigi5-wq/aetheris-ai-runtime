package io.aetheris.orchestrator.stage33;

import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.policy.CompiledPolicyDecision;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.rules.OwnerRuleEntity;
import io.aetheris.orchestrator.rules.RuleEffect;
import io.aetheris.orchestrator.stage32.EmergencyMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Stage33GovernanceTest {
    private static final Instant NOW = Instant.parse("2026-09-14T06:30:00Z");

    @Test
    void zeroCostHardBlockCannotBeOverriddenByAllowRule() {
        GovernanceAction action = action("paid", OperationMode.ZERO_COST, RiskLevel.LOW, false, false, false, false, false, false, false, false, true, 0.01, DataClassification.PUBLIC);
        OwnerGovernanceRule allow = new OwnerGovernanceRule("allow-paid", "local", "read", GovernanceRuleEffect.ALLOW, 1000, "model asks to allow");
        GovernanceDecision result = engine().preflight(action, List.of(allow), null, true, EmergencyMode.NORMAL, NOW);
        assertEquals(GovernanceDisposition.BLOCKED, result.disposition());
    }

    @Test
    void privateModeRequiresExplicitScopedOverrideBeforeProtectedDataLeavesDevice() {
        GovernanceAction action = action("private", OperationMode.PRIVATE, RiskLevel.LOW, true, false, false, false, false, false, false, false, false, 0, DataClassification.PRIVATE);
        GovernanceDecision missing = engine().preflight(action, List.of(), null, true, EmergencyMode.NORMAL, NOW);
        assertEquals(GovernanceDisposition.APPROVAL_REQUIRED, missing.disposition());

        ApprovalGrant grant = grant("private-token", action, true, false, NOW.plusSeconds(60));
        GovernanceDecision approved = engine().preflight(action, List.of(), grant, true, EmergencyMode.NORMAL, NOW);
        assertEquals(GovernanceDisposition.ALLOW, approved.disposition());
        assertTrue(approved.executionEligible());
    }

    @Test
    void liveMoneyRemainsHardBlocked() {
        GovernanceAction action = action("live-money", OperationMode.BALANCED, RiskLevel.HIGH, false, false, true, true, false, false, false, true, false, 0, DataClassification.INTERNAL);
        GovernanceDecision result = engine().preflight(action, List.of(), grant("money", action, false, false, NOW.plusSeconds(60)), true, EmergencyMode.NORMAL, NOW);
        assertEquals(GovernanceDisposition.BLOCKED, result.disposition());
        assertEquals(RiskLevel.CRITICAL, result.effectiveRisk());
    }

    @Test
    void destructiveActionRequiresSimulationBeforeApproval() {
        GovernanceAction action = action("destructive", OperationMode.BALANCED, RiskLevel.MEDIUM, false, false, false, false, true, true, true, true, false, 0, DataClassification.INTERNAL);
        ApprovalGrant grant = grant("destructive-token", action, false, false, NOW.plusSeconds(60));
        GovernanceDecision notSimulated = engine().preflight(action, List.of(), grant, false, EmergencyMode.NORMAL, NOW);
        assertEquals(GovernanceDisposition.SIMULATION_REQUIRED, notSimulated.disposition());

        GovernanceDecision simulated = engine().preflight(action, List.of(), grant, true, EmergencyMode.NORMAL, NOW);
        assertEquals(GovernanceDisposition.ALLOW, simulated.disposition());
        assertTrue(simulated.verificationRequired());
    }

    @Test
    void samePriorityAllowBlockConflictFailsClosed() {
        GovernanceAction action = localRead("conflict");
        List<OwnerGovernanceRule> rules = List.of(
                new OwnerGovernanceRule("allow", "local", "read", GovernanceRuleEffect.ALLOW, 100, "allow"),
                new OwnerGovernanceRule("block", "local", "read", GovernanceRuleEffect.BLOCK, 100, "block"));
        GovernanceDecision result = engine().preflight(action, rules, null, true, EmergencyMode.NORMAL, NOW);
        assertEquals(GovernanceDisposition.BLOCKED, result.disposition());
        assertTrue(result.reasons().stream().anyMatch(reason -> reason.contains("fail closed")));
    }

    @Test
    void existingOwnerRuleAdapterUsesCanonicalCompilerMatchesInsteadOfSecondRuleParser() {
        GovernanceAction action = localRead("existing-rule");
        OwnerRuleEntity deny = new OwnerRuleEntity(
                UUID.randomUUID(), "deny-read", "deny canonical read", "local", "action=read",
                RuleEffect.DENY, 500, true, 1);
        CompiledPolicyDecision compiled = new CompiledPolicyDecision(
                false, false, false, false, List.of("canonical owner deny"), List.of("deny-read"));

        List<OwnerGovernanceRule> normalized = new ExistingOwnerRuleAdapter().normalize(compiled, List.of(deny), action);
        GovernanceDecision result = engine().preflight(action, normalized, null, true, EmergencyMode.NORMAL, NOW);
        assertEquals(GovernanceDisposition.BLOCKED, result.disposition());
        assertTrue(result.matchedRules().contains("deny-read"));
    }

    @Test
    void structurallyConflictingExistingOwnerRulesFailClosedForAffectedScope() {
        GovernanceAction action = localRead("existing-conflict");
        OwnerRuleEntity allow = new OwnerRuleEntity(
                UUID.randomUUID(), "allow-read", "allow", "local", "action=read",
                RuleEffect.ALLOW, 300, true, 1);
        OwnerRuleEntity deny = new OwnerRuleEntity(
                UUID.randomUUID(), "deny-read", "deny", "local", "action=read",
                RuleEffect.DENY, 300, true, 1);
        CompiledPolicyDecision compiled = new CompiledPolicyDecision(
                true, false, false, false, List.of("base allowed"), List.of("allow-read"));

        List<OwnerGovernanceRule> normalized = new ExistingOwnerRuleAdapter().normalize(compiled, List.of(allow, deny), action);
        GovernanceDecision result = engine().preflight(action, normalized, null, true, EmergencyMode.NORMAL, NOW);
        assertEquals(GovernanceDisposition.BLOCKED, result.disposition());
        assertTrue(result.matchedRules().contains("existing-owner-rule-conflict"));
    }

    @Test
    void expiredAndWrongScopeApprovalsAreRejected() {
        GovernanceAction action = action("privileged", OperationMode.BALANCED, RiskLevel.HIGH, false, false, false, false, false, true, false, true, false, 0, DataClassification.INTERNAL);
        ApprovalGrant expired = grant("expired", action, false, false, NOW.minusSeconds(1));
        assertEquals(GovernanceDisposition.APPROVAL_REQUIRED, engine().preflight(action, List.of(), expired, true, EmergencyMode.NORMAL, NOW).disposition());

        ApprovalGrant wrongScope = new ApprovalGrant("wrong-scope", "owner", action.actionId(), "other", NOW.minusSeconds(10), NOW.plusSeconds(60), false, false);
        assertEquals(GovernanceDisposition.APPROVAL_REQUIRED, engine().preflight(action, List.of(), wrongScope, true, EmergencyMode.NORMAL, NOW).disposition());
    }

    @Test
    void oneTimeApprovalCannotBeReplayed() {
        ScopedApprovalService approvals = new ScopedApprovalService();
        CrossSystemGovernanceEngine engine = new CrossSystemGovernanceEngine(new GovernanceLifecycle(), approvals);
        GovernanceAction action = action("once", OperationMode.BALANCED, RiskLevel.HIGH, false, true, false, false, false, false, false, true, false, 0, DataClassification.PUBLIC);
        ApprovalGrant grant = grant("once-token", action, false, true, NOW.plusSeconds(60));
        assertEquals(GovernanceDisposition.ALLOW, engine.preflight(action, List.of(), grant, true, EmergencyMode.NORMAL, NOW).disposition());
        assertTrue(approvals.consumed("once-token"));
        assertEquals(GovernanceDisposition.APPROVAL_REQUIRED, engine.preflight(action, List.of(), grant, true, EmergencyMode.NORMAL, NOW).disposition());
    }

    @Test
    void lifecycleCannotJumpFromPlanToExecute() {
        GovernanceLifecycle lifecycle = new GovernanceLifecycle();
        assertFalse(lifecycle.canAdvance(GovernancePhase.PLAN, GovernancePhase.EXECUTE));
        assertThrows(IllegalStateException.class, () -> lifecycle.requireAdvance(GovernancePhase.PLAN, GovernancePhase.EXECUTE));
        assertTrue(lifecycle.canAdvance(GovernancePhase.PLAN, GovernancePhase.CHECK_RULES));
    }

    @Test
    void emergencyStopOverridesOtherwiseAllowedAction() {
        GovernanceDecision result = engine().preflight(localRead("stop"), List.of(), null, true, EmergencyMode.STOP, NOW);
        assertEquals(GovernanceDisposition.BLOCKED, result.disposition());
    }

    @Test
    void lowRiskLocalReadIsEligibleWithoutApproval() {
        GovernanceDecision result = engine().preflight(localRead("safe-read"), List.of(), null, false, EmergencyMode.NORMAL, NOW);
        assertEquals(GovernanceDisposition.ALLOW, result.disposition());
        assertTrue(result.executionEligible());
        assertFalse(result.verificationRequired());
    }

    @Test
    void noExecutionObservationDoesNotMarkExecuteComplete() {
        GovernanceDecision preflight = engine().preflight(localRead("not-run"), List.of(), null, false, EmergencyMode.NORMAL, NOW);
        GovernanceExecutionRecord result = new GovernanceVerificationService().finalizeExecution(preflight, null, null);
        assertEquals(ExecutionTruthStatus.UNVERIFIED, result.status());
        assertFalse(result.completedPhases().contains(GovernancePhase.EXECUTE));
        assertEquals(GovernancePhase.APPROVE_WHEN_REQUIRED, result.completedPhases().getLast());
    }

    @Test
    void successWithoutVerificationIsExplicitlyUnverified() {
        GovernanceDecision preflight = engine().preflight(localRead("unverified"), List.of(), null, false, EmergencyMode.NORMAL, NOW);
        GovernanceExecutionRecord result = new GovernanceVerificationService().finalizeExecution(
                preflight,
                new ExecutionObservation(true, true, "worker-result"),
                null);
        assertEquals(ExecutionTruthStatus.UNVERIFIED, result.status());
    }

    @Test
    void verifiedSuccessRequiresEvidenceAndCompletesReportPhase() {
        GovernanceDecision preflight = engine().preflight(localRead("verified"), List.of(), null, false, EmergencyMode.NORMAL, NOW);
        GovernanceExecutionRecord result = new GovernanceVerificationService().finalizeExecution(
                preflight,
                new ExecutionObservation(true, true, "worker-result"),
                new VerificationReceipt(true, true, "deterministic-check", Set.of("sha256:abc")));
        assertEquals(ExecutionTruthStatus.VERIFIED_SUCCESS, result.status());
        assertEquals(GovernancePhase.REPORT, result.completedPhases().getLast());
    }

    @Test
    void publicSideEffectEscalatesRiskAndRequiresApproval() {
        GovernanceAction action = action("public", OperationMode.BALANCED, RiskLevel.LOW, false, true, false, false, false, false, false, true, false, 0, DataClassification.PUBLIC);
        GovernanceDecision result = engine().preflight(action, List.of(), null, true, EmergencyMode.NORMAL, NOW);
        assertEquals(RiskLevel.HIGH, result.effectiveRisk());
        assertEquals(GovernanceDisposition.APPROVAL_REQUIRED, result.disposition());
    }

    private static CrossSystemGovernanceEngine engine() { return new CrossSystemGovernanceEngine(); }

    private static GovernanceAction localRead(String id) {
        return action(id, OperationMode.BALANCED, RiskLevel.LOW, false, false, false, false, false, false, false, false, false, 0, DataClassification.INTERNAL);
    }

    private static GovernanceAction action(String id, OperationMode mode, RiskLevel risk,
                                           boolean offDevice, boolean publicEffect, boolean financial, boolean liveMoney,
                                           boolean destructive, boolean privileged, boolean irreversible, boolean sideEffect,
                                           boolean billable, double cost, DataClassification classification) {
        return new GovernanceAction(id, "owner", "aetheris", "read", "local", mode, risk, classification,
                billable, cost, offDevice, publicEffect, financial, liveMoney, destructive, privileged, irreversible, sideEffect);
    }

    private static ApprovalGrant grant(String token, GovernanceAction action, boolean privateOverride, boolean oneTime, Instant expiry) {
        return new ApprovalGrant(token, "owner", action.actionId(), action.scope(), NOW.minusSeconds(10), expiry, oneTime, privateOverride);
    }
}
