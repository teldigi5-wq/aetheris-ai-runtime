package io.aetheris.orchestrator.stage15;

import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/orchestrator/stage15")
public class Stage15OperationsController {
    private final Stage15ServiceCatalogService catalog;
    private final Stage15ReliabilityService reliability;
    private final Stage15ChaosRehearsalService chaos;
    private final Stage15RecoveryService recovery;
    private final Stage15IncidentReplayService replay;
    private final Stage15DeliveryReceiptService receipts;
    private final Stage15ReliabilityScoreService scores;

    public Stage15OperationsController(Stage15ServiceCatalogService catalog, Stage15ReliabilityService reliability,
                                       Stage15ChaosRehearsalService chaos, Stage15RecoveryService recovery,
                                       Stage15IncidentReplayService replay, Stage15DeliveryReceiptService receipts,
                                       Stage15ReliabilityScoreService scores) {
        this.catalog = catalog; this.reliability = reliability; this.chaos = chaos; this.recovery = recovery;
        this.replay = replay; this.receipts = receipts; this.scores = scores;
    }

    @GetMapping("/services") public List<Stage15ServiceCatalogEntity> services() { return catalog.list(); }
    @PostMapping("/services") public Stage15ServiceCatalogEntity register(@RequestBody Stage15ServiceCatalogService.ServiceRegistration request) { return catalog.register(request); }
    @PostMapping("/services/{id}/maintenance") public Stage15ServiceCatalogEntity maintenance(@PathVariable String id, @RequestBody Stage15ServiceCatalogService.MaintenanceRequest request) { return catalog.scheduleMaintenance(id, request); }

    @PostMapping("/reliability/evidence") public Stage15ReliabilityEvidenceEntity evidence(@RequestBody Stage15ReliabilityService.EvidenceRequest request) { return reliability.record(request); }
    @GetMapping("/reliability/{serviceId}") public Stage15ReliabilityService.ReliabilityAssessment reliability(@PathVariable String serviceId) { return reliability.assess(serviceId); }
    @GetMapping("/score/{serviceId}") public Stage15ReliabilityScoreService.ReliabilityScore score(@PathVariable String serviceId) { return scores.score(serviceId); }

    @PostMapping("/chaos/rehearse") public Stage15ChaosRehearsalService.RehearsalResult rehearse(@RequestBody Stage15ChaosRehearsalService.RehearsalRequest request) { return chaos.rehearse(request); }

    @GetMapping("/recovery") public List<Stage15RecoveryPlanEntity> recoveryPlans() { return recovery.recent(); }
    @PostMapping("/recovery") public Stage15RecoveryPlanEntity plan(@RequestBody Stage15RecoveryService.RecoveryPlanRequest request) { return recovery.create(request); }
    @PostMapping("/recovery/{id}/authorize/{approvalId}") public Stage15RecoveryPlanEntity authorize(@PathVariable UUID id, @PathVariable UUID approvalId) { return recovery.authorize(id, approvalId); }
    @GetMapping("/recovery/{id}/readiness") public Stage15RecoveryService.ExecutionReadiness readiness(@PathVariable UUID id) { return recovery.executionReadiness(id); }
    @PostMapping("/recovery/{id}/execution-evidence") public Stage15RecoveryPlanEntity executionEvidence(@PathVariable UUID id, @RequestBody Stage15RecoveryService.TargetExecutionEvidence request) { return recovery.recordTargetExecutionEvidence(id, request); }
    @PostMapping("/recovery/{id}/verify") public Stage15RecoveryPlanEntity verify(@PathVariable UUID id, @RequestBody Stage15RecoveryService.VerificationEvidence request) { return recovery.verify(id, request); }

    @GetMapping("/incidents/{id}/replay") public Stage15IncidentReplayService.IncidentReplay replay(@PathVariable UUID id) { return replay.replay(id); }
    @GetMapping("/receipts") public List<Stage15DeliveryReceiptEntity> receipts() { return receipts.recent(); }
    @PostMapping("/receipts") public Stage15DeliveryReceiptEntity receipt(@RequestBody Stage15DeliveryReceiptService.ReceiptRequest request) { return receipts.record(request); }

    @GetMapping("/overview") public Map<String, Object> overview() {
        List<Stage15ServiceCatalogEntity> services = catalog.list();
        List<Stage15RecoveryPlanEntity> plans = recovery.recent();
        return Map.of("serviceCount", services.size(), "recoveryPlanCount", plans.size(),
                "aetherisExecutionCount", plans.stream().filter(Stage15RecoveryPlanEntity::isExecutedByAetheris).count(),
                "rollbackRequiredCount", plans.stream().filter(Stage15RecoveryPlanEntity::isRollbackRequired).count(),
                "deliveryReceiptCount", receipts.recent().size(), "targetAdapterConnected", false,
                "liveMoneyAuthority", false, "unrestrictedShellAuthority", false);
    }
}
