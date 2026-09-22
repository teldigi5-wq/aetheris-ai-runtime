package io.aetheris.orchestrator;

import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.approval.*;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.stage14.*;
import io.aetheris.orchestrator.stage15.*;
import io.aetheris.orchestrator.stage16.*;
import io.aetheris.orchestrator.task.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class Stage16IntegrationTest {
    @Autowired Stage16AdapterRegistryService adapters;
    @Autowired Stage16CommandTrustService trust;
    @Autowired Stage16SecureRecoveryService secureRecovery;
    @Autowired Stage15ServiceCatalogService catalog;
    @Autowired Stage15ReliabilityService reliability;
    @Autowired Stage15RecoveryService stage15Recovery;
    @Autowired Stage14OperationalIntelligenceService operations;
    @Autowired TaskService tasks;
    @Autowired ApprovalService approvals;

    @Test
    void adapterRegistryRejectsRealTargetsAndForbiddenAuthority() {
        var adapter = adapters.register(new Stage16AdapterRegistryService.AdapterRegistration(
                "stage16-sim-windows", "Simulated Windows", "SIMULATED_WINDOWS",
                Set.of("RESTART_SERVICE", "ROLLBACK_RELEASE"), true, "CI_SIMULATED",
                sha("stage16-adapter-attestation"), Instant.now()));
        assertThat(adapter.isSimulationOnly()).isTrue();
        assertThat(adapter.getCapabilities()).contains("RESTART_SERVICE");

        assertThatThrownBy(() -> adapters.register(new Stage16AdapterRegistryService.AdapterRegistration(
                "stage16-real-target", "Real Target", "TARGET_WINDOWS", Set.of("RESTART_SERVICE"),
                false, "TARGET_MEASURED", sha("real-target"), Instant.now())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapters.register(new Stage16AdapterRegistryService.AdapterRegistration(
                "stage16-shell", "Bad Adapter", "SIMULATED_WINDOWS", Set.of("ARBITRARY_SHELL"),
                true, "CI_SIMULATED", sha("shell-adapter"), Instant.now())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void signedEnvelopeIsShortLivedCapabilityBoundAndReplayProtected() throws Exception {
        Fixture fixture = fixture("stage16-envelope");
        KeyPair pair = keyPair();
        trust.registerSigner(new Stage16CommandTrustService.SignerRegistration(
                "stage16-envelope-key", "Envelope Test Key", Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())));
        adapters.register(new Stage16AdapterRegistryService.AdapterRegistration(
                "stage16-envelope-adapter", "Envelope Adapter", "SIMULATED_WINDOWS",
                Set.of("RESTART_SERVICE", "ROLLBACK_RELEASE"), true, "CI_SIMULATED",
                sha("envelope-adapter-attestation"), Instant.now()));

        Instant issued = Instant.now();
        Instant expires = issued.plusSeconds(120);
        String nonce = "stage16-envelope-nonce-0001";
        String canonical = Stage16SecureRecoveryService.canonical(fixture.plan().getId(), "stage16-envelope-adapter",
                "RESTART_SERVICE", fixture.serviceId(), nonce, issued, expires, fixture.plan().getPlanSha256());
        String signature = sign(pair.getPrivate(), canonical);
        var request = new Stage16SecureRecoveryService.SignedEnvelopeRequest(fixture.plan().getId(),
                "stage16-envelope-adapter", "RESTART_SERVICE", fixture.serviceId(), nonce, issued, expires,
                fixture.plan().getPlanSha256(), "stage16-envelope-key", signature);

        var admitted = secureRecovery.admit(request);
        assertThat(admitted.getStatus()).isEqualTo("ADMITTED");
        assertThat(admitted.isExternalActionAttempted()).isFalse();
        assertThatThrownBy(() -> secureRecovery.admit(request)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Replay detected");

        String wrongCanonical = Stage16SecureRecoveryService.canonical(fixture.plan().getId(), "stage16-envelope-adapter",
                "RESTART_SERVICE", fixture.serviceId(), "stage16-envelope-nonce-0002", issued, expires,
                fixture.plan().getPlanSha256());
        String badSignature = sign(pair.getPrivate(), wrongCanonical + "tampered");
        var bad = new Stage16SecureRecoveryService.SignedEnvelopeRequest(fixture.plan().getId(),
                "stage16-envelope-adapter", "RESTART_SERVICE", fixture.serviceId(), "stage16-envelope-nonce-0002",
                issued, expires, fixture.plan().getPlanSha256(), "stage16-envelope-key", badSignature);
        assertThatThrownBy(() -> secureRecovery.admit(bad)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature verification failed");
    }

    @Test
    void sandboxAutomaticallyVerifiesAndRollsBackWithoutTouchingTarget() throws Exception {
        Fixture fixture = fixture("stage16-rollback");
        KeyPair pair = keyPair();
        trust.registerSigner(new Stage16CommandTrustService.SignerRegistration(
                "stage16-rollback-key", "Rollback Test Key", Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())));
        adapters.register(new Stage16AdapterRegistryService.AdapterRegistration(
                "stage16-rollback-adapter", "Rollback Adapter", "SIMULATED_WINDOWS",
                Set.of("RESTART_SERVICE", "ROLLBACK_RELEASE"), true, "CI_SIMULATED",
                sha("rollback-adapter-attestation"), Instant.now()));
        var envelope = admit(fixture, pair, "stage16-rollback-key", "stage16-rollback-adapter",
                "stage16-rollback-nonce-0001");

        var result = secureRecovery.execute(envelope.getId(), new Stage16SecureRecoveryService.ExecuteRequest("VERIFY_FAILURE"));
        assertThat(result.outcome()).isEqualTo("SIMULATED_VERIFY_FAILED_ROLLED_BACK");
        assertThat(result.verificationAttempted()).isTrue();
        assertThat(result.verificationSucceeded()).isFalse();
        assertThat(result.rollbackAttempted()).isTrue();
        assertThat(result.rollbackSucceeded()).isTrue();
        assertThat(result.simulationOnly()).isTrue();
        assertThat(result.targetMutated()).isFalse();
        assertThat(result.externalActionAttempted()).isFalse();
        assertThat(secureRecovery.get(envelope.getId()).getStatus()).isEqualTo("SIMULATION_COMPLETED");
    }

    @Test
    void emergencyCancellationWinsBeforeSandboxExecution() throws Exception {
        Fixture fixture = fixture("stage16-cancel");
        KeyPair pair = keyPair();
        trust.registerSigner(new Stage16CommandTrustService.SignerRegistration(
                "stage16-cancel-key", "Cancel Test Key", Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())));
        adapters.register(new Stage16AdapterRegistryService.AdapterRegistration(
                "stage16-cancel-adapter", "Cancel Adapter", "SIMULATED_CONTROL",
                Set.of("RESTART_SERVICE", "ROLLBACK_RELEASE"), true, "CI_SIMULATED",
                sha("cancel-adapter-attestation"), Instant.now()));
        var envelope = admit(fixture, pair, "stage16-cancel-key", "stage16-cancel-adapter",
                "stage16-cancel-nonce-0001");

        var cancelled = secureRecovery.cancel(envelope.getId(), new Stage16SecureRecoveryService.CancelRequest("Owner STOP ALL"));
        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
        assertThat(cancelled.getCancellationReason()).isEqualTo("Owner STOP ALL");
        assertThatThrownBy(() -> secureRecovery.execute(envelope.getId(), new Stage16SecureRecoveryService.ExecuteRequest("NORMAL")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not executable");
    }

    @Test
    void exhaustedErrorBudgetBlocksSignedEnvelopeAdmission() throws Exception {
        String serviceId = "stage16-budget";
        registerService(serviceId);
        reliability.record(new Stage15ReliabilityService.EvidenceRequest(serviceId, 1000, 50, true, sha("stage16-budget-bad"), Instant.now()));
        var incident = criticalIncident(serviceId);
        var task = planningTask("Stage 16 exhausted budget");
        var plan = stage15Recovery.create(new Stage15RecoveryService.RecoveryPlanRequest(incident.getId(), task.getId(),
                serviceId, "RESTART_SERVICE", "ROLLBACK_RELEASE", "HEALTHY_ATTESTATION", sha("plan-" + serviceId)));
        var approval = approve(task.getId(), Stage15RecoveryService.APPROVAL_ACTION);
        stage15Recovery.authorize(plan.getId(), approval.getId());

        KeyPair pair = keyPair();
        trust.registerSigner(new Stage16CommandTrustService.SignerRegistration(
                "stage16-budget-key", "Budget Test Key", Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())));
        adapters.register(new Stage16AdapterRegistryService.AdapterRegistration(
                "stage16-budget-adapter", "Budget Adapter", "SIMULATED_WINDOWS",
                Set.of("RESTART_SERVICE", "ROLLBACK_RELEASE"), true, "CI_SIMULATED",
                sha("budget-adapter-attestation"), Instant.now()));
        Instant issued = Instant.now(); Instant expires = issued.plusSeconds(60); String nonce = "stage16-budget-nonce-0001";
        String canonical = Stage16SecureRecoveryService.canonical(plan.getId(), "stage16-budget-adapter", "RESTART_SERVICE",
                serviceId, nonce, issued, expires, plan.getPlanSha256());
        var request = new Stage16SecureRecoveryService.SignedEnvelopeRequest(plan.getId(), "stage16-budget-adapter",
                "RESTART_SERVICE", serviceId, nonce, issued, expires, plan.getPlanSha256(), "stage16-budget-key",
                sign(pair.getPrivate(), canonical));
        assertThatThrownBy(() -> secureRecovery.admit(request)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Error budget is exhausted");
    }

    private Fixture fixture(String serviceId) {
        registerService(serviceId);
        reliability.record(new Stage15ReliabilityService.EvidenceRequest(serviceId, 1000, 0, true, sha(serviceId + "-health-a"), Instant.now().minusSeconds(1)));
        reliability.record(new Stage15ReliabilityService.EvidenceRequest(serviceId, 1000, 0, true, sha(serviceId + "-health-b"), Instant.now()));
        var incident = criticalIncident(serviceId);
        var task = planningTask("Stage 16 recovery " + serviceId);
        var plan = stage15Recovery.create(new Stage15RecoveryService.RecoveryPlanRequest(incident.getId(), task.getId(),
                serviceId, "RESTART_SERVICE", "ROLLBACK_RELEASE", "HEALTHY_ATTESTATION", sha("plan-" + serviceId)));
        var approval = approve(task.getId(), Stage15RecoveryService.APPROVAL_ACTION);
        return new Fixture(serviceId, stage15Recovery.authorize(plan.getId(), approval.getId()));
    }

    private Stage16ExecutionEnvelopeEntity admit(Fixture fixture, KeyPair pair, String keyId, String adapterId, String nonce) throws Exception {
        Instant issued = Instant.now(); Instant expires = issued.plusSeconds(120);
        String canonical = Stage16SecureRecoveryService.canonical(fixture.plan().getId(), adapterId, "RESTART_SERVICE",
                fixture.serviceId(), nonce, issued, expires, fixture.plan().getPlanSha256());
        return secureRecovery.admit(new Stage16SecureRecoveryService.SignedEnvelopeRequest(fixture.plan().getId(), adapterId,
                "RESTART_SERVICE", fixture.serviceId(), nonce, issued, expires, fixture.plan().getPlanSha256(), keyId,
                sign(pair.getPrivate(), canonical)));
    }

    private void registerService(String id) {
        catalog.register(new Stage15ServiceCatalogService.ServiceRegistration(id, id, "SERVICE", "HIGH", 9900, 438,
                Set.of("RESTART_SERVICE", "ROLLBACK_RELEASE"), Set.of(), true));
    }
    private Stage14IncidentEntity criticalIncident(String serviceId) {
        return operations.record(new Stage14OperationalIntelligenceService.SignalRequest(
                "SERVICE", serviceId, "RECOVERY_REQUIRED", "CRITICAL", sha("fingerprint-" + serviceId),
                "Critical Stage 16 recovery fixture", true, sha("incident-evidence-" + serviceId), Instant.now())).incident();
    }
    private TaskEntity planningTask(String title) {
        TaskEntity task = tasks.create(new CreateTaskRequest(title, "Stage 16 secure recovery fixture", OperationMode.PRIVATE));
        return tasks.transition(task.getId(), new TaskTransitionRequest(TaskState.PLANNING, "incident-commander", "Prepare Stage 16 recovery envelope"));
    }
    private ApprovalEntity approve(UUID taskId, String action) {
        var approval = approvals.request(new CreateApprovalRequest(taskId, action, "Owner approval fixture for " + action, RiskLevel.HIGH));
        return approvals.decide(approval.getId(), new ApprovalDecisionRequest(true, "Approved for Stage 16 test fixture"));
    }
    private KeyPair keyPair() throws Exception { return KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); }
    private String sign(PrivateKey key, String payload) throws Exception {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(key); signature.update(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }
    private String sha(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private record Fixture(String serviceId, Stage15RecoveryPlanEntity plan) {}
}
