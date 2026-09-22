package io.aetheris.orchestrator.stage15;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class Stage15ReliabilityService {
    private static final Duration WINDOW = Duration.ofDays(30);
    private final Stage15ServiceCatalogService catalog;
    private final Stage15ReliabilityEvidenceRepository repository;

    public Stage15ReliabilityService(Stage15ServiceCatalogService catalog, Stage15ReliabilityEvidenceRepository repository) {
        this.catalog = catalog; this.repository = repository;
    }

    @Transactional
    public Stage15ReliabilityEvidenceEntity record(EvidenceRequest request) {
        if (request == null) throw new IllegalArgumentException("Reliability evidence is required");
        String serviceId = catalog.get(request.serviceId()).getId();
        if (!request.sourceMeasured()) throw new IllegalArgumentException("Stage 15 reliability evidence must be source-measured");
        if (request.totalSamples() <= 0 || request.failedSamples() < 0 || request.failedSamples() > request.totalSamples())
            throw new IllegalArgumentException("Reliability sample counts are invalid");
        String sha = sha(request.attestationSha256());
        Instant observedAt = request.observedAt() == null ? Instant.now() : request.observedAt();
        if (observedAt.isAfter(Instant.now().plusSeconds(60))) throw new IllegalArgumentException("Reliability evidence cannot be future-dated");
        return repository.save(new Stage15ReliabilityEvidenceEntity(UUID.randomUUID(), serviceId,
                request.totalSamples(), request.failedSamples(), sha, true, observedAt));
    }

    public ReliabilityAssessment assess(String serviceId) {
        Stage15ServiceCatalogEntity service = catalog.get(serviceId);
        List<Stage15ReliabilityEvidenceEntity> evidence = repository
                .findTop500ByServiceIdAndObservedAtAfterOrderByObservedAtDesc(service.getId(), Instant.now().minus(WINDOW));
        long total = evidence.stream().mapToLong(Stage15ReliabilityEvidenceEntity::getTotalSamples).sum();
        long failed = evidence.stream().mapToLong(Stage15ReliabilityEvidenceEntity::getFailedSamples).sum();
        boolean maintenance = service.isMaintenanceActive(Instant.now());
        if (total == 0) return new ReliabilityAssessment("INSUFFICIENT_EVIDENCE", service.getId(), 0, 0, -1,
                0, 0, 0, maintenance, false, List.of("no source-measured reliability samples in the last 30 days"), false);

        long successful = total - failed;
        long availabilityBps = (successful * 10_000L) / total;
        long allowedFailures = (total * (10_000L - service.getSloTargetBasisPoints())) / 10_000L;
        long remaining = Math.max(0, allowedFailures - failed);
        long usedPct = allowedFailures == 0 ? (failed == 0 ? 0 : 100) : Math.min(100, (failed * 100L) / allowedFailures);
        List<String> blockers = new ArrayList<>();
        String status;
        if (maintenance) status = "MAINTENANCE_ACTIVE";
        else if (failed > allowedFailures) { status = "ERROR_BUDGET_EXHAUSTED"; blockers.add("measured failures exceed the deterministic error budget"); }
        else if (allowedFailures > 0 && usedPct >= 75) status = "AT_RISK";
        else status = "HEALTHY";
        boolean autonomousExecutionEligible = "HEALTHY".equals(status) && evidence.size() >= 2;
        if (!autonomousExecutionEligible && blockers.isEmpty() && !"HEALTHY".equals(status)) blockers.add("reliability state is not HEALTHY");
        if (evidence.size() < 2) blockers.add("at least two independent reliability evidence records are required for autonomous execution eligibility");
        return new ReliabilityAssessment(status, service.getId(), total, failed, availabilityBps,
                allowedFailures, remaining, usedPct, maintenance, autonomousExecutionEligible, List.copyOf(blockers), false);
    }

    public List<Stage15ReliabilityEvidenceEntity> recent(String serviceId) {
        Stage15ServiceCatalogEntity service = catalog.get(serviceId);
        return repository.findTop500ByServiceIdAndObservedAtAfterOrderByObservedAtDesc(service.getId(), Instant.now().minus(WINDOW));
    }

    private String sha(String value) {
        if (value == null || !value.trim().toLowerCase(Locale.ROOT).matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("attestationSha256 must be a SHA-256 value");
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record EvidenceRequest(String serviceId, long totalSamples, long failedSamples, boolean sourceMeasured,
                                  String attestationSha256, Instant observedAt) {}
    public record ReliabilityAssessment(String status, String serviceId, long totalSamples, long failedSamples,
                                        long observedAvailabilityBasisPoints, long allowedFailures,
                                        long remainingFailureBudget, long budgetUsedPercent, boolean maintenanceActive,
                                        boolean autonomousExecutionEligible, List<String> blockers,
                                        boolean externalActionAttempted) {}
}
