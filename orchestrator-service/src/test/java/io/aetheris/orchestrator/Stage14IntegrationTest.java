package io.aetheris.orchestrator;

import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.approval.*;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.stage12.Stage12ProviderRegistryService;
import io.aetheris.orchestrator.stage13.Stage13ProviderHealthEvidenceService;
import io.aetheris.orchestrator.stage14.*;
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
class Stage14IntegrationTest {
    @Autowired Stage12ProviderRegistryService providers;
    @Autowired Stage13ProviderHealthEvidenceService providerHealth;
    @Autowired Stage14OperationalIntelligenceService operations;
    @Autowired Stage14SloMonitorService slo;
    @Autowired Stage14ContainmentService containment;
    @Autowired Stage14SelfHealingProposalService healing;
    @Autowired Stage14ChangeImpactService impact;
    @Autowired Stage14DeploymentRehearsalService rehearsal;
    @Autowired Stage14EscalationService escalation;
    @Autowired Stage14PostIncidentReportService reports;
    @Autowired TaskService tasks;
    @Autowired ApprovalService approvals;

    @Test
    void providerSloFailureCreatesCriticalIncidentCandidateWithoutDisablingProvider() {
        registerProvider("stage14-slo-provider", "NOTIFICATION", Set.of("SEND_NOTIFICATION"), false);
        providerHealth.record(new Stage13ProviderHealthEvidenceService.HealthEvidenceRequest(
                "stage14-slo-provider", "HEALTHY", 30, true, sha("slo-1"), Instant.now().minusSeconds(3)));
        providerHealth.record(new Stage13ProviderHealthEvidenceService.HealthEvidenceRequest(
                "stage14-slo-provider", "HEALTHY", 35, true, sha("slo-2"), Instant.now().minusSeconds(2)));
        providerHealth.record(new Stage13ProviderHealthEvidenceService.HealthEvidenceRequest(
                "stage14-slo-provider", "UNAVAILABLE", 0, true, sha("slo-3"), Instant.now()));

        var result = slo.assessProvider("stage14-slo-provider");
        assertThat(result.status()).isEqualTo("INCIDENT_CANDIDATE");
        assertThat(result.incidentId()).isNotBlank();
        assertThat(result.providerDisabled()).isFalse();
        assertThat(result.externalActionAttempted()).isFalse();
        assertThat(operations.incident(UUID.fromString(result.incidentId())).getSeverity()).isEqualTo("CRITICAL");
    }

    @Test
    void duplicateFingerprintCorrelatesIntoSingleActiveIncident() {
        String fingerprint = sha("same-failure");
        var one = operations.record(new Stage14OperationalIntelligenceService.SignalRequest(
                "SERVICE", "gateway", "LATENCY_SPIKE", "WARN", fingerprint, "Latency exceeded the evidence threshold",
                true, sha("latency-evidence-1"), Instant.now().minusSeconds(1)));
        var two = operations.record(new Stage14OperationalIntelligenceService.SignalRequest(
                "SERVICE", "gateway", "LATENCY_SPIKE", "CRITICAL", fingerprint, "Latency escalated beyond the critical threshold",
                true, sha("latency-evidence-2"), Instant.now()));
        assertThat(two.incident().getId()).isEqualTo(one.incident().getId());
        assertThat(two.incident().getSignalCount()).isEqualTo(2);
        assertThat(two.incident().getSeverity()).isEqualTo("CRITICAL");
    }

    @Test
    void containmentAndSelfHealingRequireExactApprovalAndNeverExecuteRemediation() {
        var incident = criticalIncident("containment");
        var task = planningTask("Stage 14 containment");

        var blocked = containment.evaluate(new Stage14ContainmentService.ContainmentRequest(
                incident.getId(), task.getId(), null, "PROVIDER_DISABLE"));
        assertThat(blocked.status()).isEqualTo("APPROVAL_OR_POLICY_REQUIRED");
        assertThat(blocked.remediationExecuted()).isFalse();
        assertThat(blocked.externalActionAttempted()).isFalse();

        var approval = approve(task.getId(), Stage14ContainmentService.APPROVAL_ACTION);
        var authorized = containment.evaluate(new Stage14ContainmentService.ContainmentRequest(
                incident.getId(), task.getId(), approval.getId(), "PROVIDER_DISABLE"));
        assertThat(authorized.status()).isEqualTo("AUTHORIZED_NOT_EXECUTED");
        assertThat(authorized.ownerApproved()).isTrue();
        assertThat(authorized.remediationExecuted()).isFalse();

        var healTask = planningTask("Stage 14 self heal");
        var proposal = healing.propose(new Stage14SelfHealingProposalService.ProposalRequest(
                incident.getId(), healTask.getId(), "RESTART_SERVICE", "gateway", "Restart is a bounded recovery proposal"));
        assertThat(proposal.getStatus()).isEqualTo("PROPOSED_AWAITING_APPROVAL");
        assertThat(proposal.isExecuted()).isFalse();
        assertThatThrownBy(() -> healing.propose(new Stage14SelfHealingProposalService.ProposalRequest(
                incident.getId(), healTask.getId(), "ARBITRARY_SHELL", "gateway", "must be rejected")))
                .isInstanceOf(IllegalArgumentException.class);
        var healApproval = approve(healTask.getId(), Stage14SelfHealingProposalService.APPROVAL_ACTION);
        var approvedProposal = healing.authorize(proposal.getId(), healApproval.getId());
        assertThat(approvedProposal.getStatus()).isEqualTo("APPROVED_NOT_EXECUTED");
        assertThat(approvedProposal.isExecuted()).isFalse();
    }

