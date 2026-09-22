package io.aetheris.orchestrator.stage19;

import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/orchestrator/stage19")
public class Stage19OperationsController {
    private final Stage19ActivationService activation;

    public Stage19OperationsController(Stage19ActivationService activation) { this.activation = activation; }

    @PostMapping("/proposals")
    public Stage19ActivationProposalEntity propose(@RequestBody Stage19ActivationService.ProposalRequest request) {
        return activation.propose(request);
    }
    @GetMapping("/proposals")
    public List<Stage19ActivationProposalEntity> proposals() { return activation.proposals(); }
    @GetMapping("/proposals/{proposalId}")
    public Stage19ActivationProposalEntity proposal(@PathVariable UUID proposalId) { return activation.get(proposalId); }

    @PostMapping("/proposals/{proposalId}/approve")
    public Stage19ActivationProposalEntity approve(@PathVariable UUID proposalId,
                                                    @RequestBody Stage19ActivationService.ApprovalRequest request) {
        return activation.approve(proposalId, request);
    }
    @PostMapping("/proposals/{proposalId}/canary/start")
    public Stage19ActivationProposalEntity startCanary(@PathVariable UUID proposalId) {
        return activation.startCanary(proposalId);
    }
    @PostMapping("/proposals/{proposalId}/canary/observe")
    public Stage19CanaryObservationEntity observe(@PathVariable UUID proposalId,
                                                  @RequestBody Stage19ActivationService.ObservationRequest request) {
        return activation.observe(proposalId, request);
    }
    @GetMapping("/proposals/{proposalId}/canary/assessment")
    public Stage19ActivationService.CanaryAssessment assess(@PathVariable UUID proposalId) {
        return activation.assess(proposalId);
    }
    @PostMapping("/proposals/{proposalId}/canary/validate")
    public Stage19ActivationProposalEntity validate(@PathVariable UUID proposalId) {
        return activation.validateCanary(proposalId);
    }
    @PostMapping("/proposals/{proposalId}/revoke")
    public Stage19ActivationProposalEntity revoke(@PathVariable UUID proposalId,
                                                   @RequestBody Stage19ActivationService.ReasonRequest request) {
        return activation.revoke(proposalId, request);
    }
    @PostMapping("/proposals/{proposalId}/rollback/rehearse")
    public Stage19ActivationProposalEntity rollback(@PathVariable UUID proposalId,
                                                     @RequestBody Stage19ActivationService.ReasonRequest request) {
        return activation.rehearseRollback(proposalId, request);
    }
    @PostMapping("/proposals/{proposalId}/attestation/refresh")
    public Stage19ActivationService.AttestationRefresh refresh(@PathVariable UUID proposalId) {
        return activation.refreshAttestation(proposalId);
    }

    @GetMapping("/observations")
    public List<Stage19CanaryObservationEntity> observations() { return activation.observations(); }
    @GetMapping("/audit")
    public List<Stage19ActivationAuditEntity> audit() { return activation.audit(); }
    @GetMapping("/audit/{proposalId}")
    public List<Stage19ActivationAuditEntity> audit(@PathVariable UUID proposalId) { return activation.audit(proposalId); }

    @GetMapping("/overview")
    public Overview overview() {
        var proposals = activation.proposals();
        long awaiting = proposals.stream().filter(x -> "AWAITING_OWNER_APPROVAL".equals(x.getStatus())).count();
        long active = proposals.stream().filter(x -> "CANARY_ACTIVE_SIMULATION".equals(x.getStatus())).count();
        long validated = proposals.stream().filter(x -> "CANARY_VALIDATED_SIMULATION_ONLY".equals(x.getStatus())).count();
        long rolledBack = proposals.stream().filter(x -> "ROLLBACK_REHEARSED_SIMULATION".equals(x.getStatus())).count();
        return new Overview(proposals.size(), awaiting, active, validated, rolledBack,
                activation.observations().size(), activation.audit().size(), false, false, false);
    }

    public record Overview(int proposalCount, long awaitingOwnerApproval, long activeSimulationCanaries,
                           long validatedSimulationCanaries, long rollbackRehearsals,
                           int observationCount, int auditEventCount,
                           boolean productionActivationAllowed, boolean targetMutated,
                           boolean externalActionAttempted) {}
}
