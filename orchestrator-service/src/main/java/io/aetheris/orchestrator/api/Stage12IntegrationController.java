package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.stage12.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orchestrator/stage12")
public class Stage12IntegrationController {
    private final Stage12ProviderRegistryService providers;
    private final Stage12ReleaseTrustService releases;
    private final Stage12TargetAttestationService targetAttestation;
    private final Stage12PrivateTransportService transport;
    private final Stage12ExternalIntegrationService external;

    public Stage12IntegrationController(Stage12ProviderRegistryService providers,
                                        Stage12ReleaseTrustService releases,
                                        Stage12TargetAttestationService targetAttestation,
                                        Stage12PrivateTransportService transport,
                                        Stage12ExternalIntegrationService external) {
        this.providers = providers;
        this.releases = releases;
        this.targetAttestation = targetAttestation;
        this.transport = transport;
        this.external = external;
    }

    @GetMapping("/providers")
    public Object providers() { return providers.list(); }

    @PostMapping("/providers")
    public Object registerProvider(@RequestBody Stage12ProviderRegistryService.ProviderRegistration request) {
        return providers.register(request);
    }

    @PostMapping("/providers/{providerId}/health")
    public Object providerHealth(@PathVariable String providerId,
                                 @RequestBody Stage12ProviderRegistryService.ProviderHealthEvidence evidence) {
        return providers.recordHealth(providerId, evidence);
    }

    @GetMapping("/release/readiness")
    public Object releaseReadiness() { return releases.readiness(); }

    @PostMapping("/release/verify")
    public Object release(@RequestBody Stage12ReleaseTrustService.ReleaseManifest manifest) {
        return releases.verify(manifest);
    }

    @GetMapping("/target-attestation/{kind}")
    public Object target(@PathVariable String kind, @RequestParam String attestationSha256) {
        return targetAttestation.assess(kind, attestationSha256);
    }

    @PostMapping("/transport/evaluate")
    public Object transport(@RequestBody Stage12PrivateTransportService.TransportEvidence evidence) {
        return transport.evaluate(evidence);
    }

    @GetMapping("/notification/{providerId}/readiness")
    public Object notification(@PathVariable String providerId) { return external.notification(providerId); }

    @PostMapping("/notification/{providerId}/delivery-evidence")
    public Object notificationEvidence(@PathVariable String providerId,
                                       @RequestBody Stage12ExternalIntegrationService.DeliveryEvidence evidence) {
        return external.evaluateNotificationDelivery(providerId, evidence);
    }

    @GetMapping("/market/{providerId}/readiness")
    public Object market(@PathVariable String providerId) { return external.marketData(providerId); }

    @PostMapping("/market/{providerId}/snapshot-evidence")
    public Object marketEvidence(@PathVariable String providerId,
                                 @RequestBody Stage12ExternalIntegrationService.MarketEvidence evidence) {
        return external.evaluateMarketSnapshot(providerId, evidence);
    }

    @GetMapping("/exchange-testnet/{providerId}/readiness")
    public Object exchangeTestnet(@PathVariable String providerId) { return external.exchangeTestnet(providerId); }
}
