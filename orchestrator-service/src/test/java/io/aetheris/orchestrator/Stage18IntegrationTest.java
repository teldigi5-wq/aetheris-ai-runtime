package io.aetheris.orchestrator;

import io.aetheris.orchestrator.stage16.*;
import io.aetheris.orchestrator.stage18.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class Stage18IntegrationTest {
    @Autowired Stage18ProvisioningService provisioning;
    @Autowired Stage16CommandTrustService trust;
    @Autowired Stage16AdapterRegistryService adapters;

    @Test
    void signedBootstrapManifestIsVerifiedAndTamperingFails() throws Exception {
        Fixture f = fixture("stage18-manifest");
        var manifest = registerManifest(f, "stage18-target-a", Set.of("github-read"));
        assertThat(manifest.getStatus()).isEqualTo("MANIFEST_VERIFIED_SIMULATION_ONLY");
        assertThat(manifest.isSimulationOnly()).isTrue();
        assertThat(manifest.getCapabilities()).contains("PC_TELEMETRY", "DPAPI", "STT", "TTS");

        Instant issued = Instant.now();
        String canonical = Stage18ProvisioningService.canonical("stage18-target-b", f.adapterId(), "WINDOWS",
                sha("package-b"), sha("cert-b"), 16384, 6144,
                Set.of("PC_TELEMETRY", "DPAPI"), Set.of(), issued);
        var bad = new Stage18ProvisioningService.SignedBootstrapManifestRequest("stage18-target-b", f.adapterId(),
                "WINDOWS", sha("package-b"), sha("cert-b"), 16384, 6144,
                Set.of("PC_TELEMETRY", "DPAPI"), Set.of(), issued, f.keyId(),
                Stage18ProvisioningService.shaText(canonical), sign(f.keyPair().getPrivate(), canonical + "tampered"));
        assertThatThrownBy(() -> provisioning.register(bad)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature verification failed");
    }

    @Test
    void forbiddenTargetAuthorityIsRejected() throws Exception {
        Fixture f = fixture("stage18-forbidden");
        Instant issued = Instant.now();
        Set<String> caps = Set.of("PC_TELEMETRY", "ARBITRARY_SHELL");
        String canonical = Stage18ProvisioningService.canonical("stage18-forbidden-target", f.adapterId(), "WINDOWS",
                sha("package-forbidden"), sha("cert-forbidden"), 16384, 6144, caps, Set.of(), issued);
        var request = new Stage18ProvisioningService.SignedBootstrapManifestRequest("stage18-forbidden-target",
                f.adapterId(), "WINDOWS", sha("package-forbidden"), sha("cert-forbidden"), 16384, 6144,
                caps, Set.of(), issued, f.keyId(), Stage18ProvisioningService.shaText(canonical),
                sign(f.keyPair().getPrivate(), canonical));
        assertThatThrownBy(() -> provisioning.register(request)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported or forbidden target authority");
    }

    @Test
    void ciEvidenceAndBenchmarksCannotSelfPromoteTargetReadiness() throws Exception {
        Fixture f = fixture("stage18-ci-evidence");
        String target = "stage18-ci-target";
        var manifest = registerManifest(f, target, Set.of("github-read"));
        recordGeneric(target, "DEVICE_IDENTITY", "system", true, false, manifest.getDeviceCertificateSha256());
        recordGeneric(target, "PACKAGE_INTEGRITY", "system", true, false, manifest.getPackageSha256());
        recordGeneric(target, "OS_VAULT", "system", true, false, null);
        recordGeneric(target, "PRIVATE_TRANSPORT", "system", true, false, manifest.getDeviceCertificateSha256());
        recordGeneric(target, "PROVIDER_BINDING", "github-read", true, false, null);
        provisioning.recordBenchmark(new Stage18ProvisioningService.BenchmarkRequest(target, "RESOURCE", false,
                32768, 8192, 0, 0, 0, 0, sha("resource-ci"), "ci-runner", Instant.now()));
        provisioning.recordBenchmark(new Stage18ProvisioningService.BenchmarkRequest(target, "SPEECH", false,
                0, 0, 500, 120, 0, 0, sha("speech-ci"), "ci-runner", Instant.now()));
        provisioning.recordBenchmark(new Stage18ProvisioningService.BenchmarkRequest(target, "LOCAL_MODEL", false,
                0, 0, 900, 0, 20, 0, sha("model-ci"), "ci-runner", Instant.now()));

        var readiness = provisioning.readiness(target);
        assertThat(readiness.productionActivationAllowed()).isFalse();
        assertThat(readiness.externalActionAttempted()).isFalse();
        assertThat(readiness.status()).isNotEqualTo("READY_FOR_OWNER_ACTIVATION_REVIEW");
        assertThat(provisioning.evidenceForTarget(target)).allMatch(e -> !e.isMeasuredOnTarget());
        assertThat(provisioning.rehearse(target).targetMutated()).isFalse();
    }

    @Test
    void exactPackageAndCertificateCorrelationRemainRequired() throws Exception {
        Fixture f = fixture("stage18-correlation");
        String target = "stage18-correlation-target";
        var manifest = registerManifest(f, target, Set.of());
        recordGeneric(target, "DEVICE_IDENTITY", "system", true, true, sha("wrong-cert"));
        recordGeneric(target, "PACKAGE_INTEGRITY", "system", true, true, sha("wrong-package"));
        recordGeneric(target, "OS_VAULT", "system", true, true, null);
        recordGeneric(target, "PRIVATE_TRANSPORT", "system", true, true, sha("wrong-cert"));
        provisioning.recordBenchmark(new Stage18ProvisioningService.BenchmarkRequest(target, "RESOURCE", true,
                32768, 8192, 0, 0, 0, 0, sha("resource-target"), "target-agent", Instant.now()));
        provisioning.recordBenchmark(new Stage18ProvisioningService.BenchmarkRequest(target, "SPEECH", true,
                0, 0, 450, 100, 0, 0, sha("speech-target"), "target-agent", Instant.now()));
        provisioning.recordBenchmark(new Stage18ProvisioningService.BenchmarkRequest(target, "LOCAL_MODEL", true,
                0, 0, 800, 0, 25, 0, sha("model-target"), "target-agent", Instant.now()));

        var readiness = provisioning.readiness(target);
        assertThat(readiness.blockers()).anyMatch(x -> x.contains("certificate fingerprint"));
        assertThat(readiness.blockers()).anyMatch(x -> x.contains("package SHA-256"));
        assertThat(readiness.productionActivationAllowed()).isFalse();
        assertThat(provisioning.bundle(target).bundleSha256()).matches("[a-f0-9]{64}");
    }

    @Test
    void secretLikeEvidenceIsRejected() throws Exception {
        Fixture f = fixture("stage18-secret");
        String target = "stage18-secret-target";
        registerManifest(f, target, Set.of());
        var request = new Stage18ProvisioningService.TargetEvidenceRequest(target, "OS_VAULT", "system", true,
                false, null, sha("secret-evidence"), "ci-runner", "api_key=do-not-log-this", Instant.now());
        assertThatThrownBy(() -> provisioning.recordEvidence(request)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("secret material");
    }

    private Stage18BootstrapManifestEntity registerManifest(Fixture f, String target, Set<String> providers) throws Exception {
        Instant issued = Instant.now();
        Set<String> capabilities = Set.of("PC_TELEMETRY", "DPAPI", "STT", "TTS", "OLLAMA", "PRIVATE_TRANSPORT");
        String packageSha = sha("package-" + target);
        String certSha = sha("cert-" + target);
        String canonical = Stage18ProvisioningService.canonical(target, f.adapterId(), "WINDOWS", packageSha, certSha,
                16384, 6144, capabilities, providers, issued);
        return provisioning.register(new Stage18ProvisioningService.SignedBootstrapManifestRequest(target, f.adapterId(),
                "WINDOWS", packageSha, certSha, 16384, 6144, capabilities, providers, issued, f.keyId(),
                Stage18ProvisioningService.shaText(canonical), sign(f.keyPair().getPrivate(), canonical)));
    }

    private void recordGeneric(String target, String kind, String component, boolean passed,
                               boolean measuredOnTarget, String subjectSha) {
        provisioning.recordEvidence(new Stage18ProvisioningService.TargetEvidenceRequest(target, kind, component,
                passed, measuredOnTarget, subjectSha, sha(target + kind + component + measuredOnTarget),
                measuredOnTarget ? "target-agent" : "ci-runner", "bounded evidence for " + kind, Instant.now()));
    }

    private Fixture fixture(String prefix) throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String keyId = prefix + "-key";
        String adapterId = prefix + "-adapter";
        trust.registerSigner(new Stage16CommandTrustService.SignerRegistration(keyId, prefix,
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())));
        adapters.register(new Stage16AdapterRegistryService.AdapterRegistration(adapterId, prefix,
                "SIMULATED_WINDOWS", Set.of("RESTART_SERVICE", "ROLLBACK_RELEASE"), true, "CI_SIMULATED",
                sha(prefix + "-adapter-attestation"), Instant.now()));
        return new Fixture(keyId, adapterId, pair);
    }

    private String sign(PrivateKey key, String payload) throws Exception {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(key); signature.update(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }
    private String sha(String value) { return Stage18ProvisioningService.shaText(value); }
    private record Fixture(String keyId, String adapterId, KeyPair keyPair) {}
}
