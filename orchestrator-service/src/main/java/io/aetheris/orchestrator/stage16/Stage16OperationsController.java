package io.aetheris.orchestrator.stage16;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orchestrator/stage16")
public class Stage16OperationsController {
    private final Stage16AdapterRegistryService adapters;
    private final Stage16CommandTrustService trust;
    private final Stage16SecureRecoveryService recovery;

    public Stage16OperationsController(Stage16AdapterRegistryService adapters,
                                       Stage16CommandTrustService trust,
                                       Stage16SecureRecoveryService recovery) {
        this.adapters = adapters; this.trust = trust; this.recovery = recovery;
    }

    @GetMapping("/adapters")
    public List<Stage16AdapterEntity> adapters() { return adapters.list(); }

    @PostMapping("/adapters")
    public Stage16AdapterEntity registerAdapter(@RequestBody Stage16AdapterRegistryService.AdapterRegistration request) {
        return adapters.register(request);
    }

    @GetMapping("/signers")
    public List<SignerView> signers() {
        return trust.list().stream().map(s -> new SignerView(s.getKeyId(), s.getDisplayName(),
                s.getPublicKeySha256(), s.isEnabled(), s.getCreatedAt().toString())).toList();
    }

    @PostMapping("/signers")
    public SignerView registerSigner(@RequestBody Stage16CommandTrustService.SignerRegistration request) {
        var s = trust.registerSigner(request);
        return new SignerView(s.getKeyId(), s.getDisplayName(), s.getPublicKeySha256(), s.isEnabled(), s.getCreatedAt().toString());
    }

    @GetMapping("/envelopes")
    public List<Stage16ExecutionEnvelopeEntity> envelopes() { return recovery.recent(); }

    @GetMapping("/envelopes/{id}")
    public Stage16ExecutionEnvelopeEntity envelope(@PathVariable UUID id) { return recovery.get(id); }

    @PostMapping("/envelopes/admit")
    public Stage16ExecutionEnvelopeEntity admit(@RequestBody Stage16SecureRecoveryService.SignedEnvelopeRequest request) {
        return recovery.admit(request);
    }

    @PostMapping("/envelopes/{id}/cancel")
    public Stage16ExecutionEnvelopeEntity cancel(@PathVariable UUID id,
                                                  @RequestBody Stage16SecureRecoveryService.CancelRequest request) {
        return recovery.cancel(id, request);
    }

    @PostMapping("/envelopes/{id}/execute-sandbox")
    public Stage16SecureRecoveryService.ExecutionResult execute(@PathVariable UUID id,
                                                                 @RequestBody(required = false) Stage16SecureRecoveryService.ExecuteRequest request) {
        return recovery.execute(id, request);
    }

    public record SignerView(String keyId, String displayName, String publicKeySha256,
                             boolean enabled, String createdAt) {}
}
