package io.aetheris.orchestrator;

import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.approval.*;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.stage14.*;
import io.aetheris.orchestrator.stage15.*;
import io.aetheris.orchestrator.stage16.*;
import io.aetheris.orchestrator.stage17.*;
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
class Stage17IntegrationTest {
    @Autowired Stage17AdapterManifestService manifests;
    @Autowired Stage17CapabilityPolicyService policies;
    @Autowired Stage17TransportLabService transport;
    @Autowired Stage17OfflineQueueService queue;
    @Autowired Stage17MeasuredReceiptService receipts;
    @Autowired Stage17CertificationService certifications;
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
    void signedManifestNegotiatesProtocolAndRejectsTampering() throws Exception {
        KeyPair pair = keyPair();
        String keyId = "stage17-manifest-key";
        String adapterId = "stage17-manifest-adapter";
        registerSigner(keyId, pair);
        registerAdapter(adapterId);
        var manifest = registerManifest(adapterId, keyId, pair);
        assertThat(manifest.getStatus()).isEqualTo("MANIFEST_VERIFIED");
        assertThat(manifest.isSimulationOnly()).isTrue();
        assertThat(manifests.negotiate(adapterId, 1, 2).selectedProtocolVersion()).isEqualTo(2);
        assertThat(manifests.negotiate(adapterId, 3, 4).status()).isEqualTo("INCOMPATIBLE");

        Set<String> caps = Set.of("RESTART_SERVICE");
        Set<String> transports = Set.of("SYNTHETIC_MTLS");
        String content = Stage17AdapterManifestService.contentCanonical(adapterId, "1.0.1", 1, 2, caps, transports,
                Stage17AdapterManifestService.RECEIPT_SCHEMA_V1);
        String manifestSha = Stage17AdapterManifestService.shaText(content);
        var bad = new Stage17AdapterManifestService.SignedManifestRequest(adapterId, "1.0.1", 1, 2, caps, transports,
                Stage17AdapterManifestService.RECEIPT_SCHEMA_V1, manifestSha, keyId,
                sign(pair.getPrivate(), Stage17AdapterManifestService.signedCanonical(content + "tampered", manifestSha)));
        assertThatThrownBy(() -> manifests.register(bad)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature verification failed");
    }

    @Test
    void policyAndSyntheticMutualTlsStayCapabilityBoundAndOffline() throws Exception {
        KeyPair pair = keyPair();
        String keyId = "stage17-policy-key";
        String adapterId = "stage17-policy-adapter";
        registerSigner(keyId, pair); registerAdapter(adapterId); registerManifest(adapterId, keyId, pair);
        var policy = policies.compile(adapterId, Set.of("RESTART_SERVICE"));
        assertThat(policy.status()).isEqualTo("POLICY_COMPILED");
        assertThat(policy.productionActivationAllowed()).isFalse();
        assertThatThrownBy(() -> policies.compile(adapterId, Set.of("LIVE_ORDER")))
                .isInstanceOf(IllegalArgumentException.class);

        var request = transportRequest(adapterId, 2);
        var result = transport.rehearse(request);
        assertThat(result.status()).isEqualTo("SYNTHETIC_MTLS_PASS");
        assertThat(result.networkConnectionAttempted()).isFalse();
        assertThat(result.productionTransportActivated()).isFalse();
        var badTls = new Stage17TransportLabService.TransportRequest(adapterId, 2, "TLS1.2", true, true, true, true,
                sha("stage17-policy-client"), sha("stage17-policy-server"));
        assertThatThrownBy(() -> transport.rehearse(badTls)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TLS1.3");
    }

    @Test
    void offlineQueueHonorsReconnectCancellationAndRevocationWithoutExecution() throws Exception {
        KeyPair pair = keyPair();
        String keyId = "stage17-queue-key";
        String adapterId = "stage17-queue-adapter";
        registerSigner(keyId, pair); registerAdapter(adapterId); registerManifest(adapterId, keyId, pair);

        Fixture delivery = fixture("stage17-queue-delivery");
        var deliveryEnvelope = admit(delivery, pair, keyId, adapterId, "stage17-queue-delivery-nonce-01");
        var deliveryItem = queue.enqueue(deliveryEnvelope.getId());
        assertThat(deliveryItem.getState()).isEqualTo("QUEUED_OFFLINE");
        assertThat(queue.reconnect(adapterId, false).status()).isEqualTo("TRANSPORT_UNAVAILABLE");
        assertThat(queue.reconnect(adapterId, true).delivered()).isEqualTo(1);
        assertThat(queue.get(deliveryItem.getId()).getState()).isEqualTo("DELIVERED_SIMULATION");
        assertThat(queue.get(deliveryItem.getId()).isExternalActionAttempted()).isFalse();

        Fixture cancelled = fixture("stage17-queue-cancel");
        var cancelledEnvelope = admit(cancelled, pair, keyId, adapterId, "stage17-queue-cancel-nonce-0001");
        var cancelledItem = queue.enqueue(cancelledEnvelope.getId());
        secureRecovery.cancel(cancelledEnvelope.getId(), new Stage16SecureRecoveryService.CancelRequest("owner stop fixture"));
        assertThat(queue.reconnect(adapterId, true).cancelled()).isGreaterThanOrEqualTo(1);
        assertThat(queue.get(cancelledItem.getId()).getState()).isEqualTo("CANCELLED");

        Fixture revoked = fixture("stage17-queue-revoke");
        var revokedEnvelope = admit(revoked, pair, keyId, adapterId, "stage17-queue-revoke-nonce-0001");
        var revokedItem = queue.enqueue(revokedEnvelope.getId());
        assertThat(queue.revokePending(adapterId, "adapter revocation fixture")).isGreaterThanOrEqualTo(1);
        assertThat(queue.get(revokedItem.getId()).getState()).isEqualTo("REVOKED");
        assertThat(queue.conformance(adapterId).status()).isEqualTo("CONFORMANT");
    }

    @Test
    void measuredReceiptAndCertificationRemainSimulationOnly() throws Exception {
        KeyPair pair = keyPair();
        String keyId = "stage17-cert-key";
        String adapterId = "stage17-cert-adapter";
        registerSigner(keyId, pair); registerAdapter(adapterId); registerManifest(adapterId, keyId, pair);

        Fixture delivered = fixture("stage17-cert-delivery");
        var envelope = admit(delivered, pair, keyId, adapterId, "stage17-cert-delivery-nonce-01");
        queue.enqueue(envelope.getId());
        queue.reconnect(adapterId, true);
        var execution = secureRecovery.execute(envelope.getId(), new Stage16SecureRecoveryService.ExecuteRequest("NORMAL"));
        var receipt = receipts.record(new Stage17MeasuredReceiptService.ReceiptRequest(envelope.getId(),
                Stage17AdapterManifestService.RECEIPT_SCHEMA_V1, execution.outcome(), execution.receiptSha256(),
                true, true, false, false, sha("stage17-cert-receipt-attestation"), Instant.now()));
        assertThat(receipt.isSimulationOnly()).isTrue();
        assertThat(receipt.isTargetMutated()).isFalse();

        Fixture cancelled = fixture("stage17-cert-cancel");
        var cancelEnvelope = admit(cancelled, pair, keyId, adapterId, "stage17-cert-cancel-nonce-0001");
        queue.enqueue(cancelEnvelope.getId());
        secureRecovery.cancel(cancelEnvelope.getId(), new Stage16SecureRecoveryService.CancelRequest("cert cancellation fixture"));
        queue.reconnect(adapterId, true);
        assertThat(queue.conformance(adapterId).status()).isEqualTo("CONFORMANT");

        var certification = certifications.certify(new Stage17CertificationService.CertificationRequest(adapterId,
                Set.of("RESTART_SERVICE"), 1, 2, transportRequest(adapterId, 2), receipt.getId()));
        assertThat(certification.getStatus()).isEqualTo("CERTIFIED_SIMULATION_ONLY");
        assertThat(certification.getScore()).isEqualTo(100);
        assertThat(certification.isProductionActivationAllowed()).isFalse();
        assertThat(certification.isExternalActionAttempted()).isFalse();
        var matrix = certifications.compatibilityMatrix();
        assertThat(matrix.productionActivationAllowed()).isFalse();
        assertThat(matrix.adapters()).anyMatch(r -> r.adapterId().equals(adapterId)
                && r.certificationStatus().equals("CERTIFIED_SIMULATION_ONLY") && r.certificationScore() == 100);
    }

    private void registerSigner(String keyId, KeyPair pair) {
        trust.registerSigner(new Stage16CommandTrustService.SignerRegistration(keyId, keyId,
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())));
    }
    private void registerAdapter(String adapterId) {
        adapters.register(new Stage16AdapterRegistryService.AdapterRegistration(adapterId, adapterId,
                "SIMULATED_WINDOWS", Set.of("RESTART_SERVICE", "ROLLBACK_RELEASE"), true, "CI_SIMULATED",
                sha(adapterId + "-adapter-attestation"), Instant.now()));
    }
    private Stage17AdapterManifestEntity registerManifest(String adapterId, String keyId, KeyPair pair) throws Exception {
        Set<String> caps = Set.of("RESTART_SERVICE", "ROLLBACK_RELEASE");
        Set<String> transports = Set.of("SYNTHETIC_MTLS", "OFFLINE_QUEUE", "LOOPBACK_SIMULATION");
        String content = Stage17AdapterManifestService.contentCanonical(adapterId, "1.0.0", 1, 2, caps, transports,
                Stage17AdapterManifestService.RECEIPT_SCHEMA_V1);
        String manifestSha = Stage17AdapterManifestService.shaText(content);
        String signed = Stage17AdapterManifestService.signedCanonical(content, manifestSha);
        return manifests.register(new Stage17AdapterManifestService.SignedManifestRequest(adapterId, "1.0.0", 1, 2,
                caps, transports, Stage17AdapterManifestService.RECEIPT_SCHEMA_V1, manifestSha, keyId,
                sign(pair.getPrivate(), signed)));
    }
    private Stage17TransportLabService.TransportRequest transportRequest(String adapterId, int protocol) {
        return new Stage17TransportLabService.TransportRequest(adapterId, protocol, "TLS1.3", true, true, true, true,
                sha(adapterId + "-client-cert"), sha(adapterId + "-server-cert"));
    }

    private Fixture fixture(String serviceId) {
        catalog.register(new Stage15ServiceCatalogService.ServiceRegistration(serviceId, serviceId, "SERVICE", "HIGH",
                9900, 438, Set.of("RESTART_SERVICE", "ROLLBACK_RELEASE"), Set.of(), true));
        reliability.record(new Stage15ReliabilityService.EvidenceRequest(serviceId, 1000, 0, true,
                sha(serviceId + "-health-a"), Instant.now().minusSeconds(1)));
        reliability.record(new Stage15ReliabilityService.EvidenceRequest(serviceId, 1000, 0, true,
                sha(serviceId + "-health-b"), Instant.now()));
        var incident = operations.record(new Stage14OperationalIntelligenceService.SignalRequest(
                "SERVICE", serviceId, "RECOVERY_REQUIRED", "CRITICAL", sha("fingerprint-" + serviceId),
                "Critical Stage 17 fixture", true, sha("incident-evidence-" + serviceId), Instant.now())).incident();
        TaskEntity task = tasks.create(new CreateTaskRequest("Stage 17 " + serviceId, "Adapter certification fixture", OperationMode.PRIVATE));
        task = tasks.transition(task.getId(), new TaskTransitionRequest(TaskState.PLANNING, "incident-commander", "Prepare Stage 17 fixture"));
        var plan = stage15Recovery.create(new Stage15RecoveryService.RecoveryPlanRequest(incident.getId(), task.getId(),
                serviceId, "RESTART_SERVICE", "ROLLBACK_RELEASE", "HEALTHY_ATTESTATION", sha("plan-" + serviceId)));
        var approval = approvals.request(new CreateApprovalRequest(task.getId(), Stage15RecoveryService.APPROVAL_ACTION,
                "Stage 17 certification fixture", RiskLevel.HIGH));
        approval = approvals.decide(approval.getId(), new ApprovalDecisionRequest(true, "Approved Stage 17 fixture"));
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

    private KeyPair keyPair() throws Exception { return KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); }
    private String sign(PrivateKey key, String payload) throws Exception {
        Signature signature = Signature.getInstance("Ed25519"); signature.initSign(key);
        signature.update(payload.getBytes(StandardCharsets.UTF_8)); return Base64.getEncoder().encodeToString(signature.sign());
    }
    private String sha(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private record Fixture(String serviceId, Stage15RecoveryPlanEntity plan) {}
}
