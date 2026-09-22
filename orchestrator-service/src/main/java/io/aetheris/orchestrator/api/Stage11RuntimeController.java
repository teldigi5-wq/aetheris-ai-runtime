package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.stage11.Stage11DeploymentService;
import io.aetheris.orchestrator.stage11.Stage11SpeechAdapterService;
import io.aetheris.orchestrator.stage11.Stage11WatcherIngestionBridgeService;
import io.aetheris.orchestrator.vault.WindowsDpapiCredentialVault;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/orchestrator/stage11")
public class Stage11RuntimeController {
    private final Stage11DeploymentService deployment;
    private final Stage11SpeechAdapterService speech;
    private final Stage11WatcherIngestionBridgeService watcherBridge;
    private final Optional<WindowsDpapiCredentialVault> dpapi;

    public Stage11RuntimeController(Stage11DeploymentService deployment,
                                    Stage11SpeechAdapterService speech,
                                    Stage11WatcherIngestionBridgeService watcherBridge,
                                    Optional<WindowsDpapiCredentialVault> dpapi) {
        this.deployment = deployment;
        this.speech = speech;
        this.watcherBridge = watcherBridge;
        this.dpapi = dpapi;
    }

    @GetMapping("/deployment/package-plan")
    public Stage11DeploymentService.PackagePlan packagePlan() { return deployment.packagePlan(); }

    @PostMapping("/deployment/package-validate")
    public Stage11DeploymentService.PackageValidation validatePackage(@RequestBody Stage11DeploymentService.PackageEvidence evidence) {
        return deployment.validatePackage(evidence);
    }

    @PostMapping("/deployment/desktop-performance")
    public Stage11DeploymentService.DesktopPerformanceResult desktop(@RequestBody Stage11DeploymentService.DesktopPerformanceEvidence evidence) {
        return deployment.evaluateDesktop(evidence);
    }

    @GetMapping("/vault/dpapi/readiness")
    public Object dpapiReadiness() {
        if (dpapi.isEmpty()) return Map.of(
                "status", "DISABLED_OR_NON_WINDOWS",
                "backend", "none",
                "verified", false,
                "detail", "Windows DPAPI adapter is opt-in and is not active in this runtime");
        return dpapi.get().probe();
    }

    @PostMapping("/speech/adapters")
    public Stage11SpeechAdapterService.LocalSpeechAdapterSpec registerSpeech(@RequestBody Stage11SpeechAdapterService.LocalSpeechAdapterSpec spec) {
        return speech.register(spec);
    }

    @GetMapping("/speech/adapters")
    public Object speechAdapters() { return speech.adapters(); }

    @PostMapping("/speech/pipeline/validate")
    public Stage11SpeechAdapterService.PipelineReadiness speechPipeline(@RequestBody Stage11SpeechAdapterService.PipelineEvidence evidence) {
        return speech.evaluatePipeline(evidence);
    }

    @PostMapping("/watcher/bridge")
    public Stage11WatcherIngestionBridgeService.BridgeResult watcherBridge(@RequestBody Stage11WatcherIngestionBridgeService.BridgeRequest request) {
        return watcherBridge.bridge(request);
    }
}
