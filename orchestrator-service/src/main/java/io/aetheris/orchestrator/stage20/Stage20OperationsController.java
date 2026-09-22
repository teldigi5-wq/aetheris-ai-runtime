package io.aetheris.orchestrator.stage20;

import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/orchestrator/stage20")
public class Stage20OperationsController {
    private final Stage20HardwareHandoffService handoff;

    public Stage20OperationsController(Stage20HardwareHandoffService handoff) { this.handoff = handoff; }

    @PostMapping("/authorizations")
    public Stage20ActivationAuthorizationEntity authorize(@RequestBody Stage20HardwareHandoffService.SignedAuthorizationRequest request) {
        return handoff.authorize(request);
    }
    @GetMapping("/authorizations")
    public List<Stage20ActivationAuthorizationEntity> authorizations() { return handoff.authorizations(); }
    @GetMapping("/authorizations/{authorizationId}")
    public Stage20ActivationAuthorizationEntity authorization(@PathVariable UUID authorizationId) {
        return handoff.getAuthorization(authorizationId);
    }

    @PostMapping("/authorizations/{authorizationId}/challenges")
    public Stage20HardwareHandoffService.ChallengeIssue challenge(@PathVariable UUID authorizationId) {
        return handoff.issueChallenge(authorizationId);
    }
    @PostMapping("/challenges/{challengeId}/attest")
    public Stage20DeviceChallengeEntity attest(@PathVariable UUID challengeId,
                                               @RequestBody Stage20HardwareHandoffService.DeviceAttestationRequest request) {
        return handoff.attest(challengeId, request);
    }
    @GetMapping("/challenges")
    public List<Stage20DeviceChallengeEntity> challenges() { return handoff.challenges(); }

    @PostMapping("/authorizations/{authorizationId}/leases")
    public Stage20ActivationLeaseEntity startLease(@PathVariable UUID authorizationId,
                                                   @RequestBody Stage20HardwareHandoffService.LeaseRequest request) {
        return handoff.startLease(authorizationId, request);
    }
    @PostMapping("/leases/{leaseId}/renew")
    public Stage20ActivationLeaseEntity renewLease(@PathVariable UUID leaseId,
                                                   @RequestBody Stage20HardwareHandoffService.LeaseRenewalRequest request) {
        return handoff.renewLease(leaseId, request);
    }
    @GetMapping("/leases")
    public List<Stage20ActivationLeaseEntity> leases() { return handoff.leases(); }

    @PostMapping("/authorizations/{authorizationId}/emergency-stop")
    public Stage20ActivationAuthorizationEntity emergencyStop(@PathVariable UUID authorizationId,
                                                              @RequestBody Stage20HardwareHandoffService.ReasonRequest request) {
        return handoff.engageEmergencyStop(authorizationId, request);
    }
    @PostMapping("/authorizations/{authorizationId}/emergency-stop/clear")
    public Stage20ActivationAuthorizationEntity clearEmergencyStop(@PathVariable UUID authorizationId,
                                                                   @RequestBody Stage20HardwareHandoffService.ClearInterlockRequest request) {
        return handoff.clearEmergencyStop(authorizationId, request);
    }

    @PostMapping("/leases/{leaseId}/receipts")
    public Stage20TargetReceiptEntity receipt(@PathVariable UUID leaseId,
                                              @RequestBody Stage20HardwareHandoffService.TargetReceiptRequest request) {
        return handoff.recordReceipt(leaseId, request);
    }
    @GetMapping("/receipts")
    public List<Stage20TargetReceiptEntity> receipts() { return handoff.receipts(); }

    @GetMapping("/authorizations/{authorizationId}/evidence-bundle")
    public Stage20HardwareHandoffService.PostActivationEvidenceBundle evidenceBundle(@PathVariable UUID authorizationId) {
        return handoff.postActivationBundle(authorizationId);
    }
    @GetMapping("/audit")
    public List<Stage20HandoffAuditEntity> audit() { return handoff.audit(); }
    @GetMapping("/audit/{authorizationId}")
    public List<Stage20HandoffAuditEntity> audit(@PathVariable UUID authorizationId) { return handoff.audit(authorizationId); }

    @GetMapping("/overview")
    public Overview overview() {
        var auth = handoff.authorizations();
        var leases = handoff.leases();
        long emergency = auth.stream().filter(Stage20ActivationAuthorizationEntity::isEmergencyStopEngaged).count();
        long activeLeases = leases.stream().filter(x -> Set.of("LEASE_ACTIVE_SIMULATION_ONLY", "LEASE_RENEWED_SIMULATION_ONLY").contains(x.getStatus())).count();
        long revokedLeases = leases.stream().filter(x -> "LEASE_REVOKED_SIMULATION_ONLY".equals(x.getStatus())).count();
        return new Overview(auth.size(), handoff.challenges().size(), leases.size(), activeLeases, revokedLeases,
                handoff.receipts().size(), emergency, handoff.audit().size(), false, false, false);
    }

    public record Overview(int authorizationCount, int challengeCount, int leaseCount, long activeSimulationLeases,
                           long revokedSimulationLeases, int receiptCount, long emergencyStoppedAuthorizations,
                           int auditEventCount, boolean productionActivationAllowed,
                           boolean targetMutated, boolean externalActionAttempted) {}
}
