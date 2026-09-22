package io.aetheris.orchestrator.stage12;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class Stage12PrivateTransportService {
    private final Stage12ProviderRegistryService providers;

    public Stage12PrivateTransportService(Stage12ProviderRegistryService providers) { this.providers = providers; }

    public TransportEligibility evaluate(TransportEvidence evidence) {
        if (evidence == null) throw new IllegalArgumentException("Private transport evidence is required");
        List<String> blockers = new ArrayList<>();
        Stage12ProviderRegistryService.ProviderView provider = providers.find(evidence.providerId()).orElse(null);
        if (provider == null) blockers.add("registered private-transport provider is required");
        else {
            if (!"PRIVATE_TRANSPORT".equals(provider.type())) blockers.add("provider type must be PRIVATE_TRANSPORT");
            if (!provider.enabled()) blockers.add("private-transport provider must be enabled");
            if (!"PROVIDER_REPORTED_HEALTHY".equals(provider.status())) blockers.add("provider health evidence is required");
        }
        if (!"TLS1.3".equalsIgnoreCase(compact(evidence.tlsVersion()))) blockers.add("TLS 1.3 is required");
        if (!evidence.mutualAuthentication()) blockers.add("mutual device authentication is required");
        if (!evidence.privateTunnel()) blockers.add("private tunnel/network evidence is required");
        if (!evidence.revocationChecked()) blockers.add("revocation state must be checked");
        if (evidence.rawPublicAgentApi()) blockers.add("raw public general-purpose agent API is forbidden");
        String pin = evidence.certificateSha256() == null ? "" : evidence.certificateSha256().trim().toLowerCase(Locale.ROOT);
        if (!pin.matches("[a-f0-9]{64}")) blockers.add("certificate SHA-256 pin is required");
        if (!evidence.targetMeasured()) blockers.add("target transport evidence is required");
        String attestation = evidence.attestationSha256() == null ? "" : evidence.attestationSha256().trim().toLowerCase(Locale.ROOT);
        if (evidence.targetMeasured() && !attestation.matches("[a-f0-9]{64}")) blockers.add("target transport evidence requires an attestation SHA-256");
        return new TransportEligibility(blockers.isEmpty() ? "ELIGIBLE_FOR_PRIVATE_TRANSPORT_TEST" : "BLOCKED",
                List.copyOf(blockers), false, false,
                blockers.isEmpty() ? "All Stage 12 transport gates are satisfied for a controlled deployment test; this does not claim an active production tunnel"
                        : "Remote production activation remains disabled");
    }

    private String compact(String value) {
        return value == null ? "" : value.replace(" ", "").replace("_", "").replace("-", "");
    }

    public record TransportEvidence(String providerId, String tlsVersion, boolean mutualAuthentication,
                                    boolean privateTunnel, boolean revocationChecked, boolean rawPublicAgentApi,
                                    String certificateSha256, boolean targetMeasured, String attestationSha256) {}
    public record TransportEligibility(String status, List<String> blockers, boolean productionTunnelActive,
                                       boolean rawPublicAgentApiAllowed, String detail) {}
}
