package io.aetheris.orchestrator;

import io.aetheris.orchestrator.stage16.*;
import io.aetheris.orchestrator.stage17.*;
import io.aetheris.orchestrator.stage18.*;
import io.aetheris.orchestrator.stage19.*;
import io.aetheris.orchestrator.stage20.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class Stage20IntegrationTest {
    @Autowired Stage20HardwareHandoffService handoff;
    @Autowired Stage19ActivationService stage19;
    @Autowired Stage18ProvisioningService stage18;
    @Autowired Stage16CommandTrustService trust;
    @Autowired Stage16AdapterRegistryService adapters;
    @Autowired Stage17AdapterCertificationRepository certifications;

    @Test
    void ownerAuthorizationAndDeviceChallengeRequireSeparateValidSigners() throws Exception {
        Fixture f = fixture("stage20-trust");
        assertThat(f.authorization().getStatus()).isEqualTo("AUTHORIZED_SIMULATION_ONLY");
        assertThat(f.authorization().isProductionActivationAllowed()).isFalse();
        assertThat(f.authorization().isTargetMutated()).isFalse();

        var challenge = handoff.issueChallenge(f.authorization().getId());
        assertThat(challenge.nonceBase64()).isNotBlank();
        Instant observed = Instant.now();
        String canonical = Stage20HardwareHandoffService.deviceAttestationCanonical(challenge.challengeId(),
                f.authorization().getId(), f.targetId(), challenge.nonceSha256(), f.adapterId(),
                f.packageSha(), f.certSha(), observed);
        var bad = new Stage20HardwareHandoffService.DeviceAttestationRequest(f.adapterId(), f.packageSha(), f.certSha(),
                observed, sign(f.ownerPair().getPrivate(), canonical));
        assertThatThrownBy(() -> handoff.attest(challenge.challengeId(), bad))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("signature verification failed");

        var verified = handoff.attest(challenge.challengeId(), new Stage20HardwareHandoffService.DeviceAttestationRequest(
                f.adapterId(), f.packageSha(), f.certSha(), observed, sign(f.devicePair().getPrivate(), canonical)));
        assertThat(verified.getStatus()).isEqualTo("DEVICE_ATTESTATION_VERIFIED_SIMULATION_ONLY");
        assertThat(verified.getAttestationSha256()).matches("[a-f0-9]{64}");
    }

    @Test
    void leaseCannotWidenCapabilitiesAndChallengeCannotReplay() throws Exception {
        Fixture f = fixture("stage20-lease");
        var challenge = verifiedChallenge(f);
        assertThatThrownBy(() -> handoff.startLease(f.authorization().getId(),
                new Stage20HardwareHandoffService.LeaseRequest(challenge.getId(), Set.of("OLLAMA"), 2)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot widen");

        var lease = handoff.startLease(f.authorization().getId(),
                new Stage20HardwareHandoffService.LeaseRequest(challenge.getId(), Set.of("PC_TELEMETRY"), 2));
        assertThat(lease.getStatus()).isEqualTo("LEASE_ACTIVE_SIMULATION_ONLY");
        assertThat(lease.isProductionActivationAllowed()).isFalse();
        assertThatThrownBy(() -> handoff.renewLease(lease.getId(),
                new Stage20HardwareHandoffService.LeaseRenewalRequest(challenge.getId(), 1)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already consumed");

        var renewalChallenge = verifiedChallenge(f);
        var renewed = handoff.renewLease(lease.getId(),
                new Stage20HardwareHandoffService.LeaseRenewalRequest(renewalChallenge.getId(), 1));
        assertThat(renewed.getStatus()).isEqualTo("LEASE_RENEWED_SIMULATION_ONLY");
        assertThat(renewed.getRenewalCount()).isEqualTo(1);
        assertThat(renewed.getLastRenewalSha256()).matches("[a-f0-9]{64}");
    }

    @Test
    void emergencyStopRevokesLeaseAndOwnerSignatureIsRequiredToClear() throws Exception {
        Fixture f = fixture("stage20-stop");
        var challenge = verifiedChallenge(f);
        var lease = handoff.startLease(f.authorization().getId(),
                new Stage20HardwareHandoffService.LeaseRequest(challenge.getId(), Set.of("PC_TELEMETRY"), 2));
        var stopped = handoff.engageEmergencyStop(f.authorization().getId(),
                new Stage20HardwareHandoffService.ReasonRequest("owner requested immediate stop"));
        assertThat(stopped.getStatus()).isEqualTo("EMERGENCY_STOPPED_SIMULATION");
        assertThat(handoff.getLease(lease.getId()).getStatus()).isEqualTo("LEASE_REVOKED_SIMULATION_ONLY");
        assertThatThrownBy(() -> handoff.issueChallenge(f.authorization().getId()))
                .isInstanceOf(IllegalStateException.class);

        Instant observed = Instant.now();
        String canonical = Stage20HardwareHandoffService.clearInterlockCanonical(f.authorization().getId(),
                f.authorization().getAuthorizationSha256(), observed);
        assertThatThrownBy(() -> handoff.clearEmergencyStop(f.authorization().getId(),
                new Stage20HardwareHandoffService.ClearInterlockRequest("OWNER", observed,
                        sign(f.devicePair().getPrivate(), canonical))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("signature verification failed");
        var cleared = handoff.clearEmergencyStop(f.authorization().getId(),
                new Stage20HardwareHandoffService.ClearInterlockRequest("OWNER", observed,
                        sign(f.ownerPair().getPrivate(), canonical)));
        assertThat(cleared.getStatus()).isEqualTo("AUTHORIZED_SIMULATION_ONLY");
        assertThat(cleared.isEmergencyStopEngaged()).isFalse();
    }

    @Test
    void attestationDriftReceiptAutomaticallyRevokesAuthorizationAndLease() throws Exception {
        Fixture f = fixture("stage20-drift");
        var challenge = verifiedChallenge(f);
        var lease = handoff.startLease(f.authorization().getId(),
                new Stage20HardwareHandoffService.LeaseRequest(challenge.getId(), Set.of("PC_TELEMETRY"), 2));
        Instant observed = Instant.now();
        String wrongCert = sha("wrong-device-cert");
        String canonical = Stage20HardwareHandoffService.targetReceiptCanonical(lease.getId(), f.authorization().getId(),
                f.targetId(), lease.getLeaseSha256(), f.packageSha(), wrongCert, lease.getLastAttestationSha256(),
                "SUCCESS", false, observed);
        var receipt = handoff.recordReceipt(lease.getId(), new Stage20HardwareHandoffService.TargetReceiptRequest(
                f.packageSha(), wrongCert, lease.getLastAttestationSha256(), "SUCCESS", false,
                false, false, observed, sign(f.devicePair().getPrivate(), canonical)));
        assertThat(receipt.getStatus()).isEqualTo("REJECTED_ATTESTATION_DRIFT_SIMULATION");
        assertThat(handoff.getAuthorization(f.authorization().getId()).getStatus())
                .isEqualTo("REVOKED_ATTESTATION_DRIFT_SIMULATION");
        assertThat(handoff.getLease(lease.getId()).getStatus()).isEqualTo("LEASE_REVOKED_SIMULATION_ONLY");
        assertThat(handoff.audit(f.authorization().getId())).anyMatch(a -> "ATTESTATION_DRIFT_AUTO_REVOKE".equals(a.getEventType()));
    }

    @Test
    void verifiedReceiptAndPostActivationBundleRemainSimulationOnly() throws Exception {
        Fixture f = fixture("stage20-bundle");
        var challenge = verifiedChallenge(f);
        var lease = handoff.startLease(f.authorization().getId(),
                new Stage20HardwareHandoffService.LeaseRequest(challenge.getId(), Set.of("PC_TELEMETRY", "STT"), 2));
        Instant observed = Instant.now();
        String canonical = Stage20HardwareHandoffService.targetReceiptCanonical(lease.getId(), f.authorization().getId(),
                f.targetId(), lease.getLeaseSha256(), f.packageSha(), f.certSha(), lease.getLastAttestationSha256(),
                "HEALTHY", false, observed);
        var receipt = handoff.recordReceipt(lease.getId(), new Stage20HardwareHandoffService.TargetReceiptRequest(
                f.packageSha(), f.certSha(), lease.getLastAttestationSha256(), "HEALTHY", false,
                false, false, observed, sign(f.devicePair().getPrivate(), canonical)));
        assertThat(receipt.getStatus()).isEqualTo("VERIFIED_SIMULATION_ONLY");
        assertThat(receipt.isTargetMutated()).isFalse();
        assertThat(receipt.isExternalActionAttempted()).isFalse();

        var bundle = handoff.postActivationBundle(f.authorization().getId());
        assertThat(bundle.status()).isEqualTo("POST_ACTIVATION_EVIDENCE_SIMULATION_ONLY");
        assertThat(bundle.bundleSha256()).matches("[a-f0-9]{64}");
        assertThat(bundle.productionActivationAllowed()).isFalse();
        assertThat(bundle.targetMutated()).isFalse();
        assertThat(bundle.externalActionAttempted()).isFalse();

        assertThatThrownBy(() -> handoff.recordReceipt(lease.getId(), new Stage20HardwareHandoffService.TargetReceiptRequest(
                f.packageSha(), f.certSha(), lease.getLastAttestationSha256(), "SUCCESS", false,
                true, false, Instant.now(), "AA==")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("refuses receipts that claim target mutation");
    }

    private Stage20DeviceChallengeEntity verifiedChallenge(Fixture f) throws Exception {
        var issue = handoff.issueChallenge(f.authorization().getId());
        Instant observed = Instant.now();
        String canonical = Stage20HardwareHandoffService.deviceAttestationCanonical(issue.challengeId(),
                f.authorization().getId(), f.targetId(), issue.nonceSha256(), f.adapterId(),
                f.packageSha(), f.certSha(), observed);
        return handoff.attest(issue.challengeId(), new Stage20HardwareHandoffService.DeviceAttestationRequest(
                f.adapterId(), f.packageSha(), f.certSha(), observed, sign(f.devicePair().getPrivate(), canonical)));
    }

    private Fixture fixture(String prefix) throws Exception {
        KeyPair owner = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair device = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String ownerKey = prefix + "-owner-key";
        String deviceKey = prefix + "-device-key";
        String adapterId = prefix + "-adapter";
        String targetId = prefix + "-target";
        trust.registerSigner(new Stage16CommandTrustService.SignerRegistration(ownerKey, prefix + " owner",
                Base64.getEncoder().encodeToString(owner.getPublic().getEncoded())));
        trust.registerSigner(new Stage16CommandTrustService.SignerRegistration(deviceKey, prefix + " device",
                Base64.getEncoder().encodeToString(device.getPublic().getEncoded())));
        adapters.register(new Stage16AdapterRegistryService.AdapterRegistration(adapterId, prefix,
                "SIMULATED_WINDOWS", Set.of("RESTART_SERVICE", "ROLLBACK_RELEASE"), true, "CI_SIMULATED",
                sha(prefix + "-adapter-attestation"), Instant.now()));
        certifications.save(new Stage17AdapterCertificationEntity(UUID.randomUUID(), adapterId, UUID.randomUUID(), 2,
                sha(prefix + "-policy"), sha(prefix + "-transport"), UUID.randomUUID(), 100,
                Instant.now().plus(Duration.ofDays(7))));

        Instant issued = Instant.now();
        Set<String> targetCaps = Set.of("PROCESS_READ", "PC_TELEMETRY", "OLLAMA", "DPAPI", "VAD", "STT", "TTS",
                "PRIVATE_TRANSPORT", "APP_LAUNCH", "FILE_OPEN");
        String packageSha = sha("package-" + targetId);
        String certSha = sha("cert-" + targetId);
        String manifestCanonical = Stage18ProvisioningService.canonical(targetId, adapterId, "WINDOWS", packageSha, certSha,
                16384, 6144, targetCaps, Set.of(), issued);
        stage18.register(new Stage18ProvisioningService.SignedBootstrapManifestRequest(targetId, adapterId, "WINDOWS",
                packageSha, certSha, 16384, 6144, targetCaps, Set.of(), issued, ownerKey,
                Stage18ProvisioningService.shaText(manifestCanonical), sign(owner.getPrivate(), manifestCanonical)));
        recordGeneric(targetId, "DEVICE_IDENTITY", certSha);
        recordGeneric(targetId, "PACKAGE_INTEGRITY", packageSha);
        recordGeneric(targetId, "OS_VAULT", null);
        recordGeneric(targetId, "PRIVATE_TRANSPORT", certSha);
        stage18.recordBenchmark(new Stage18ProvisioningService.BenchmarkRequest(targetId, "RESOURCE", true,
                32768, 8192, 0, 0, 0, 0, sha(prefix + "-resource"), "target-agent", Instant.now()));
        stage18.recordBenchmark(new Stage18ProvisioningService.BenchmarkRequest(targetId, "SPEECH", true,
                0, 0, 450, 100, 0, 0, sha(prefix + "-speech"), "target-agent", Instant.now()));
        stage18.recordBenchmark(new Stage18ProvisioningService.BenchmarkRequest(targetId, "LOCAL_MODEL", true,
                0, 0, 800, 0, 24, 0, sha(prefix + "-model"), "target-agent", Instant.now()));

        var proposal = stage19.propose(new Stage19ActivationService.ProposalRequest(targetId,
                stage18.bundle(targetId).bundleSha256(), Set.of("PC_TELEMETRY", "STT")));
        stage19.approve(proposal.getId(), new Stage19ActivationService.ApprovalRequest("OWNER", 10));
        stage19.startCanary(proposal.getId());
        for (int i = 0; i < 3; i++) stage19.observe(proposal.getId(), new Stage19ActivationService.ObservationRequest(
                120, 99.99, 0.05, 200, 0, false, "ci-stage20-canary",
                sha(prefix + "-canary-" + i), Instant.now()));
        stage19.validateCanary(proposal.getId());
        stage19.refreshAttestation(proposal.getId());
        proposal = stage19.get(proposal.getId());

        Instant authIssued = Instant.now();
        Instant maintenanceStart = authIssued.minusSeconds(30);
        Instant maintenanceEnd = authIssued.plus(Duration.ofMinutes(15));
        Instant authExpires = authIssued.plus(Duration.ofMinutes(10));
        Set<String> authCaps = Set.of("PC_TELEMETRY", "STT");
        String authCanonical = Stage20HardwareHandoffService.authorizationCanonical(proposal.getId(),
                proposal.getProposalSha256(), targetId, adapterId, proposal.getManifestSha256(),
                stage18.bundle(targetId).bundleSha256(), packageSha, certSha, proposal.getRefreshedAttestationSha256(),
                ownerKey, deviceKey, authCaps, authIssued, authExpires, maintenanceStart, maintenanceEnd, 5);
        String authSha = sha(authCanonical);
        var authorization = handoff.authorize(new Stage20HardwareHandoffService.SignedAuthorizationRequest(
                proposal.getId(), ownerKey, deviceKey, authCaps, authIssued, authExpires, maintenanceStart, maintenanceEnd,
                5, authSha, sign(owner.getPrivate(), authCanonical)));
        return new Fixture(targetId, adapterId, packageSha, certSha, owner, device, authorization);
    }

    private void recordGeneric(String targetId, String kind, String subjectSha) {
        stage18.recordEvidence(new Stage18ProvisioningService.TargetEvidenceRequest(targetId, kind, "system", true,
                true, subjectSha, sha(targetId + "-" + kind), "target-agent", "fresh target evidence for " + kind,
                Instant.now()));
    }
    private String sign(PrivateKey key, String payload) throws Exception {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(key); signature.update(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }
    private String sha(String value) { return Stage18ProvisioningService.shaText(value); }

    private record Fixture(String targetId, String adapterId, String packageSha, String certSha,
                           KeyPair ownerPair, KeyPair devicePair, Stage20ActivationAuthorizationEntity authorization) {}
}
