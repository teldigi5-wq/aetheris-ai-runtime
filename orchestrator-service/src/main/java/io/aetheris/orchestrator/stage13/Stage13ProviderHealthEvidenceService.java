package io.aetheris.orchestrator.stage13;

import io.aetheris.orchestrator.stage12.Stage12ProviderRegistryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class Stage13ProviderHealthEvidenceService {
    private static final Duration MAX_WINDOW_AGE = Duration.ofMinutes(5);
    private final Stage12ProviderRegistryService providers;
    private final Stage13ProviderHealthEvidenceRepository repository;

    public Stage13ProviderHealthEvidenceService(Stage12ProviderRegistryService providers,
                                                Stage13ProviderHealthEvidenceRepository repository) {
        this.providers = providers;
        this.repository = repository;
    }

    @Transactional
    public Stage13ProviderHealthEvidenceEntity record(HealthEvidenceRequest request) {
        if (request == null) throw new IllegalArgumentException("Provider health evidence is required");
        String providerId = token(request.providerId(), "providerId", 80).toLowerCase(Locale.ROOT);
        var provider = providers.find(providerId).orElseThrow(() -> new NoSuchElementException("Unknown Stage 12 provider"));
        if (!provider.enabled()) throw new IllegalStateException("Disabled provider cannot record active Stage 13 health evidence");
        if (!request.providerMeasured()) throw new IllegalArgumentException("Stage 13 provider health must be provider-measured");
        String health = token(request.health(), "health", 24).toUpperCase(Locale.ROOT);
        if (!Set.of("HEALTHY", "DEGRADED", "UNAVAILABLE").contains(health)) {
            throw new IllegalArgumentException("Unsupported provider health state");
        }
        if (request.latencyMs() < 0 || request.latencyMs() > 120_000) throw new IllegalArgumentException("Provider latency is invalid");
        String attestation = sha256(request.attestationSha256(), "attestationSha256");
        Instant observedAt = request.observedAt() == null ? Instant.now() : request.observedAt();
        if (observedAt.isAfter(Instant.now().plusSeconds(60))) throw new IllegalArgumentException("Provider evidence cannot be materially future-dated");
        return repository.save(new Stage13ProviderHealthEvidenceEntity(UUID.randomUUID(), providerId, health,
                request.latencyMs(), attestation, observedAt));
    }

    public List<Stage13ProviderHealthEvidenceEntity> recent(String providerId) {
        String id = token(providerId, "providerId", 80).toLowerCase(Locale.ROOT);
        providers.find(id).orElseThrow(() -> new NoSuchElementException("Unknown Stage 12 provider"));
        return repository.findTop50ByProviderIdOrderByObservedAtDesc(id);
    }

    public HealthWindow assessWindow(String providerId) {
        String id = token(providerId, "providerId", 80).toLowerCase(Locale.ROOT);
        var provider = providers.find(id).orElseThrow(() -> new NoSuchElementException("Unknown Stage 12 provider"));
        List<String> blockers = new ArrayList<>();
        if (!provider.enabled()) blockers.add("provider is disabled");
        if (!"PROVIDER_REPORTED_HEALTHY".equals(provider.status())) blockers.add("Stage 12 provider-reported HEALTHY state is required");

        Instant cutoff = Instant.now().minus(MAX_WINDOW_AGE);
        List<Stage13ProviderHealthEvidenceEntity> window = repository.findTop50ByProviderIdOrderByObservedAtDesc(id).stream()
                .filter(e -> !e.getObservedAt().isBefore(cutoff))
                .toList();
        long healthy = window.stream().filter(e -> "HEALTHY".equals(e.getHealth())).count();
        if (window.size() < 3) blockers.add("at least 3 recent provider-measured samples are required");
        if (healthy < 2) blockers.add("at least 2 recent HEALTHY samples are required");
        if (!window.isEmpty() && "UNAVAILABLE".equals(window.getFirst().getHealth())) blockers.add("latest provider sample is UNAVAILABLE");
        long p95 = percentile95(window.stream().map(Stage13ProviderHealthEvidenceEntity::getLatencyMs).toList());
        return new HealthWindow(blockers.isEmpty() ? "HEALTH_WINDOW_READY" : "BLOCKED", id,
                window.size(), healthy, p95, List.copyOf(blockers), false);
    }

    private long percentile95(List<Long> values) {
        if (values.isEmpty()) return -1;
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95) - 1);
        return sorted.get(index);
    }

    private String token(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String v = value.trim();
        if (v.length() > max || !v.matches("[A-Za-z0-9._:-]{1," + max + "}")) throw new IllegalArgumentException(label + " is invalid");
        return v;
    }

    private String sha256(String value, String label) {
        if (value == null || !value.trim().toLowerCase(Locale.ROOT).matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException(label + " must be a SHA-256 value");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record HealthEvidenceRequest(String providerId, String health, long latencyMs, boolean providerMeasured,
                                        String attestationSha256, Instant observedAt) {}
    public record HealthWindow(String status, String providerId, int sampleCount, long healthySamples,
                               long p95LatencyMs, List<String> blockers, boolean externalActionAttempted) {}
}
