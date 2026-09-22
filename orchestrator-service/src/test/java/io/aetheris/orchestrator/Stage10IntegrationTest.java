package io.aetheris.orchestrator;

import io.aetheris.orchestrator.stage10.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class Stage10IntegrationTest {
    @Autowired Stage10WorkstationService workstation;
    @Autowired Stage10WatcherService watchers;
    @Autowired Stage10RemoteTransportService remote;
    @Autowired Stage10RegressionGateService regression;
    @Autowired Stage10OperationsService operations;

    @Test
    void bootstrapAndReadinessRemainHardwareHonest() {
        assertThat(workstation.bootstrapPlan().status()).isEqualTo("PACKAGE_READY_HARDWARE_PENDING");
        var ready = workstation.assess(new Stage10WorkstationService.WorkstationProbe("Windows 11", 12, 16384, 6144, true, true, true));
        assertThat(ready.status()).isEqualTo("READY_FOR_HARDWARE_VALIDATION");
        assertThat(ready.windowsHostEligible()).isTrue();
        assertThat(ready.speechEligible()).isTrue();
        assertThat(ready.localInferenceEligible()).isTrue();
        assertThat(ready.detail()).contains("no software was installed");
        assertThat(workstation.vaultReadiness().status()).isEqualTo("HARDWARE_PENDING");
    }

    @Test
    void leastPrivilegeWindowsGuardHasNoShellOrAdminEscape() {
        UUID host = UUID.randomUUID();
        var telemetry = workstation.validateLeastPrivilegeCommand(new Stage10WorkstationService.LeastPrivilegeCommand(
                host, "system.telemetry", "snapshot", Map.of()));
        assertThat(telemetry.allowed()).isTrue();
        assertThatThrownBy(() -> workstation.validateLeastPrivilegeCommand(new Stage10WorkstationService.LeastPrivilegeCommand(
                host, "process.status", "status", Map.of("processName", "powershell.exe"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("forbidden");
        assertThatThrownBy(() -> workstation.validateLeastPrivilegeCommand(new Stage10WorkstationService.LeastPrivilegeCommand(
                host, "shell.exec", "run", Map.of("command", "cmd.exe /c whoami"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resourceGovernorProtectsVoiceAndPriorityControls() {
        var defer = workstation.govern(
                new Stage10WorkstationService.ResourceSnapshot(62, 70, 74, true, true),
                new Stage10WorkstationService.WorkloadRequest("LOCAL_LLM_HEAVY", 50, true));
        assertThat(defer.decision()).isEqualTo("DEFER");
        var priority = workstation.govern(
                new Stage10WorkstationService.ResourceSnapshot(99, 99, 99, true, true),
                new Stage10WorkstationService.WorkloadRequest("PRIORITY_CONTROL", 100, false));
        assertThat(priority.decision()).isEqualTo("ALLOW");
    }

    @Test
    void speechNeedsMeasuredTargetHardwareEvidence() {
        var synthetic = workstation.evaluateSpeech(new Stage10WorkstationService.SpeechBenchmarkSample(180, 600, 220, 90, 40, 30, 35, false));
        assertThat(synthetic.verdict()).isEqualTo("EVIDENCE_REQUIRED");
        var measured = workstation.evaluateSpeech(new Stage10WorkstationService.SpeechBenchmarkSample(180, 600, 220, 90, 40, 30, 35, true));
        assertThat(measured.verdict()).isEqualTo("VERIFIED");
    }

    @Test
    void ownerWatchRootsAreExplicitHashedAndCannotEscape() {
        var root = watchers.register(new Stage10WatcherService.RegisterWatchRootRequest("Aetheris", "D:/Projects/Aetheris", Set.of("java", "md"), true));
        String hashA = "a".repeat(64), hashB = "b".repeat(64);
        assertThat(watchers.ingest(root.id(), new Stage10WatcherService.WatchEvent("D:/Projects/Aetheris/README.md", hashA, "UPSERT")).status()).isEqualTo("ADDED");
        assertThat(watchers.ingest(root.id(), new Stage10WatcherService.WatchEvent("D:/Projects/Aetheris/README.md", hashA, "UPSERT")).status()).isEqualTo("UNCHANGED");
        assertThat(watchers.ingest(root.id(), new Stage10WatcherService.WatchEvent("D:/Projects/Aetheris/README.md", hashB, "UPSERT")).status()).isEqualTo("CHANGED");
        assertThatThrownBy(() -> watchers.ingest(root.id(), new Stage10WatcherService.WatchEvent("D:/Secrets/passwords.md", hashA, "UPSERT")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("outside");
        assertThatThrownBy(() -> watchers.register(new Stage10WatcherService.RegisterWatchRootRequest("drive", "C:/", Set.of("java"), true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("subdirectory");
    }

    @Test
    void privateTransportGateFailsClosed() {
        var ready = remote.evaluate(new Stage10RemoteTransportService.RemoteTransportEvidence(
                "TLS1.3", true, true, true, true, Set.of("mission.read", "mission.stop"), false, 15));
        assertThat(ready.status()).isEqualTo("READY_FOR_DEPLOYMENT_TEST");
        assertThat(ready.rawPublicAgentApiAllowed()).isFalse();
        var blocked = remote.evaluate(new Stage10RemoteTransportService.RemoteTransportEvidence(
                "TLS1.2", false, false, true, false, Set.of("mission.read"), true, 180));
        assertThat(blocked.status()).isEqualTo("BLOCKED");
        assertThat(blocked.blockers()).isNotEmpty();
    }

    @Test
    void regressionGateBlocksQualityOrSafetyRegressions() {
        var allowed = regression.evaluate(new Stage10RegressionGateService.RegressionGateRequest(
                "coding-core", 78, 77, 3, true, 2, 0));
        assertThat(allowed.decision()).isEqualTo("PROMOTE_ALLOWED");
        var blocked = regression.evaluate(new Stage10RegressionGateService.RegressionGateRequest(
                "coding-core", 78, 62, 3, true, 2, 0));
        assertThat(blocked.decision()).isEqualTo("PROMOTION_BLOCKED");
        assertThat(blocked.blockers()).anyMatch(x -> x.contains("below Stage 9 PASS"));
    }

    @Test
    void sloAndRecoveryEvidenceCannotPretendTargetRuntimeValidation() {
        var synthetic = operations.assess(new Stage10OperationsService.SloEvidence(120, 80, 180, 5, 10, .2, 60, false));
        assertThat(synthetic.status()).isEqualTo("EVIDENCE_REQUIRED");
        var measured = operations.assess(new Stage10OperationsService.SloEvidence(120, 80, 180, 5, 10, .2, 60, true));
        assertThat(measured.status()).isEqualTo("HEALTHY");
        assertThat(operations.recovery().steps()).anyMatch(x -> x.contains("STOP ALL"));
        assertThat(operations.recovery().prohibitions()).anyMatch(x -> x.contains("never weaken UAC"));
        assertThat(operations.updatePolicy().installerProduced()).isFalse();
        assertThat(operations.updatePolicy().state()).isEqualTo("SIGNED_CANARY_REQUIRED");
    }
}
