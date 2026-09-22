package io.aetheris.orchestrator.stage31;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class Stage31FoundationTest {
    private static final Instant PROJECT_TIME = Instant.parse("2026-09-14T05:00:00Z");
    private static final Instant PC_TIME = Instant.parse("2026-09-14T05:01:00Z");

    @Test
    void syntheticPcTwinCannotClaimPhysicalVerification() {
        assertThrows(IllegalArgumentException.class, () -> new PcDigitalTwin(
                "pc-1", "synthetic-rtx4050", Set.of(), Set.of(), Set.of(), Set.of(),
                "synthetic-network", 50, 40, 55, "known-good-1",
                TwinEvidence.SYNTHETIC, true, PC_TIME));
    }

    @Test
    void proactiveEngineSurfacesProjectAndPcProblemsWithoutInventingPhysicalTruth() {
        Stage31ProactiveIntelligenceEngine engine = new Stage31ProactiveIntelligenceEngine();
        List<ProactiveIssue> issues = engine.scan(
                unhealthyProject(),
                syntheticPc(),
                workspace("aetheris-platform"));

        Set<String> codes = issues.stream().map(ProactiveIssue::code).collect(java.util.stream.Collectors.toSet());
        assertTrue(codes.contains("PROJECT_CI_FAILED"));
        assertTrue(codes.contains("PROJECT_TESTS_FAILED"));
        assertTrue(codes.contains("PROJECT_DOCS_INCOMPLETE"));
        assertTrue(codes.contains("PROJECT_DEPENDENCY_DRIFT"));
        assertTrue(codes.contains("PROJECT_DEPLOYMENT_UNHEALTHY"));
        assertTrue(codes.contains("PROJECT_QUOTA_LOW"));
        assertTrue(codes.contains("PC_LOW_DISK"));
        assertTrue(codes.contains("PC_SERVICE_FAILURE"));
        assertTrue(codes.contains("PC_PHYSICAL_VALIDATION_PENDING"));

        ProactiveIssue lowDisk = issues.stream()
                .filter(issue -> issue.code().equals("PC_LOW_DISK"))
                .findFirst().orElseThrow();
        assertEquals(TwinEvidence.SYNTHETIC, lowDisk.evidence());
        assertFalse(lowDisk.ownerAttentionRequired(),
                "synthetic PC telemetry must not be treated as a verified host incident");
    }

    @Test
    void workspaceDriftIsDetected() {
        List<ProactiveIssue> issues = new Stage31ProactiveIntelligenceEngine().scan(
                healthyProject(), syntheticPc(), workspace("different-project"));
        assertTrue(issues.stream().anyMatch(issue -> issue.code().equals("WORKSPACE_PROJECT_DRIFT")));
    }

    @Test
    void goalPriorityBrainIsDeterministic() {
        GoalPriorityBrain brain = new GoalPriorityBrain();
        List<GoalCandidate> goals = List.of(
                new GoalCandidate("docs", "Finish docs", 5, 6, 2, 1, 6),
                new GoalCandidate("ci", "Repair CI", 10, 10, 9, 3, 10),
                new GoalCandidate("feature", "Build optional feature", 4, 7, 3, 5, 5));

        List<PrioritizedGoal> first = brain.rank(goals);
        List<PrioritizedGoal> second = brain.rank(goals);
        assertEquals(first, second);
        assertEquals("ci", first.getFirst().goalId());
    }

    @Test
    void selfHealingPlannerOnlyMarksBoundedReversiblePreapprovedWorkEligible() {
        SelfHealingPlanner planner = new SelfHealingPlanner();
        RecoveryPlan safe = planner.plan(new RecoveryAction(
                "restart-local", RecoveryKind.RESTART_SERVICE, "local-test-service",
                true, true, true, false, false, false));
        assertEquals(RecoveryDisposition.AUTO_ELIGIBLE, safe.disposition());
        assertFalse(safe.executed());

        RecoveryPlan privileged = planner.plan(new RecoveryAction(
                "restart-admin", RecoveryKind.RESTART_SERVICE, "privileged-service",
                true, true, true, true, false, false));
        assertEquals(RecoveryDisposition.OWNER_APPROVAL_REQUIRED, privileged.disposition());
        assertFalse(privileged.executed());
    }

    @Test
    void destructiveAndFinancialRecoveryAreBlocked() {
        SelfHealingPlanner planner = new SelfHealingPlanner();
        RecoveryPlan destructive = planner.plan(new RecoveryAction(
                "delete-data", RecoveryKind.DELETE_USER_DATA, "Documents",
                false, false, false, true, true, false));
        RecoveryPlan financial = planner.plan(new RecoveryAction(
                "trade", RecoveryKind.FINANCIAL_ACTION, "live-account",
                true, false, false, false, false, true));
        assertEquals(RecoveryDisposition.BLOCKED, destructive.disposition());
        assertEquals(RecoveryDisposition.BLOCKED, financial.disposition());
    }

    @Test
    void safeUpdateRequiresVerifiedCanaryHealthBeforePromotion() {
        SafeUpdateManager manager = new SafeUpdateManager();
        assertEquals(UpdateState.STAGED,
                manager.transition(UpdateState.PROPOSED, UpdateEvent.STAGE, false).to());
        assertEquals(UpdateState.CANARY_RUNNING,
                manager.transition(UpdateState.STAGED, UpdateEvent.START_CANARY, false).to());
        assertThrows(IllegalStateException.class,
                () -> manager.transition(UpdateState.CANARY_RUNNING, UpdateEvent.HEALTH_PASS, false));
        assertThrows(IllegalStateException.class,
                () -> manager.transition(UpdateState.STAGED, UpdateEvent.PROMOTE, true));

        UpdateTransition healthy = manager.transition(
                UpdateState.CANARY_RUNNING, UpdateEvent.HEALTH_PASS, true);
        assertEquals(UpdateState.HEALTHY, healthy.to());
        assertEquals(UpdateState.PROMOTED,
                manager.transition(UpdateState.HEALTHY, UpdateEvent.PROMOTE, true).to());
    }

    @Test
    void failedCanaryForcesRollbackPath() {
        SafeUpdateManager manager = new SafeUpdateManager();
        UpdateTransition failed = manager.transition(
                UpdateState.CANARY_RUNNING, UpdateEvent.HEALTH_FAIL, false);
        assertEquals(UpdateState.ROLLBACK_REQUIRED, failed.to());
        assertEquals(UpdateState.ROLLED_BACK,
                manager.transition(UpdateState.ROLLBACK_REQUIRED, UpdateEvent.ROLLBACK, false).to());
    }

    @Test
    void modelBenchmarkingHonorsZeroCostAndPrivatePolicies() {
        ModelBenchmarkEngine engine = new ModelBenchmarkEngine();
        List<ModelBenchmarkSample> samples = List.of(
                new ModelBenchmarkSample("local-fast", 0.80, 250, 45, 0.02, 4200, 0, true),
                new ModelBenchmarkSample("cloud-premium", 0.98, 120, 80, 0.01, 0, 0.08, false),
                new ModelBenchmarkSample("local-slow", 0.72, 700, 25, 0.03, 3000, 0, true));

        List<ModelBenchmarkScore> ranked = engine.rank(samples, true, true);
        assertEquals(2, ranked.size());
        assertEquals("local-fast", ranked.getFirst().modelId());
        assertTrue(ranked.stream().allMatch(score -> score.estimatedCostUsd() == 0.0));
        assertTrue(ranked.stream().allMatch(ModelBenchmarkScore::privateLocal));
    }

    @Test
    void aggregateAssessmentUsesEvidenceTimeAndKeepsPhysicalBoundaryVisible() {
        Stage31DigitalTwinService service = new Stage31DigitalTwinService();
        Stage31AssessmentRequest request = new Stage31AssessmentRequest(
                healthyProject(), syntheticPc(), workspace("aetheris-platform"),
                List.of(new GoalCandidate("stage31", "Finish Stage 31", 8, 9, 8, 3, 10)));

        Stage31AssessmentResult first = service.assess(request);
        Stage31AssessmentResult second = service.assess(request);
        assertEquals(first, second);
        assertTrue(first.physicalValidationPending());
        assertEquals("BLOCKED_PENDING_HARDWARE", first.truthStatus());
        assertEquals(PC_TIME, first.evaluatedAt());
    }

    private static ProjectDigitalTwin unhealthyProject() {
        return new ProjectDigitalTwin(
                "aetheris-platform", "deadbeef", "stage-31",
                Set.of("orchestrator-service"), Set.of("bug-1"), Set.of("task-1"), Set.of("stage31"),
                false, false, 2, false, true, 3.0, TwinEvidence.OBSERVED, PROJECT_TIME);
    }

    private static ProjectDigitalTwin healthyProject() {
        return new ProjectDigitalTwin(
                "aetheris-platform", "cafebabe", "stage-31",
                Set.of("orchestrator-service"), Set.of(), Set.of("task-1"), Set.of("stage31"),
                true, true, 0, true, false, 80.0, TwinEvidence.OBSERVED, PROJECT_TIME);
    }

    private static PcDigitalTwin syntheticPc() {
        return new PcDigitalTwin(
                "owner-pc", "synthetic-target-profile",
                Set.of("gpu-driver-placeholder"), Set.of("docker-placeholder"),
                Set.of("docker", "ollama"), Set.of("ollama"), "synthetic-network",
                10.0, 96.0, 92.0, "known-good-placeholder",
                TwinEvidence.SYNTHETIC, false, PC_TIME);
    }

    private static OwnerWorkspaceModel workspace(String activeProject) {
        return new OwnerWorkspaceModel(
                "primary-owner", activeProject, Set.of("portfolio"), Set.of("ai"),
                Set.of("safe-health-scan"), Set.of("local-tools"), true, true);
    }
}
