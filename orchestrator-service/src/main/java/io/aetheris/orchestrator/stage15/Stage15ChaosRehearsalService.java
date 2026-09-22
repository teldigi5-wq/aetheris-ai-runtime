package io.aetheris.orchestrator.stage15;

import io.aetheris.orchestrator.stage14.Stage14ChangeImpactService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class Stage15ChaosRehearsalService {
    private static final Set<String> FAULTS = Set.of("DEPENDENCY_UNAVAILABLE", "HIGH_LATENCY", "SERVICE_CRASH_SIMULATION",
            "PROVIDER_DEGRADED", "DISK_PRESSURE_SIMULATION");
    private final Stage15ServiceCatalogService catalog;
    private final Stage14ChangeImpactService impact;

    public Stage15ChaosRehearsalService(Stage15ServiceCatalogService catalog, Stage14ChangeImpactService impact) {
        this.catalog = catalog; this.impact = impact;
    }

    public RehearsalResult rehearse(RehearsalRequest request) {
        if (request == null) throw new IllegalArgumentException("Chaos rehearsal request is required");
        Stage15ServiceCatalogEntity target = catalog.get(request.serviceId());
        String fault = token(request.faultType(), "faultType", 48).toUpperCase(Locale.ROOT);
        if (!FAULTS.contains(fault)) throw new IllegalArgumentException("Unsupported or unsafe chaos fault type");
        if (!request.isolatedSimulation()) throw new IllegalArgumentException("Stage 15 chaos rehearsal must be isolated simulation only");
        String proof = sha(request.rehearsalSha256());

        Map<String, Set<String>> graph = new HashMap<>();
        Set<String> critical = new HashSet<>();
        for (Stage15ServiceCatalogEntity service : catalog.list()) {
            graph.put(service.getId(), service.getDependencies());
            if (Set.of("HIGH", "CRITICAL").contains(service.getCriticality())) critical.add(service.getId());
        }
        var result = impact.assess(new Stage14ChangeImpactService.ChangeImpactRequest(Set.of(target.getId()), graph, critical));
        String status = "BLOCKED".equals(result.status()) ? "REHEARSAL_BLOCKED_DEPENDENCY_CYCLE" : "REHEARSAL_COMPLETED_SIMULATION_ONLY";
        return new RehearsalResult(status, target.getId(), fault, result.affectedComponents(), result.criticalAffected(),
                result.riskLevel(), proof, true, false, false);
    }

    private String token(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String v = value.trim();
        if (v.length() > max || !v.matches("[A-Za-z0-9._:/-]{1," + max + "}")) throw new IllegalArgumentException(label + " is invalid");
        return v;
    }
    private String sha(String value) {
        if (value == null || !value.trim().toLowerCase(Locale.ROOT).matches("[a-f0-9]{64}")) throw new IllegalArgumentException("rehearsalSha256 must be a SHA-256 value");
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record RehearsalRequest(String serviceId, String faultType, boolean isolatedSimulation, String rehearsalSha256) {}
    public record RehearsalResult(String status, String serviceId, String faultType, Set<String> affectedServices,
                                  Set<String> criticalAffected, String riskLevel, String rehearsalSha256,
                                  boolean simulationOnly, boolean productionAffected, boolean externalActionAttempted) {}
}
