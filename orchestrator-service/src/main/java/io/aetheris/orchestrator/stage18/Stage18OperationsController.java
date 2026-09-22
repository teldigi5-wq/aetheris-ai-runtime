package io.aetheris.orchestrator.stage18;

import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/orchestrator/stage18")
public class Stage18OperationsController {
    private final Stage18ProvisioningService provisioning;

    public Stage18OperationsController(Stage18ProvisioningService provisioning) { this.provisioning = provisioning; }

    @PostMapping("/manifests")
    public Stage18BootstrapManifestEntity register(@RequestBody Stage18ProvisioningService.SignedBootstrapManifestRequest request) {
        return provisioning.register(request);
    }
    @GetMapping("/manifests")
    public List<Stage18BootstrapManifestEntity> manifests() { return provisioning.manifests(); }
    @GetMapping("/manifests/{targetId}/latest")
    public Stage18BootstrapManifestEntity latest(@PathVariable String targetId) { return provisioning.latest(targetId); }

    @PostMapping("/evidence")
    public Stage18TargetEvidenceEntity recordEvidence(@RequestBody Stage18ProvisioningService.TargetEvidenceRequest request) {
        return provisioning.recordEvidence(request);
    }
    @PostMapping("/benchmarks")
    public Stage18TargetEvidenceEntity recordBenchmark(@RequestBody Stage18ProvisioningService.BenchmarkRequest request) {
        return provisioning.recordBenchmark(request);
    }
    @GetMapping("/evidence")
    public List<Stage18TargetEvidenceEntity> evidence() { return provisioning.evidence(); }
    @GetMapping("/evidence/{targetId}")
    public List<Stage18TargetEvidenceEntity> evidenceForTarget(@PathVariable String targetId) {
        return provisioning.evidenceForTarget(targetId);
    }

    @GetMapping("/readiness/{targetId}")
    public Stage18ProvisioningService.ReadinessAssessment readiness(@PathVariable String targetId) {
        return provisioning.readiness(targetId);
    }
    @GetMapping("/bundle/{targetId}")
    public Stage18ProvisioningService.EvidenceBundle bundle(@PathVariable String targetId) {
        return provisioning.bundle(targetId);
    }
    @PostMapping("/rehearse/{targetId}")
    public Stage18ProvisioningService.ProvisioningRehearsal rehearse(@PathVariable String targetId) {
        return provisioning.rehearse(targetId);
    }

    @GetMapping("/overview")
    public Overview overview() {
        var manifests = provisioning.manifests();
        var evidence = provisioning.evidence();
        long targetMeasured = evidence.stream().filter(Stage18TargetEvidenceEntity::isMeasuredOnTarget).count();
        long simulated = evidence.size() - targetMeasured;
        long ready = manifests.stream().map(Stage18BootstrapManifestEntity::getTargetId).distinct()
                .map(provisioning::readiness).filter(r -> "READY_FOR_OWNER_ACTIVATION_REVIEW".equals(r.status())).count();
        return new Overview(manifests.size(), evidence.size(), targetMeasured, simulated, ready, false, false);
    }

    public record Overview(int manifestCount, int evidenceCount, long targetMeasuredEvidence,
                           long simulatedEvidence, long activationReviewReadyTargets,
                           boolean productionActivationAllowed, boolean externalActionAttempted) {}
}
