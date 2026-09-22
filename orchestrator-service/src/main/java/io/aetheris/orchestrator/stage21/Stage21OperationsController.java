package io.aetheris.orchestrator.stage21;

import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/orchestrator/stage21")
public class Stage21OperationsController {
    private final Stage21PilotService pilot;

    public Stage21OperationsController(Stage21PilotService pilot) { this.pilot = pilot; }

    @PostMapping("/pilots")
    public Stage21PilotEntity prepare(@RequestBody Stage21PilotService.PilotRequest request) {
        return pilot.prepare(request);
    }
    @GetMapping("/pilots")
    public List<Stage21PilotEntity> pilots() { return pilot.pilots(); }
    @GetMapping("/pilots/{pilotId}")
    public Stage21PilotEntity pilot(@PathVariable UUID pilotId) { return pilot.get(pilotId); }

    @PostMapping("/pilots/{pilotId}/evidence")
    public Stage21PilotEvidenceEntity evidence(@PathVariable UUID pilotId,
                                               @RequestBody Stage21PilotService.EvidenceRequest request) {
        return pilot.recordEvidence(pilotId, request);
    }
    @GetMapping("/pilots/{pilotId}/evidence")
    public List<Stage21PilotEvidenceEntity> evidence(@PathVariable UUID pilotId) { return pilot.evidence(pilotId); }
    @GetMapping("/evidence")
    public List<Stage21PilotEvidenceEntity> evidence() { return pilot.evidence(); }

    @GetMapping("/pilots/{pilotId}/readiness")
    public Stage21PilotService.Readiness readiness(@PathVariable UUID pilotId) { return pilot.readiness(pilotId); }
    @GetMapping("/pilots/{pilotId}/report")
    public Stage21PilotService.PilotReport report(@PathVariable UUID pilotId) { return pilot.report(pilotId); }

    @GetMapping("/overview")
    public Overview overview() {
        List<Stage21PilotEntity> pilots = pilot.pilots();
        long blocked = 0, incomplete = 0, ready = 0;
        for (Stage21PilotEntity item : pilots) {
            String status = pilot.readiness(item.getId()).status();
            if ("BLOCKED_PENDING_HARDWARE".equals(status)) blocked++;
            else if ("READY_FOR_OWNER_RESTRICTED_PILOT_REVIEW".equals(status)) ready++;
            else incomplete++;
        }
        return new Overview(pilots.size(), blocked, incomplete, ready, pilot.evidence().size(),
                true, false, false, false, false);
    }

    public record Overview(int pilotCount, long blockedPendingHardware, long hardwareEvidenceIncomplete,
                           long readyForOwnerReview, int evidenceCount, boolean hardwareRequired,
                           boolean physicalPilotComplete, boolean ownerPilotActivationAllowed,
                           boolean productionActivationAllowed, boolean externalActionAttempted) {}
}
