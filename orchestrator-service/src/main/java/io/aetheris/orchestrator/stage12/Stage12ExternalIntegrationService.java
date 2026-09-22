package io.aetheris.orchestrator.stage12;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class Stage12ExternalIntegrationService {
    private final Stage12ProviderRegistryService providers;

    public Stage12ExternalIntegrationService(Stage12ProviderRegistryService providers) { this.providers = providers; }

    public IntegrationReadiness notification(String providerId) {
        return readiness(providerId, "NOTIFICATION", false, Set.of("SEND_NOTIFICATION"));
    }

    public IntegrationReadiness marketData(String providerId) {
        return readiness(providerId, "MARKET_DATA", true, Set.of("READ_MARKET"));
    }

    public IntegrationReadiness exchangeTestnet(String providerId) {
        Stage12ProviderRegistryService.ProviderView p = provider(providerId);
        List<String> blockers = baseBlockers(p, "EXCHANGE_TESTNET");
        if (!p.endpoint().toLowerCase(Locale.ROOT).contains("testnet") && !p.endpoint().toLowerCase(Locale.ROOT).contains("sandbox")) {
            blockers.add("exchange endpoint is not explicitly testnet/sandbox");
        }
        if (!p.capabilities().contains("TEST_ORDER")) blockers.add("TEST_ORDER capability is required");
        if (p.capabilities().stream().anyMatch(Set.of("LIVE_ORDER", "WITHDRAWAL", "TRANSFER")::contains)) {
            blockers.add("live-money, withdrawal and transfer capabilities are forbidden");
        }
        return new IntegrationReadiness(blockers.isEmpty() ? "TESTNET_PROVIDER_READY_FOR_CONTROLLED_TEST" : "BLOCKED",
                List.copyOf(blockers), p.providerId(), p.type(), false, false, false,
                blockers.isEmpty() ? "Provider has explicit testnet-only configuration and provider-reported health; deterministic trading risk gates remain authoritative"
                        : "No exchange order may be attempted");
    }

    public DeliveryEvidence evaluateNotificationDelivery(String providerId, DeliveryEvidence evidence) {
        IntegrationReadiness ready = notification(providerId);
        if (!ready.blockers().isEmpty()) throw new IllegalStateException("Notification provider is not ready for a controlled delivery test");
        if (evidence == null || !evidence.providerMeasured()) throw new IllegalArgumentException("Provider-measured delivery evidence is required");
        String attestation = evidence.attestationSha256() == null ? "" : evidence.attestationSha256().trim().toLowerCase(Locale.ROOT);
        if (!attestation.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Notification delivery evidence requires a SHA-256 attestation");
        String status = token(evidence.status(), 24).toUpperCase(Locale.ROOT);
        if (!Set.of("DELIVERED", "FAILED", "DEFERRED").contains(status)) throw new IllegalArgumentException("Unsupported delivery status");
        return new DeliveryEvidence(status, true, attestation, false);
    }

    public MarketEvidence evaluateMarketSnapshot(String providerId, MarketEvidence evidence) {
        IntegrationReadiness ready = marketData(providerId);
        if (!ready.blockers().isEmpty()) throw new IllegalStateException("Market-data provider is not ready");
        if (evidence == null || !evidence.providerMeasured()) throw new IllegalArgumentException("Provider-measured market evidence is required");
        if (evidence.ageMs() < 0 || evidence.ageMs() > 60_000) throw new IllegalArgumentException("Market snapshot is too stale or has invalid age");
        String attestation = evidence.attestationSha256() == null ? "" : evidence.attestationSha256().trim().toLowerCase(Locale.ROOT);
        if (!attestation.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Market evidence requires a SHA-256 attestation");
        return new MarketEvidence(evidence.symbol(), evidence.ageMs(), true, attestation, true);
    }

    private IntegrationReadiness readiness(String providerId, String expectedType, boolean requireReadOnly, Set<String> requiredCaps) {
        Stage12ProviderRegistryService.ProviderView p = provider(providerId);
        List<String> blockers = baseBlockers(p, expectedType);
        if (requireReadOnly && !p.readOnly()) blockers.add("provider must be read-only");
        for (String cap : requiredCaps) if (!p.capabilities().contains(cap)) blockers.add(cap + " capability is required");
        return new IntegrationReadiness(blockers.isEmpty() ? "READY_FOR_CONTROLLED_PROVIDER_TEST" : "BLOCKED",
                List.copyOf(blockers), p.providerId(), p.type(), false, false, false,
                blockers.isEmpty() ? "Configuration and provider health permit a controlled test; no external action has been attempted"
                        : "External provider action remains disabled");
    }

    private List<String> baseBlockers(Stage12ProviderRegistryService.ProviderView p, String expectedType) {
        List<String> blockers = new ArrayList<>();
        if (!expectedType.equals(p.type())) blockers.add("provider type must be " + expectedType);
        if (!p.enabled()) blockers.add("provider must be enabled");
        if (!"PROVIDER_REPORTED_HEALTHY".equals(p.status())) blockers.add("provider-reported healthy evidence is required");
        if (p.credentialAlias() == null || p.credentialAlias().isBlank()) blockers.add("credential alias is required; secret value must remain in the vault");
        return blockers;
    }

    private Stage12ProviderRegistryService.ProviderView provider(String providerId) {
        if (providerId == null || providerId.isBlank()) throw new IllegalArgumentException("providerId is required");
        return providers.find(providerId).orElseThrow(() -> new NoSuchElementException("Unknown Stage 12 provider"));
    }

    private String token(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || !value.matches("[A-Za-z0-9._:-]{1," + max + "}")) {
            throw new IllegalArgumentException("status is invalid");
        }
        return value.trim();
    }

    public record IntegrationReadiness(String status, List<String> blockers, String providerId, String providerType,
                                       boolean externalActionAttempted, boolean liveMoneyEnabled, boolean withdrawalOrTransferEnabled,
                                       String detail) {}
    public record DeliveryEvidence(String status, boolean providerMeasured, String attestationSha256,
                                   boolean secretValueExposed) {}
    public record MarketEvidence(String symbol, long ageMs, boolean providerMeasured, String attestationSha256,
                                 boolean readOnly) {}
}
