package io.aetheris.orchestrator;

import io.aetheris.orchestrator.ingestion.IncrementalKnowledgeIngestionService;
import io.aetheris.orchestrator.stage10.Stage10WatcherService;
import io.aetheris.orchestrator.stage10.Stage10WorkstationService;
import io.aetheris.orchestrator.stage11.Stage11DeploymentService;
import io.aetheris.orchestrator.stage11.Stage11SpeechAdapterService;
import io.aetheris.orchestrator.stage11.Stage11WatcherIngestionBridgeService;
import io.aetheris.orchestrator.vault.WindowsDpapiCredentialVault;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class Stage11IntegrationTest {
    @Autowired Stage11DeploymentService deployment;
    @Autowired Stage11SpeechAdapterService speech;
    @Autowired Stage11WatcherIngestionBridgeService bridge;
    @Autowired Stage10WatcherService watchers;
    @Autowired IncrementalKnowledgeIngestionService ingestion;
    @Autowired Optional<WindowsDpapiCredentialVault> dpapi;

    @Test
    void packagingAndDpapiRemainTargetHardwareHonest() {
        assertThat(deployment.packagePlan().status()).isEqualTo("SOURCE_PACKAGE_READY_TARGET_VALIDATION_PENDING");
        assertThat(deployment.packagePlan().installerProduced()).isFalse();

        var ci = deployment.validatePackage(new Stage11DeploymentService.PackageEvidence(
                "a".repeat(64), true, true, true, true, false, false));
        assertThat(ci.status()).isEqualTo("CI_PACKAGE_VERIFIED_TARGET_PENDING");
        assertThat(ci.targetMeasured()).isFalse();

        var blocked = deployment.validatePackage(new Stage11DeploymentService.PackageEvidence(
                "b".repeat(64), true, true, true, true, true, true));
        assertThat(blocked.status()).isEqualTo("BLOCKED");
        assertThat(blocked.blockers()).anyMatch(x -> x.contains("administrator"));

        var desktop = deployment.evaluateDesktop(new Stage11DeploymentService.DesktopPerformanceEvidence(
                7.0, 30, 6, 45, 0, false));
        assertThat(desktop.status()).isEqualTo("EVIDENCE_REQUIRED");

        assertThat(dpapi).isEmpty();
    }

    @Test
    void watcherBridgeVerifiesContentHashAndTombstonesDeletes() throws Exception {
        var root = watchers.register(new Stage10WatcherService.RegisterWatchRootRequest(
                "Stage11 test", "D:/Projects/Stage11Test", Set.of("md"), true));
        String path = "D:/Projects/Stage11Test/knowledge.md";
        String content = "stage eleven watcher verified knowledge";
        String hash = sha256(content);

        var first = bridge.bridge(new Stage11WatcherIngestionBridgeService.BridgeRequest(
                root.id(), new Stage10WatcherService.WatchEvent(path, hash, "UPSERT"),
                "Stage 11 knowledge", content, null, null, false, Set.of("test")));
        assertThat(first.status()).isEqualTo("SYNCHRONIZED");
        assertThat(first.ingestion()).isNotNull();
        assertThat(first.ingestion().changed()).isTrue();

        var unchanged = bridge.bridge(new Stage11WatcherIngestionBridgeService.BridgeRequest(
                root.id(), new Stage10WatcherService.WatchEvent(path, hash, "UPSERT"),
                "Stage 11 knowledge", content, null, null, false, Set.of("test")));
        assertThat(unchanged.status()).isEqualTo("SKIPPED_UNCHANGED");
        assertThat(unchanged.ingestion()).isNull();

        assertThatThrownBy(() -> bridge.bridge(new Stage11WatcherIngestionBridgeService.BridgeRequest(
                root.id(), new Stage10WatcherService.WatchEvent(path, "f".repeat(64), "UPSERT"),
                "bad", content, null, null, false, Set.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SHA-256");

        var deleted = bridge.bridge(new Stage11WatcherIngestionBridgeService.BridgeRequest(
                root.id(), new Stage10WatcherService.WatchEvent(path, "", "DELETE"),
                null, null, null, null, false, Set.of()));
        assertThat(deleted.status()).isEqualTo("TOMBSTONED");
        assertThat(deleted.ingestion()).isNotNull();
        assertThat(deleted.ingestion().tombstoned()).isTrue();
    }

    @Test
    void speechAdapterPipelineIsLocalOnlyAndNeedsTargetEvidence() {
        speech.register(new Stage11SpeechAdapterService.LocalSpeechAdapterSpec(
                "silero-vad", "VAD", "syntra-vad", "models/silero-vad.onnx", true, true, true, null));
        speech.register(new Stage11SpeechAdapterService.LocalSpeechAdapterSpec(
                "whisper-local", "STT", "syntra-stt", "models/whisper-small", true, true, true, null));
        speech.register(new Stage11SpeechAdapterService.LocalSpeechAdapterSpec(
                "piper-local", "TTS", "syntra-tts", "models/piper-voice", true, true, true, null));

        assertThatThrownBy(() -> speech.register(new Stage11SpeechAdapterService.LocalSpeechAdapterSpec(
                "remote", "STT", "bad-stt", "https://example.com/model", true, true, true, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("remote URL");

        var synthetic = speech.evaluatePipeline(new Stage11SpeechAdapterService.PipelineEvidence(
                new Stage10WorkstationService.SpeechBenchmarkSample(180, 600, 220, 90, 40, 30, 35, false), true));
        assertThat(synthetic.status()).isEqualTo("EVIDENCE_REQUIRED");

        var target = speech.evaluatePipeline(new Stage11SpeechAdapterService.PipelineEvidence(
                new Stage10WorkstationService.SpeechBenchmarkSample(180, 600, 220, 90, 40, 30, 35, true), true));
        assertThat(target.status()).isEqualTo("TARGET_PIPELINE_VERIFIED");

        var priorityBroken = speech.evaluatePipeline(new Stage11SpeechAdapterService.PipelineEvidence(
                new Stage10WorkstationService.SpeechBenchmarkSample(180, 600, 220, 90, 40, 30, 35, true), false));
        assertThat(priorityBroken.status()).isEqualTo("BLOCKED");
        assertThat(priorityBroken.blockers()).anyMatch(x -> x.contains("priority"));
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
