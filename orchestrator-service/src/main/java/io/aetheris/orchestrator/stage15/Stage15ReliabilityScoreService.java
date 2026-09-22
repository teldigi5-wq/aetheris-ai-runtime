package io.aetheris.orchestrator.stage15;

import io.aetheris.orchestrator.stage14.Stage14OperationalIntelligenceService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Duration;
import java.util.*;

@Service
public class Stage15ReliabilityScoreService {
    private final Stage15ServiceCatalogService catalog;
    private final Stage15ReliabilityService reliability;
    private final Stage15RecoveryPlanRepository recoveries;
    private final Stage14OperationalIntelligenceService operations;

    public Stage15ReliabilityScoreService(Stage15ServiceCatalogService catalog, Stage15ReliabilityService reliability,
                                          Stage15RecoveryPlanRepository recoveries, Stage14OperationalIntelligenceService operations) {
        this.catalog = catalog; this.reliability = reliability; this.recoveries = recoveries; this.operations = operations;
    }

    public ReliabilityScore score(String serviceId) {
        Stage15ServiceCatalogEntity service = catalog.get(serviceId);
        var r = reliability.assess(service.getId());
        int slo = switch (r.status()) {
            case "HEALTHY" -> 50;
            case "AT_RISK", "MAINTENANCE_ACTIVE" -> 35;
            case "ERROR_BUDGET_EXHAUSTED" -> 15;
            default -> 0;
        };
        int evidence = r.totalSamples() >= 100 ? 20 : r.totalSamples() > 0 ? 10 : 0;
        boolean rollbackCoverage = recoveries.findTop100ByServiceIdOrderByUpdatedAtDesc(service.getId()).stream()
                .anyMatch(p -> p.getRollbackAction() != null && !p.getRollbackAction().isBlank());
        int recovery = rollbackCoverage ? 20 : 0;
        Instant cutoff = Instant.now().minus(Duration.ofDays(30));
        long criticalSignals = operations.recentSignals().stream()
                .filter(s -> service.getId().equalsIgnoreCase(s.getSourceId()))
                .filter(s -> "CRITICAL".equals(s.getSeverity()))
                .filter(s -> !s.getObservedAt().isBefore(cutoff)).count();
        int stability = criticalSignals == 0 ? 10 : criticalSignals <= 2 ? 5 : 0;
        int total = Math.max(0, Math.min(100, slo + evidence + recovery + stability));
        List<String> gaps = new ArrayList<>();
        if (r.totalSamples() == 0) gaps.add("no source-measured reliability evidence");
        if (!rollbackCoverage) gaps.add("no recovery plan with rollback coverage");
        if (criticalSignals > 0) gaps.add("critical operational signals observed in the last 30 days");
        String grade = total >= 90 ? "A" : total >= 80 ? "B" : total >= 65 ? "C" : total >= 50 ? "D" : "E";
        return new ReliabilityScore("SCORED", service.getId(), total, grade, slo, evidence, recovery, stability,
                criticalSignals, r.status(), List.copyOf(gaps), false);
    }

    public record ReliabilityScore(String status, String serviceId, int score, String grade, int sloComponent,
                                   int evidenceComponent, int recoveryComponent, int stabilityComponent,
                                   long criticalSignals30d, String reliabilityStatus, List<String> evidenceGaps,
                                   boolean externalActionAttempted) {}
}
