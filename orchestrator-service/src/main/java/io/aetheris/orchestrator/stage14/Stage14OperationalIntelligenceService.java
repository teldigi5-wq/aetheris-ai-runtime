package io.aetheris.orchestrator.stage14;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class Stage14OperationalIntelligenceService {
    private static final Set<String> SOURCE_TYPES = Set.of("PROVIDER", "SERVICE", "TASK", "REMOTE", "MARKET", "WORKSTATION", "SECURITY", "DEPLOYMENT");
    private static final Set<String> SEVERITIES = Set.of("INFO", "WARN", "CRITICAL");
    private final Stage14OperationalSignalRepository signals;
    private final Stage14IncidentRepository incidents;

    public Stage14OperationalIntelligenceService(Stage14OperationalSignalRepository signals,
                                                  Stage14IncidentRepository incidents) {
        this.signals = signals;
        this.incidents = incidents;
    }

    @Transactional
    public SignalResult record(SignalRequest request) {
        if (request == null) throw new IllegalArgumentException("Operational signal is required");
        String sourceType = token(request.sourceType(), "sourceType", 32).toUpperCase(Locale.ROOT);
        if (!SOURCE_TYPES.contains(sourceType)) throw new IllegalArgumentException("Unsupported operational source type");
        String sourceId = token(request.sourceId(), "sourceId", 100);
        String signalType = token(request.signalType(), "signalType", 64).toUpperCase(Locale.ROOT);
        String severity = token(request.severity(), "severity", 16).toUpperCase(Locale.ROOT);
        if (!SEVERITIES.contains(severity)) throw new IllegalArgumentException("Unsupported severity");
        String fingerprint = sha(request.fingerprintSha256(), "fingerprintSha256");
        String summary = text(request.summary(), "summary", 1200);
        String attestation = null;
        if (request.sourceMeasured()) attestation = sha(request.attestationSha256(), "attestationSha256");
        else if (request.attestationSha256() != null && !request.attestationSha256().isBlank()) attestation = sha(request.attestationSha256(), "attestationSha256");
        Instant observedAt = request.observedAt() == null ? Instant.now() : request.observedAt();
        if (observedAt.isAfter(Instant.now().plusSeconds(60))) throw new IllegalArgumentException("Signal cannot be materially future-dated");

        Stage14OperationalSignalEntity signal = signals.save(new Stage14OperationalSignalEntity(UUID.randomUUID(), sourceType,
                sourceId, signalType, severity, fingerprint, summary, attestation, request.sourceMeasured(), observedAt));
        Stage14IncidentEntity incident = null;
        if (!"INFO".equals(severity)) {
            String correlationKey = sourceType + ":" + sourceId.toLowerCase(Locale.ROOT) + ":" + fingerprint;
            incident = incidents.findTop20ByCorrelationKeyOrderByLastSeenAtDesc(correlationKey).stream()
                    .filter(i -> !"RESOLVED".equals(i.getStatus())).findFirst().orElse(null);
            if (incident == null) {
                incident = new Stage14IncidentEntity(UUID.randomUUID(), correlationKey,
                        signalType + " on " + sourceId, severity, observedAt);
            } else {
                incident.absorb(severity, observedAt);
            }
            incident = incidents.save(incident);
            signal.attachIncident(incident.getId());
            signal = signals.save(signal);
        }
        return new SignalResult(signal, incident, false);
    }

    public List<Stage14OperationalSignalEntity> recentSignals() { return signals.findTop100ByOrderByObservedAtDesc(); }
    public List<Stage14IncidentEntity> recentIncidents() { return incidents.findTop100ByOrderByLastSeenAtDesc(); }
    public Stage14IncidentEntity incident(UUID id) { return incidents.findById(id).orElseThrow(() -> new NoSuchElementException("Unknown Stage 14 incident")); }
    public List<Stage14OperationalSignalEntity> signalsForIncident(UUID id) { incident(id); return signals.findTop100ByIncidentIdOrderByObservedAtAsc(id); }

    @Transactional
    public Stage14IncidentEntity acknowledge(UUID id, String note) {
        Stage14IncidentEntity incident = incident(id);
        incident.acknowledge(text(note, "note", 1200));
        return incidents.save(incident);
    }

    @Transactional
    public Stage14IncidentEntity markMitigationProposed(UUID id) {
        Stage14IncidentEntity incident = incident(id);
        incident.markMitigationProposed();
        return incidents.save(incident);
    }

    @Transactional
    public Stage14IncidentEntity resolve(UUID id, String note) {
        Stage14IncidentEntity incident = incident(id);
        incident.resolve(text(note, "note", 1200));
        return incidents.save(incident);
    }

    private String token(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String v = value.trim();
        if (v.length() > max || !v.matches("[A-Za-z0-9._:/-]{1," + max + "}")) throw new IllegalArgumentException(label + " is invalid");
        return v;
    }
    private String text(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String v = value.trim();
        if (v.length() > max) throw new IllegalArgumentException(label + " is too long");
        return v;
    }
    private String sha(String value, String label) {
        if (value == null || !value.trim().toLowerCase(Locale.ROOT).matches("[a-f0-9]{64}")) throw new IllegalArgumentException(label + " must be a SHA-256 value");
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record SignalRequest(String sourceType, String sourceId, String signalType, String severity,
                                String fingerprintSha256, String summary, boolean sourceMeasured,
                                String attestationSha256, Instant observedAt) {}
    public record SignalResult(Stage14OperationalSignalEntity signal, Stage14IncidentEntity incident,
                               boolean externalActionAttempted) {}
}
