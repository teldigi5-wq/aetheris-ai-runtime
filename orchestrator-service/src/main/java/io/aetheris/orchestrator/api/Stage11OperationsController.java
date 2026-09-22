package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.stage11.Stage11RemoteDeviceBindingService;
import io.aetheris.orchestrator.stage11.Stage11RuntimeEvidenceEntity;
import io.aetheris.orchestrator.stage11.Stage11RuntimeEvidenceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orchestrator/stage11")
public class Stage11OperationsController {
    private final Stage11RemoteDeviceBindingService remote;
    private final Stage11RuntimeEvidenceService evidence;

    public Stage11OperationsController(Stage11RemoteDeviceBindingService remote, Stage11RuntimeEvidenceService evidence) {
        this.remote = remote;
        this.evidence = evidence;
    }

    @PostMapping("/remote/pair")
    public Stage11RemoteDeviceBindingService.PairingResult pair(@RequestBody Stage11RemoteDeviceBindingService.PairingRequest request) {
        return remote.pair(request);
    }

    @PostMapping("/remote/authenticate")
    public Stage11RemoteDeviceBindingService.AccessResult authenticate(@RequestBody RemoteAuthenticateRequest request) {
        return remote.authenticate(request.sessionId(), request.pairingToken(), request.capability(), request.certificateSha256());
    }

    @PostMapping("/remote/{sessionId}/revoke")
    public void revoke(@PathVariable UUID sessionId) { remote.revoke(sessionId); }

    @GetMapping("/remote/bindings")
    public List<Stage11RemoteDeviceBindingService.BindingView> bindings() { return remote.bindings(); }

    @PostMapping("/evidence")
    public Stage11RuntimeEvidenceEntity recordEvidence(@RequestBody Stage11RuntimeEvidenceService.EvidenceRequest request) {
        return evidence.record(request);
    }

    @GetMapping("/evidence")
    public List<Stage11RuntimeEvidenceEntity> evidence() { return evidence.recent(); }

    public record RemoteAuthenticateRequest(UUID sessionId, String pairingToken, String capability,
                                            String certificateSha256) {}
}