    @Test
    void changeImpactAndDigitalTwinRehearsalAreDeterministicAndSimulationOnly() {
        var request = new Stage14ChangeImpactService.ChangeImpactRequest(
                Set.of("db"), Map.of("api", Set.of("db"), "gateway", Set.of("api")), Set.of("gateway"));
        var result = impact.assess(request);
        assertThat(result.status()).isEqualTo("ANALYZED");
        assertThat(result.affectedComponents()).containsExactlyInAnyOrder("db", "api", "gateway");
        assertThat(result.criticalAffected()).containsExactly("gateway");
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.externalActionAttempted()).isFalse();

        var dryRun = rehearsal.rehearse(new Stage14DeploymentRehearsalService.RehearsalRequest(
                request, true, true, sha("rehearsal-proof")));
        assertThat(dryRun.status()).isEqualTo("REHEARSAL_PASS");
        assertThat(dryRun.simulationOnly()).isTrue();
        assertThat(dryRun.productionPromoted()).isFalse();
        assertThat(dryRun.externalActionAttempted()).isFalse();
    }

    @Test
    void escalationAndPostIncidentReportStayEvidenceHonest() {
        registerProvider("stage14-notify", "NOTIFICATION", Set.of("SEND_NOTIFICATION"), false);
        var incident = operations.record(new Stage14OperationalIntelligenceService.SignalRequest(
                "SECURITY", "remote-session", "REVOCATION_ANOMALY", "WARN", sha("revocation-anomaly"),
                "A remote revocation anomaly needs owner attention", true, sha("revocation-evidence"), Instant.now())).incident();

        var decision = escalation.evaluate(incident.getId(), "stage14-notify");
        assertThat(decision.status()).isEqualTo("ESCALATION_READY_NOT_SENT");
        assertThat(decision.notificationSent()).isFalse();
        assertThat(decision.externalActionAttempted()).isFalse();

        var report = reports.build(incident.getId());
        assertThat(report.reportStatus()).isEqualTo("DRAFT_INCIDENT_REPORT");
        assertThat(report.rootCause()).isEqualTo("UNDETERMINED_FROM_AVAILABLE_EVIDENCE");
        assertThat(report.externalActionAttempted()).isFalse();
    }

    private Stage14IncidentEntity criticalIncident(String seed) {
        return operations.record(new Stage14OperationalIntelligenceService.SignalRequest(
                "SERVICE", "service-" + seed, "SERVICE_FAILURE", "CRITICAL", sha("fingerprint-" + seed),
                "Critical service failure fixture", true, sha("evidence-" + seed), Instant.now())).incident();
    }

    private void registerProvider(String id, String type, Set<String> capabilities, boolean readOnly) {
        providers.register(new Stage12ProviderRegistryService.ProviderRegistration(id, type,
                "https://" + id + ".example.test", id + "-alias", true, capabilities, readOnly));
        providers.recordHealth(id, new Stage12ProviderRegistryService.ProviderHealthEvidence(
                "HEALTHY", 25, true, sha(id + "-health"), Instant.now()));
    }

    private TaskEntity planningTask(String title) {
        TaskEntity task = tasks.create(new CreateTaskRequest(title, "Stage 14 governed operations fixture", OperationMode.PRIVATE));
        return tasks.transition(task.getId(), new TaskTransitionRequest(TaskState.PLANNING, "incident-commander", "Prepare Stage 14 owner-controlled operation"));
    }

    private ApprovalEntity approve(UUID taskId, String action) {
        var approval = approvals.request(new CreateApprovalRequest(taskId, action, "Owner approval fixture for " + action, RiskLevel.HIGH));
        return approvals.decide(approval.getId(), new ApprovalDecisionRequest(true, "Approved for Stage 14 test fixture"));
    }

    private String sha(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
