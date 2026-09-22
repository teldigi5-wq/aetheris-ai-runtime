package io.aetheris.orchestrator;

import io.aetheris.orchestrator.stage10.Stage10RemoteTransportService;
import io.aetheris.orchestrator.stage11.Stage11RemoteDeviceBindingService;
import io.aetheris.orchestrator.stage11.Stage11RuntimeEvidenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class Stage11OperationsIntegrationTest {
    @Autowired Stage11RemoteDeviceBindingService remote;
    @Autowired Stage11RuntimeEvidenceService evidence;

    @Test
    void remoteSessionsAreShortLivedCapabilityScopedAndCertificateBound() {
        var transport = new Stage10RemoteTransportService.RemoteTransportEvidence(
                "TLS1.3", true, true, true, true,
                Set.of("status.read", "task.stop"), false, 10);
        var paired = remote.pair(new Stage11RemoteDeviceBindingService.PairingRequest(
                "c".repeat(64), Set.of("READ_STATUS", "STOP_TASK"), 30, transport));
        assertThat(paired.status()).isEqualTo("READY_FOR_PRIVATE_TRANSPORT_INTEGRATION_TEST");
        assertThat(paired.pairingToken()).isNotBlank();
        assertThat(paired.capabilities()).containsExactlyInAnyOrder("READ_STATUS", "STOP_TASK");

        var access = remote.authenticate(paired.sessionId(), paired.pairingToken(), "READ_STATUS", "c".repeat(64));
        assertThat(access.status()).isEqualTo("AUTHENTICATED_CONTRACT_BOUND_SESSION");

        assertThatThrownBy(() -> remote.authenticate(
                paired.sessionId(), paired.pairingToken(), "READ_STATUS", "d".repeat(64)))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("fingerprint");

        assertThatThrownBy(() -> remote.pair(new Stage11RemoteDeviceBindingService.PairingRequest(
                "e".repeat(64), Set.of("READ_STATUS"), 61, transport)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("60 minutes");

        remote.revoke(paired.sessionId());
        assertThatThrownBy(() -> remote.authenticate(
                paired.sessionId(), paired.pairingToken(), "READ_STATUS", "c".repeat(64)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void remotePairingCannotClaimScopesMissingFromTransportEvidence() {
        var transport = new Stage10RemoteTransportService.RemoteTransportEvidence(
                "TLS1.3", true, true, true, true,
                Set.of("status.read"), false, 5);
        assertThatThrownBy(() -> remote.pair(new Stage11RemoteDeviceBindingService.PairingRequest(
                "f".repeat(64), Set.of("TAKE_CONTROL"), 15, transport)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("control.take");
    }

    @Test
    void runtimeEvidenceIsDurableButDoesNotSelfCertifyHardware() {
        var ci = evidence.record(new Stage11RuntimeEvidenceService.EvidenceRequest(
                "SLO", "PASS", "github-actions", false, null,
                "Stage 11 CI latency regression gate passed"));
        assertThat(ci.getStatus()).isEqualTo("CI_RECORDED:PASS");
        assertThat(ci.isTargetMeasured()).isFalse();

        var target = evidence.record(new Stage11RuntimeEvidenceService.EvidenceRequest(
                "WORKSTATION", "HEALTHY", "aetheris-host-agent", true, "a".repeat(64),
                "Owner workstation health sample reported by host evidence channel"));
        assertThat(target.getStatus()).isEqualTo("TARGET_REPORTED:HEALTHY");
        assertThat(target.getAttestationSha256()).hasSize(64);

        assertThatThrownBy(() -> evidence.record(new Stage11RuntimeEvidenceService.EvidenceRequest(
                "WORKSTATION", "HEALTHY", "host", true, null, "missing attestation")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attestation");

        assertThatThrownBy(() -> evidence.record(new Stage11RuntimeEvidenceService.EvidenceRequest(
                "INCIDENT", "OPEN", "host", false, null, "password=do-not-store-this")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credential material");

        assertThat(evidence.recent()).extracting(x -> x.getKind()).contains("SLO", "WORKSTATION");
    }
}
