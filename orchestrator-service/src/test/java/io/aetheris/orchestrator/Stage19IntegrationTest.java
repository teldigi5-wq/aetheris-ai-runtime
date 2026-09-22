package io.aetheris.orchestrator;

import io.aetheris.orchestrator.stage16.*;
import io.aetheris.orchestrator.stage17.*;
import io.aetheris.orchestrator.stage18.*;
import io.aetheris.orchestrator.stage19.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class Stage19IntegrationTest {
    @Autowired Stage19ActivationService activation;
    @Autowired Stage18ProvisioningService stage18;
    @Autowired Stage16CommandTrustService trust;
    @Autowired Stage16AdapterRegistryService adapters;
    @Autowired Stage17AdapterCertificationRepository certifications;

    @Test
    void exactBundleAndOwnerApprovalAreMandatory() throws Exception {
        ReadyFixture f = readyFixture("stage19-owner");
        var bundle = stage18.bundle(f.targetId());
        assertThat(stage18.readiness(f.targetId()).status()).isEqualTo("READY_FOR_OWNER_ACTIVATION_REVIEW");
        assertThat(stage18.readiness(f.targetId()).score()).isEqualTo(100);

        assertThatThrownBy(() -> activation.propose(new Stage19ActivationService.ProposalRequest(
                f.targetId(), sha("wrong-bundle"), Set.of("PC_TELEMETRY"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("current exact Stage 18 evidence bundle");

        var proposal = activation.propose(new Stage19ActivationService.ProposalRequest(
                f.targetId(), bundle.bundleSha256(), Set.of("PC_TELEMETRY", "STT")));
        assertThat(proposal.getStatus()).isEqualTo("AWAITING_OWNER_APPROVAL");
        assertThat(proposal.isProductionActivationAllowed()).isFalse();
        assertThat(proposal.isTargetMutated()).isFalse();
        assertThat(proposal.isExternalActionAttempted()).isFalse();

        assertThatThrownBy(() -> activation.approve(proposal.getId(),
                new Stage19ActivationService.ApprovalRequest("MODEL", 5)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("OWNER");
        assertThatThrownBy(() -> activation.approve(proposal.getId(),
                new Stage19ActivationService.ApprovalRequest("OWNER", 31)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("between 1 and 30 minutes");

        var approved = activation.approve(proposal.getId(), new Stage19ActivationService.ApprovalRequest("OWNER", 5));
        assertThat(approved.getStatus()).isEqualTo("OWNER_APPROVED_SIMULATION_ONLY");
        assertThat(approved.getApprovalExpiresAt()).isAfter(Instant.now());
        var active = activation.startCanary(proposal.getId());
        assertThat(active.getStatus()).isEqualTo("CANARY_ACTIVE_SIMULATION");
        assertThat(active.isProductionActivationAllowed()).isFalse();
        var refresh = activation.refreshAttestation(proposal.getId());
        assertThat(refresh.status()).isEqualTo("ATTESTATION_REFRESH_VERIFIED_SIMULATION_ONLY");
        assertThat(refresh.attestationSha256()).matches("[a-f0-9]{64}");
        assertThat(refresh.targetMutated()).isFalse();
    }

    @Test
    void narrowCanaryCapabilitiesCannotBeExpanded() throws Exception {
        ReadyFixture f = readyFixture("stage19-caps");
        var bundle = stage18.bundle(f.targetId());
        assertThatThrownBy(() -> activation.propose(new Stage19ActivationService.ProposalRequest(
                f.targetId(), bundle.bundleSha256(), Set.of("APP_LAUNCH"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("narrow canary allowlist");
    }

    @Test
    void healthyObservationWindowOnlyProducesSimulationValidation() throws Exception {
        ReadyFixture f = readyFixture("stage19-healthy");
        var p = activation.propose(new Stage19ActivationService.ProposalRequest(
                f.targetId(), stage18.bundle(f.targetId()).bundleSha256(), Set.of("PC_TELEMETRY", "OLLAMA")));
        activation.approve(p.getId(), new Stage19ActivationService.ApprovalRequest("OWNER", 10));
        activation.startCanary(p.getId());

        for (int i = 0; i < 3; i++) {
            var o = activation.observe(p.getId(), new Stage19ActivationService.ObservationRequest(
                    120, 99.95, 0.1, 250, 0, false, "ci-canary",
                    sha("healthy-" + f.targetId() + "-" + i), Instant.now()));
            assertThat(o.getStatus()).isEqualTo("PASS_SIMULATED");
            assertThat(o.isExternalActionAttempted()).isFalse();
        }
        var assessment = activation.assess(p.getId());
        assertThat(assessment.healthy()).isTrue();
        assertThat(assessment.passingWindowSeconds()).isEqualTo(360);
        assertThat(assessment.productionActivationAllowed()).isFalse();

        var validated = activation.validateCanary(p.getId());
        assertThat(validated.getStatus()).isEqualTo("CANARY_VALIDATED_SIMULATION_ONLY");
        assertThat(validated.isTargetMutated()).isFalse();
        assertThat(validated.isExternalActionAttempted()).isFalse();
        assertThat(activation.audit(p.getId())).anyMatch(a -> "CANARY_VALIDATED".equals(a.getEventType()));
    }

    @Test
    void failedCanaryObservationAutomaticallyRehearsesRollback() throws Exception {
        ReadyFixture f = readyFixture("stage19-fail");
        var p = activation.propose(new Stage19ActivationService.ProposalRequest(
                f.targetId(), stage18.bundle(f.targetId()).bundleSha256(), Set.of("PC_TELEMETRY")));
        activation.approve(p.getId(), new Stage19ActivationService.ApprovalRequest("OWNER", 10));
        activation.startCanary(p.getId());
        var failed = activation.observe(p.getId(), new Stage19ActivationService.ObservationRequest(
                120, 94.0, 4.0, 4200, 1, false, "ci-canary", sha("failed-" + f.targetId()), Instant.now()));
        assertThat(failed.getStatus()).isEqualTo("FAIL");
        var proposal = activation.get(p.getId());
        assertThat(proposal.getStatus()).isEqualTo("ROLLBACK_REHEARSED_SIMULATION");
        assertThat(proposal.isProductionActivationAllowed()).isFalse();
        assertThat(proposal.isTargetMutated()).isFalse();
        assertThat(activation.audit(p.getId())).anyMatch(a -> "AUTO_ROLLBACK_REHEARSED".equals(a.getEventType()));
    }

    @Test
    void evidenceDriftAfterProposalBlocksApproval() throws Exception {
        ReadyFixture f = readyFixture("stage19-drift");
        var p = activation.propose(new Stage19ActivationService.ProposalRequest(
                f.targetId(), stage18.bundle(f.targetId()).bundleSha256(), Set.of("PC_TELEMETRY")));
        stage18.recordEvidence(new Stage18ProvisioningService.TargetEvidenceRequest(f.targetId(), "OS_VAULT", "system",
                false, true, null, sha("vault-drift-" + f.targetId()), "target-agent",
                "target vault regression evidence", Instant.now()));
        assertThatThrownBy(() -> activation.approve(p.getId(), new Stage19ActivationService.ApprovalRequest("OWNER", 5)))
                .isInstanceOf(IllegalStateException.class);
    }

    private ReadyFixture readyFixture(String prefix) throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String keyId = prefix + "-key";
        String adapterId = prefix + "-adapter";
        String targetId = prefix + "-target";
        trust.registerSigner(new Stage16CommandTrustService.SignerRegistration(keyId, prefix,
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())));
        adapters.register(new Stage16AdapterRegistryService.AdapterRegistration(adapterId, prefix,
                "SIMULATED_WINDOWS", Set.of("RESTART_SERVICE", "ROLLBACK_RELEASE"), true, "CI_SIMULATED",
                sha(prefix + "-adapter-attestation"), Instant.now()));
        certifications.save(new Stage17AdapterCertificationEntity(UUID.randomUUID(), adapterId, UUID.randomUUID(), 2,
                sha(prefix + "-policy"), sha(prefix + "-transport"), UUID.randomUUID(), 100,
                Instant.now().plus(Duration.ofDays(7))));

        Instant issued = Instant.now();
        Set<String> capabilities = Set.of("PROCESS_READ", "PC_TELEMETRY", "OLLAMA", "DPAPI",
                "VAD", "STT", "TTS", "PRIVATE_TRANSPORT", "APP_LAUNCH", "FILE_OPEN");
        String packageSha = sha("package-" + targetId);
        String certSha = sha("cert-" + targetId);
        String canonical = Stage18ProvisioningService.canonical(targetId, adapterId, "WINDOWS", packageSha, certSha,
                16384, 6144, capabilities, Set.of(), issued);
        stage18.register(new Stage18ProvisioningService.SignedBootstrapManifestRequest(targetId, adapterId, "WINDOWS",
                packageSha, certSha, 16384, 6144, capabilities, Set.of(), issued, keyId,
                Stage18ProvisioningService.shaText(canonical), sign(pair.getPrivate(), canonical)));

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
        assertThat(stage18.readiness(targetId).score()).isEqualTo(100);
        return new ReadyFixture(targetId, adapterId, packageSha, certSha);
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
    private record ReadyFixture(String targetId, String adapterId, String packageSha, String certSha) {}
}
