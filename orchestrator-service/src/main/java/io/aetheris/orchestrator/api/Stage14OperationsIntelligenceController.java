package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.stage14.*;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orchestrator/stage14")
public class Stage14OperationsIntelligenceController {
    private final Stage14OperationalIntelligenceService operations;
    private final Stage14SloMonitorService slo;
    private final Stage14ContainmentService containment;
    private final Stage14SelfHealingProposalService healing;
    private final Stage14ChangeImpactService impact;
    private final Stage14DeploymentRehearsalService rehearsal;
    private final Stage14EscalationService escalation;
    private final Stage14PostIncidentReportService reports;

    public Stage14OperationsIntelligenceController(Stage14OperationalIntelligenceService operations,
                                                   Stage14SloMonitorService slo,
                                                   Stage14ContainmentService containment,
                                                   Stage14SelfHealingProposalService healing,
                                                   Stage14ChangeImpactService impact,
                                                   Stage14DeploymentRehearsalService rehearsal,
                                                   Stage14EscalationService escalation,
                                                   Stage14PostIncidentReportService reports) {
        this.operations = operations;
        this.slo = slo;
        this.containment = containment;
        this.healing = healing;
        this.impact = impact;
        this.rehearsal = rehearsal;
        this.escalation = escalation;
        this.reports = reports;
    }

    @PostMapping("/signals")
    public Object signal(@RequestBody Stage14OperationalIntelligenceService.SignalRequest request) { return operations.record(request); }
    @GetMapping("/signals")
    public Object signals() { return operations.recentSignals(); }
    @GetMapping("/incidents")
    public Object incidents() { return operations.recentIncidents(); }
    @PostMapping("/incidents/{id}/acknowledge")
    public Object acknowledge(@PathVariable UUID id, @RequestBody NoteRequest request) { return operations.acknowledge(id, request.note()); }
    @PostMapping("/incidents/{id}/resolve")
    public Object resolve(@PathVariable UUID id, @RequestBody NoteRequest request) { return operations.resolve(id, request.note()); }
    @PostMapping("/slo/provider/{providerId}/assess")
    public Object assessProvider(@PathVariable String providerId) { return slo.assessProvider(providerId); }
    @PostMapping("/containment/evaluate")
    public Object containment(@RequestBody Stage14ContainmentService.ContainmentRequest request) { return containment.evaluate(request); }
    @PostMapping("/self-healing")
    public Object proposeHealing(@RequestBody Stage14SelfHealingProposalService.ProposalRequest request) { return healing.propose(request); }
    @PostMapping("/self-healing/{proposalId}/authorize/{approvalId}")
    public Object authorizeHealing(@PathVariable UUID proposalId, @PathVariable UUID approvalId) { return healing.authorize(proposalId, approvalId); }
    @GetMapping("/self-healing")
    public Object healing() { return healing.recent(); }
    @PostMapping("/change-impact")
    public Object impact(@RequestBody Stage14ChangeImpactService.ChangeImpactRequest request) { return impact.assess(request); }
    @PostMapping("/rehearsal")
    public Object rehearsal(@RequestBody Stage14DeploymentRehearsalService.RehearsalRequest request) { return rehearsal.rehearse(request); }
    @PostMapping("/incidents/{incidentId}/escalation")
    public Object escalation(@PathVariable UUID incidentId, @RequestParam(required = false) String notificationProviderId) {
        return escalation.evaluate(incidentId, notificationProviderId);
    }
    @GetMapping("/incidents/{incidentId}/report")
    public Object report(@PathVariable UUID incidentId) { return reports.build(incidentId); }

    public record NoteRequest(String note) {}
}
