package io.aetheris.orchestrator.stage14;

import io.aetheris.orchestrator.stage13.Stage13ProviderHealthEvidenceService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class Stage14SloMonitorService {
    private final Stage13ProviderHealthEvidenceService health;
    private final Stage14OperationalIntelligenceService operations;

    public Stage14SloMonitorService(Stage13ProviderHealthEvidenceService health,
                                    Stage14OperationalIntelligenceService operations) {
        this.health = health;
        this.operations = operations;
    }

    public SloAssessment assessProvider(String providerId) {
        var window = health.assessWindow(providerId);
        if ("HEALTH_WINDOW_READY".equals(window.status())) {
            return new SloAssessment("SLO_HEALTHY", providerId, window.sampleCount(), window.healthySamples(),
                    window.p95LatencyMs(), null, false, false);
        }
        boolean unavailable = window.blockers().stream().anyMatch(x -> x.toUpperCase().contains("UNAVAILABLE"));
        String severity = unavailable ? "CRITICAL" : "WARN";
        String fingerprint = sha256("provider-slo:" + providerId + ":" + String.join("|", window.blockers()));
        var result = operations.record(new Stage14OperationalIntelligenceService.SignalRequest(
                "PROVIDER", providerId, "PROVIDER_SLO_BREACH", severity, fingerprint,
                "Stage 13 provider health window is blocked: " + String.join("; ", window.blockers()),
                false, null, null));
        return new SloAssessment("INCIDENT_CANDIDATE", providerId, window.sampleCount(), window.healthySamples(),
                window.p95LatencyMs(), result.incident() == null ? null : result.incident().getId().toString(), false, false);
    }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public record SloAssessment(String status, String providerId, int sampleCount, long healthySamples,
                                long p95LatencyMs, String incidentId, boolean providerDisabled,
                                boolean externalActionAttempted) {}
}
