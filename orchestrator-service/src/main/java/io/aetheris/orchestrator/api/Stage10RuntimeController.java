package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.host.HostCommandEnvelope;
import io.aetheris.orchestrator.stage10.Stage10OperationsService;
import io.aetheris.orchestrator.stage10.Stage10RegressionGateService;
import io.aetheris.orchestrator.stage10.Stage10RemoteTransportService;
import io.aetheris.orchestrator.stage10.Stage10WatcherService;
import io.aetheris.orchestrator.stage10.Stage10WorkstationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orchestrator/stage10")
public class Stage10RuntimeController {
    private final Stage10WorkstationService workstation;
    private final Stage10WatcherService watchers;
    private final Stage10RemoteTransportService remote;
    private final Stage10RegressionGateService regression;
    private final Stage10OperationsService operations;

    public Stage10RuntimeController(Stage10WorkstationService workstation, Stage10WatcherService watchers,
                                    Stage10RemoteTransportService remote, Stage10RegressionGateService regression,
                                    Stage10OperationsService operations) {
        this.workstation = workstation;
        this.watchers = watchers;
        this.remote = remote;
        this.regression = regression;
        this.operations = operations;
    }

    @GetMapping("/bootstrap-plan")
    public Stage10WorkstationService.BootstrapPlan bootstrap() { return workstation.bootstrapPlan(); }

    @PostMapping("/workstation/readiness")
    public Stage10WorkstationService.WorkstationReadiness readiness(@RequestBody Stage10WorkstationService.WorkstationProbe probe) {
        return workstation.assess(probe);
    }

    @PostMapping("/workstation/commands/validate")
    public Stage10WorkstationService.CommandGuardDecision validate(@RequestBody Stage10WorkstationService.LeastPrivilegeCommand command) {
        return workstation.validateLeastPrivilegeCommand(command);
    }

    @PostMapping("/workstation/commands/issue")
    public HostCommandEnvelope issue(@RequestBody Stage10WorkstationService.LeastPrivilegeCommand command) {
        return workstation.issueLeastPrivilegeCommand(command);
    }

    @PostMapping("/resources/govern")
    public Stage10WorkstationService.ResourceDecision govern(@RequestBody ResourceGovernRequest request) {
        return workstation.govern(request.snapshot(), request.workload());
    }

    @PostMapping("/speech/benchmark")
    public Stage10WorkstationService.SpeechBenchmarkResult speech(@RequestBody Stage10WorkstationService.SpeechBenchmarkSample sample) {
        return workstation.evaluateSpeech(sample);
    }

    @GetMapping("/vault/readiness")
    public Stage10WorkstationService.VaultReadiness vault() { return workstation.vaultReadiness(); }

    @PostMapping("/remote/validate")
    public Stage10RemoteTransportService.TransportReadiness remote(@RequestBody Stage10RemoteTransportService.RemoteTransportEvidence evidence) {
        return remote.evaluate(evidence);
    }

    @PostMapping("/watch-roots")
    public Stage10WatcherService.WatchRoot addRoot(@RequestBody Stage10WatcherService.RegisterWatchRootRequest request) {
        return watchers.register(request);
    }

    @PutMapping("/watch-roots/{id}/enabled")
    public Stage10WatcherService.WatchRoot rootEnabled(@PathVariable UUID id, @RequestParam boolean enabled) {
        return watchers.setEnabled(id, enabled);
    }

    @PostMapping("/watch-roots/{id}/events")
    public Stage10WatcherService.WatchEventResult watchEvent(@PathVariable UUID id, @RequestBody Stage10WatcherService.WatchEvent event) {
        return watchers.ingest(id, event);
    }

    @GetMapping("/watch-roots")
    public List<Stage10WatcherService.WatchRoot> roots() { return watchers.roots(); }

    @GetMapping("/watch-events")
    public List<Stage10WatcherService.WatchEventResult> events() { return watchers.recent(); }

    @PostMapping("/regression-gate")
    public Stage10RegressionGateService.RegressionGateResult regression(@RequestBody Stage10RegressionGateService.RegressionGateRequest request) {
        return regression.evaluate(request);
    }

    @PostMapping("/operations/slo")
    public Stage10OperationsService.SloAssessment slo(@RequestBody Stage10OperationsService.SloEvidence evidence) {
        return operations.assess(evidence);
    }

    @GetMapping("/operations/recovery")
    public Stage10OperationsService.RecoveryRunbook recovery() { return operations.recovery(); }

    @GetMapping("/operations/update-policy")
    public Stage10OperationsService.InstallerUpdatePolicy updatePolicy() { return operations.updatePolicy(); }

    public record ResourceGovernRequest(Stage10WorkstationService.ResourceSnapshot snapshot,
                                        Stage10WorkstationService.WorkloadRequest workload) {}
}
