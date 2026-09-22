package io.aetheris.orchestrator;

import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.approval.*;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.stage14.*;
import io.aetheris.orchestrator.stage15.*;
import io.aetheris.orchestrator.task.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class Stage15IntegrationTest {
    @Autowired Stage15ServiceCatalogService catalog;
    @Autowired Stage15ReliabilityService reliability;
    @Autowired Stage15ChaosRehearsalService chaos;
    @Autowired Stage15RecoveryService recovery;
    @Autowired Stage15IncidentReplayService replay;
    @Autowired Stage15DeliveryReceiptService receipts;
    @Autowired Stage15ReliabilityScoreService scores;
    @Autowired Stage14OperationalIntelligenceService operations;
    @Autowired TaskService tasks;
    @Autowired ApprovalService approvals;

    @Test
    void catalogRejectsDangerousAuthorityAndPersistsMaintenanceWindow() {
        var service = register("stage15-catalog", "SERVICE", "HIGH", Set.of("RESTART_SERVICE", "ROLLBACK_RELEASE"), Set.of(), 9900);
        assertThat(service.getAllowedActions()).containsExactlyInAnyOrder("RESTART_SERVICE", "ROLLBACK_RELEASE");
        assertThatThrownBy(() -> catalog.register(new Stage15ServiceCatalogService.ServiceRegistration(
                "stage15-forbidden", "Forbidden", "SERVICE", "HIGH", 9900, 438,
                Set.of("ARBITRARY_SHELL"), Set.of(), true))).isInstanceOf(IllegalArgumentException.class);
        var maintained = catalog.scheduleMaintenance(service.getId(), new Stage15ServiceCatalogService.MaintenanceRequest(
                Instant.now().minusSeconds(1), Instant.now().plusSeconds(60), "Controlled maintenance fixture"));
        assertThat(maintained.isMaintenanceActive(Instant.now())).isTrue();
    }

    @Test
    void chaosRehearsalUsesDependencyGraphAndNeverTouchesProduction() {
        register("stage15-db", "SERVICE", "MEDIUM", Set.of("RESTART_SERVICE", "ROLLBACK_RELEASE"), Set.of(), 9900);
        register("stage15-api", "SERVICE", "HIGH", Set.of("RESTART_SERVICE", "ROLLBACK_RELEASE"), Set.of("stage15-db"), 9900);
        var result = chaos.rehearse(new Stage15ChaosRehearsalService.RehearsalRequest(
                "stage15-db", "DEPENDENCY_UNAVAILABLE", true, sha("chaos-proof")));
        assertThat(result.status()).isEqualTo("REHEARSAL_COMPLETED_SIMULATION_ONLY");
        assertThat(result.affectedServices()).contains("stage15-db", "stage15-api");
        assertThat(result.simulationOnly()).isTrue();
        assertThat(result.productionAffected()).isFalse();
        assertThat(result.externalActionAttempted()).isFalse();
    }

    @Test
    void exhaustedErrorBudgetBlocksExecutionEvenAfterOwnerApproval() {
        register("stage15-budget", "SERVICE", "CRITICAL", Set.of("RESTART_SERVICE", "ROLLBACK_RELEASE"), Set.of(), 9900);
        reliability.record(new Stage15ReliabilityService.EvidenceRequest("stage15-budget", 1000, 20, true, sha("budget-evidence"), Instant.now()));
        assertThat(reliability.assess("stage15-budget").status()).isEqualTo("ERROR_BUDGET_EXHAUSTED");

        var incident = criticalIncident("stage15-budget");
        var task = planningTask("Stage 15 budget gate");
        var plan = recovery.create(new Stage15RecoveryService.RecoveryPlanRequest(incident.getId(), task.getId(),
                "stage15-budget", "RESTART_SERVICE", "ROLLBACK_RELEASE", "HEALTHY_ATTESTATION", sha("budget-plan")));
        var approval = approve(task.getId(), Stage15RecoveryService.APPROVAL_ACTION);
        var authorized = recovery.authorize(plan.getId(), approval.getId());
        assertThat(authorized.getStatus()).isEqualTo("AUTHORIZED_PENDING_TARGET_EVIDENCE");
        assertThat(authorized.isExecutedByAetheris()).isFalse();
        var readiness = recovery.executionReadiness(plan.getId());
        assertThat(readiness.status()).isEqualTo("BLOCKED");
        assertThat(readiness.blockers()).anyMatch(x -> x.contains("error budget"));
        assertThat(readiness.executionAttempted()).isFalse();
    }

    @Test
    void recoveryRequiresExactApprovalTargetEvidenceVerificationAndRollbackOnFailure() {
        register("stage15-recovery", "SERVICE", "HIGH", Set.of("RESTART_SERVICE", "ROLLBACK_RELEASE"), Set.of(), 9900);
        reliability.record(new Stage15ReliabilityService.EvidenceRequest("stage15-recovery", 1000, 0, true, sha("recovery-evidence-a"), Instant.now().minusSeconds(1)));
        reliability.record(new Stage15ReliabilityService.EvidenceRequest("stage15-recovery", 1000, 0, true, sha("recovery-evidence-b"), Instant.now()));
        var incident = criticalIncident("stage15-recovery");
        var task = planningTask("Stage 15 recovery");
        var plan = recovery.create(new Stage15RecoveryService.RecoveryPlanRequest(incident.getId(), task.getId(),
                "stage15-recovery", "RESTART_SERVICE", "ROLLBACK_RELEASE", "HEALTHY_ATTESTATION", sha("recovery-plan")));

        var wrong = approve(task.getId(), "STAGE15_WRONG_ACTION");
        assertThatThrownBy(() -> recovery.authorize(plan.getId(), wrong.getId())).isInstanceOf(IllegalArgumentException.class);
        var approval = approve(task.getId(), Stage15RecoveryService.APPROVAL_ACTION);
        var authorized = recovery.authorize(plan.getId(), approval.getId());
        assertThat(authorized.getStatus()).isEqualTo("AUTHORIZED_PENDING_TARGET_EVIDENCE");
        assertThat(recovery.executionReadiness(plan.getId()).blockers()).anyMatch(x -> x.contains("target execution adapter"));

        var observed = recovery.recordTargetExecutionEvidence(plan.getId(),
                new Stage15RecoveryService.TargetExecutionEvidence(true, true, sha("target-execution")));
        assertThat(observed.getStatus()).isEqualTo("TARGET_EXECUTION_EVIDENCE_RECORDED");
        assertThat(observed.isExecutedByAetheris()).isFalse();
        var verified = recovery.verify(plan.getId(), new Stage15RecoveryService.VerificationEvidence(true, "UNAVAILABLE", sha("post-action-health")));
        assertThat(verified.getStatus()).isEqualTo("VERIFY_FAILED_ROLLBACK_REQUIRED");
        assertThat(verified.isRollbackRequired()).isTrue();
    }

    @Test
    void incidentReplayDeliveryReceiptsAndReliabilityScoreStayEvidenceHonest() {
        register("stage15-replay", "SERVICE", "HIGH", Set.of("RESTART_SERVICE", "ROLLBACK_RELEASE"), Set.of(), 9900);
        reliability.record(new Stage15ReliabilityService.EvidenceRequest("stage15-replay", 200, 0, true, sha("replay-evidence"), Instant.now()));
        var incident = criticalIncident("stage15-replay");
        var task = planningTask("Stage 15 replay");
        recovery.create(new Stage15RecoveryService.RecoveryPlanRequest(incident.getId(), task.getId(),
                "stage15-replay", "RESTART_SERVICE", "ROLLBACK_RELEASE", "HEALTHY_ATTESTATION", sha("replay-plan")));

        assertThatThrownBy(() -> receipts.record(new Stage15DeliveryReceiptService.ReceiptRequest(incident.getId(),
                "notify-provider", "msg-001", "DELIVERED", false, sha("fake-receipt"), Instant.now())))
                .isInstanceOf(IllegalArgumentException.class);
        var receipt = receipts.record(new Stage15DeliveryReceiptService.ReceiptRequest(incident.getId(),
                "notify-provider", "msg-002", "DELIVERED", true, sha("real-receipt-fixture"), Instant.now()));
        assertThat(receipt.getDeliveryStatus()).isEqualTo("DELIVERED");

        var replayResult = replay.replay(incident.getId());
        assertThat(replayResult.status()).isEqualTo("REPLAY_READY");
        assertThat(replayResult.readOnly()).isTrue();
        assertThat(replayResult.timeline()).extracting(Stage15IncidentReplayService.ReplayEvent::type)
                .contains("SIGNAL", "RECOVERY_PLAN");
        var score = scores.score("stage15-replay");
        assertThat(score.status()).isEqualTo("SCORED");
        assertThat(score.score()).isBetween(0, 100);
        assertThat(score.externalActionAttempted()).isFalse();
    }

    private Stage15ServiceCatalogEntity register(String id, String type, String criticality, Set<String> actions,
                                                 Set<String> dependencies, int slo) {
        return catalog.register(new Stage15ServiceCatalogService.ServiceRegistration(id, id, type, criticality,
                slo, 438, actions, dependencies, true));
    }
    private Stage14IncidentEntity criticalIncident(String serviceId) {
        return operations.record(new Stage14OperationalIntelligenceService.SignalRequest(
                "SERVICE", serviceId, "RECOVERY_REQUIRED", "CRITICAL", sha("fingerprint-" + serviceId),
                "Critical recovery fixture", true, sha("incident-evidence-" + serviceId), Instant.now())).incident();
    }
    private TaskEntity planningTask(String title) {
        TaskEntity task = tasks.create(new CreateTaskRequest(title, "Stage 15 governed recovery fixture", OperationMode.PRIVATE));
        return tasks.transition(task.getId(), new TaskTransitionRequest(TaskState.PLANNING, "incident-commander", "Prepare Stage 15 recovery"));
    }
    private ApprovalEntity approve(UUID taskId, String action) {
        var approval = approvals.request(new CreateApprovalRequest(taskId, action, "Owner approval fixture for " + action, RiskLevel.HIGH));
        return approvals.decide(approval.getId(), new ApprovalDecisionRequest(true, "Approved for Stage 15 test fixture"));
    }
    private String sha(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
