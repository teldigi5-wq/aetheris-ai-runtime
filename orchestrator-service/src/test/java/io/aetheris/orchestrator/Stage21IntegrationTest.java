package io.aetheris.orchestrator;

import io.aetheris.orchestrator.stage18.Stage18ProvisioningService;
import io.aetheris.orchestrator.stage20.*;
import io.aetheris.orchestrator.stage21.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class Stage21IntegrationTest {
    @Autowired Stage21PilotService pilots;
    @Autowired Stage20ActivationAuthorizationRepository authorizations;
    @Autowired Stage20TargetReceiptRepository receipts;

    @Test
    void repositoryOnlyPilotStartsBlockedPendingHardware() {
        var auth = auth("stage21-blocked", Set.of("PC_TELEMETRY", "OLLAMA", "STT"));
        var pilot = pilots.prepare(new Stage21PilotService.PilotRequest(auth.getId(), Set.of("PC_TELEMETRY")));
        var readiness = pilots.readiness(pilot.getId());
        assertThat(pilot.getStatus()).isEqualTo("BLOCKED_PENDING_HARDWARE");
        assertThat(readiness.status()).isEqualTo("BLOCKED_PENDING_HARDWARE");
        assertThat(readiness.score()).isZero();
        assertThat(readiness.hardwareRequired()).isTrue();
        assertThat(readiness.ownerPilotActivationAllowed()).isFalse();
        assertThat(pilot.isPhysicalPilotComplete()).isFalse();
        assertThat(pilot.isProductionActivationAllowed()).isFalse();
        assertThat(pilot.isTargetMutated()).isFalse();
        assertThat(pilot.isExternalActionAttempted()).isFalse();
    }

    @Test
    void restrictedPilotCannotWidenCapabilities() {
        var auth = auth("stage21-caps", Set.of("PC_TELEMETRY", "OLLAMA"));
        assertThatThrownBy(() -> pilots.prepare(new Stage21PilotService.PilotRequest(auth.getId(), Set.of("APP_LAUNCH"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("restricted physical-pilot allowlist");
        assertThatThrownBy(() -> pilots.prepare(new Stage21PilotService.PilotRequest(auth.getId(), Set.of("STT"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot widen");
    }

    @Test
    void simulatedEvidenceNeverCountsAsPhysicalHardwareEvidence() {
        var auth = auth("stage21-sim", Set.of("PC_TELEMETRY"));
        var pilot = pilots.prepare(new Stage21PilotService.PilotRequest(auth.getId(), Set.of("PC_TELEMETRY")));
        int i = 0;
        for (String kind : Stage21PilotService.REQUIRED_EVIDENCE) {
            pilots.recordEvidence(pilot.getId(), new Stage21PilotService.EvidenceRequest(kind, "PASS", false,
                    "ci-simulation", null, sha("sim-" + kind + "-" + (++i)), Instant.now()));
        }
        var readiness = pilots.readiness(pilot.getId());
        assertThat(readiness.status()).isEqualTo("BLOCKED_PENDING_HARDWARE");
        assertThat(readiness.passingEvidence()).isZero();
        assertThat(readiness.missingEvidence()).containsExactlyElementsOf(Stage21PilotService.REQUIRED_EVIDENCE);
    }

    @Test
    void targetIdentityAndPackageMustMatchStage20Authorization() {
        var auth = auth("stage21-bind", Set.of("PC_TELEMETRY"));
        var pilot = pilots.prepare(new Stage21PilotService.PilotRequest(auth.getId(), Set.of("PC_TELEMETRY")));
        assertThatThrownBy(() -> pilots.recordEvidence(pilot.getId(), new Stage21PilotService.EvidenceRequest(
                "DEVICE_IDENTITY", "PASS", true, "target-agent", sha("wrong-cert"), sha("e1"), Instant.now())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("authorized certificate");
        assertThatThrownBy(() -> pilots.recordEvidence(pilot.getId(), new Stage21PilotService.EvidenceRequest(
                "PACKAGE_INTEGRITY", "PASS", true, "target-agent", sha("wrong-package"), sha("e2"), Instant.now())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("authorized package");
    }

    @Test
    void completeTargetEvidenceOnlyReachesOwnerReviewNotActivation() {
        var auth = auth("stage21-ready", Set.of("PC_TELEMETRY", "OLLAMA"));
        var pilot = pilots.prepare(new Stage21PilotService.PilotRequest(auth.getId(), Set.of("PC_TELEMETRY")));
        String receiptSha = sha("target-receipt-" + pilot.getId());
        receipts.save(new Stage20TargetReceiptEntity(UUID.randomUUID(), auth.getId(), UUID.randomUUID(), auth.getTargetId(),
                sha("lease-" + pilot.getId()), auth.getPackageSha256(), auth.getDeviceCertificateSha256(),
                sha("attestation-" + pilot.getId()), "SUCCESS", receiptSha, sha("signature-" + pilot.getId()),
                "VERIFIED_TARGET_REPORTED_CONTRACT_ONLY", true, Instant.now()));

        int i = 0;
        for (String kind : Stage21PilotService.REQUIRED_EVIDENCE) {
            String subject = null;
            if ("DEVICE_IDENTITY".equals(kind)) subject = auth.getDeviceCertificateSha256();
            if ("PACKAGE_INTEGRITY".equals(kind)) subject = auth.getPackageSha256();
            if ("STAGE20_LEASE_RECEIPT".equals(kind)) subject = receiptSha;
            pilots.recordEvidence(pilot.getId(), new Stage21PilotService.EvidenceRequest(kind, "PASS", true,
                    "target-agent", subject, sha("physical-evidence-" + kind + "-" + (++i)), Instant.now()));
        }

        var readiness = pilots.readiness(pilot.getId());
        assertThat(readiness.status()).isEqualTo("READY_FOR_OWNER_RESTRICTED_PILOT_REVIEW");
        assertThat(readiness.score()).isEqualTo(100);
        assertThat(readiness.physicalEvidenceComplete()).isTrue();
        assertThat(readiness.ownerPilotActivationAllowed()).isFalse();
        assertThat(readiness.productionActivationAllowed()).isFalse();
        var report = pilots.report(pilot.getId());
        assertThat(report.reportSha256()).matches("[a-f0-9]{64}");
        assertThat(report.physicalPilotComplete()).isFalse();
        assertThat(report.ownerPilotActivationAllowed()).isFalse();
        assertThat(report.externalActionAttempted()).isFalse();
    }

    private Stage20ActivationAuthorizationEntity auth(String prefix, Set<String> capabilities) {
        Instant now = Instant.now();
        Stage20ActivationAuthorizationEntity auth = new Stage20ActivationAuthorizationEntity(UUID.randomUUID(), UUID.randomUUID(),
                prefix + "-target", prefix + "-adapter", sha(prefix + "-p19"), sha(prefix + "-attestation"),
                sha(prefix + "-bundle"), sha(prefix + "-manifest"), sha(prefix + "-package"), sha(prefix + "-cert"),
                prefix + "-owner", prefix + "-device", capabilities, sha(prefix + "-authorization"),
                sha(prefix + "-signature"), now.minusSeconds(30), now.plus(Duration.ofMinutes(20)),
                now.minus(Duration.ofMinutes(1)), now.plus(Duration.ofMinutes(25)), 5);
        return authorizations.save(auth);
    }

    private String sha(String value) { return Stage18ProvisioningService.shaText(value); }
}
