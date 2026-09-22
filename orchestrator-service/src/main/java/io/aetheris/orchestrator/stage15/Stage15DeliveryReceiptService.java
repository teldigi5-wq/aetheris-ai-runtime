package io.aetheris.orchestrator.stage15;

import io.aetheris.orchestrator.stage14.Stage14OperationalIntelligenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class Stage15DeliveryReceiptService {
    private static final Set<String> STATUSES = Set.of("DELIVERED", "FAILED", "ACKNOWLEDGED");
    private final Stage15DeliveryReceiptRepository repository;
    private final Stage14OperationalIntelligenceService operations;

    public Stage15DeliveryReceiptService(Stage15DeliveryReceiptRepository repository, Stage14OperationalIntelligenceService operations) {
        this.repository = repository; this.operations = operations;
    }

    @Transactional
    public Stage15DeliveryReceiptEntity record(ReceiptRequest request) {
        if (request == null || request.incidentId() == null) throw new IllegalArgumentException("incidentId is required");
        operations.incident(request.incidentId());
        if (!request.providerMeasured()) throw new IllegalArgumentException("Delivery receipt must be provider-measured");
        String provider = token(request.providerId(), "providerId", 80);
        String message = token(request.providerMessageId(), "providerMessageId", 160);
        String status = token(request.deliveryStatus(), "deliveryStatus", 24).toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) throw new IllegalArgumentException("Unsupported delivery receipt status");
        String sha = sha(request.attestationSha256());
        Instant observedAt = request.observedAt() == null ? Instant.now() : request.observedAt();
        if (observedAt.isAfter(Instant.now().plusSeconds(60))) throw new IllegalArgumentException("Delivery receipt cannot be future-dated");
        return repository.save(new Stage15DeliveryReceiptEntity(UUID.randomUUID(), request.incidentId(), provider, message,
                status, sha, true, observedAt));
    }

    public List<Stage15DeliveryReceiptEntity> recent() { return repository.findTop100ByOrderByObservedAtDesc(); }
    public List<Stage15DeliveryReceiptEntity> forIncident(UUID incidentId) { operations.incident(incidentId); return repository.findTop100ByIncidentIdOrderByObservedAtDesc(incidentId); }

    private String token(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String v = value.trim();
        if (v.length() > max || !v.matches("[A-Za-z0-9._:/-]{1," + max + "}")) throw new IllegalArgumentException(label + " is invalid");
        return v;
    }
    private String sha(String value) {
        if (value == null || !value.trim().toLowerCase(Locale.ROOT).matches("[a-f0-9]{64}")) throw new IllegalArgumentException("attestationSha256 must be a SHA-256 value");
        return value.trim().toLowerCase(Locale.ROOT);
    }
    public record ReceiptRequest(UUID incidentId, String providerId, String providerMessageId, String deliveryStatus,
                                 boolean providerMeasured, String attestationSha256, Instant observedAt) {}
}
