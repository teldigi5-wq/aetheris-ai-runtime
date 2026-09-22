package io.aetheris.orchestrator.stage15;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class Stage15ServiceCatalogService {
    public static final Set<String> BOUNDED_ACTIONS = Set.of("RESTART_SERVICE", "PAUSE_PROVIDER", "REVOKE_REMOTE_SESSION",
            "ROLLBACK_RELEASE", "PAUSE_TASK", "STOP_TRADING", "FAILOVER_READ_ONLY_PROVIDER", "CLEAR_BOUNDED_CACHE");
    public static final Set<String> FORBIDDEN_ACTIONS = Set.of("ARBITRARY_SHELL", "ADMIN_BYPASS", "LIVE_ORDER", "WITHDRAWAL", "TRANSFER");
    private static final Set<String> TYPES = Set.of("SERVICE", "PROVIDER", "REMOTE", "WORKSTATION", "TRADING_CONTROL");
    private static final Set<String> CRITICALITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private final Stage15ServiceCatalogRepository repository;

    public Stage15ServiceCatalogService(Stage15ServiceCatalogRepository repository) { this.repository = repository; }

    @Transactional
    public Stage15ServiceCatalogEntity register(ServiceRegistration request) {
        if (request == null) throw new IllegalArgumentException("Service registration is required");
        String id = token(request.id(), "id", 80).toLowerCase(Locale.ROOT);
        String name = text(request.displayName(), "displayName", 160);
        String type = token(request.serviceType(), "serviceType", 32).toUpperCase(Locale.ROOT);
        String criticality = token(request.criticality(), "criticality", 16).toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw new IllegalArgumentException("Unsupported Stage 15 service type");
        if (!CRITICALITIES.contains(criticality)) throw new IllegalArgumentException("Unsupported criticality");
        if (request.sloTargetBasisPoints() < 9000 || request.sloTargetBasisPoints() > 10000)
            throw new IllegalArgumentException("SLO target must be between 90.00% and 100.00%");
        if (request.monthlyErrorBudgetMinutes() < 0 || request.monthlyErrorBudgetMinutes() > 43_200)
            throw new IllegalArgumentException("Monthly error budget minutes are invalid");
        Set<String> actions = normalize(request.allowedActions());
        if (actions.isEmpty()) throw new IllegalArgumentException("At least one bounded action is required");
        if (actions.stream().anyMatch(FORBIDDEN_ACTIONS::contains) || !BOUNDED_ACTIONS.containsAll(actions))
            throw new IllegalArgumentException("Service catalog contains forbidden or unsupported actions");
        Set<String> dependencies = request.dependencies() == null ? Set.of() : request.dependencies().stream()
                .map(v -> token(v, "dependency", 80).toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (dependencies.contains(id)) throw new IllegalArgumentException("A service cannot depend on itself");
        if (repository.existsById(id)) throw new IllegalStateException("Stage 15 service already exists");
        return repository.save(new Stage15ServiceCatalogEntity(id, name, type, criticality,
                request.sloTargetBasisPoints(), request.monthlyErrorBudgetMinutes(), actions, dependencies, request.rollbackRequired()));
    }

    @Transactional
    public Stage15ServiceCatalogEntity scheduleMaintenance(String serviceId, MaintenanceRequest request) {
        Stage15ServiceCatalogEntity service = get(serviceId);
        if (request == null || request.start() == null || request.end() == null) throw new IllegalArgumentException("Maintenance start/end are required");
        if (!request.end().isAfter(request.start())) throw new IllegalArgumentException("Maintenance end must be after start");
        if (Duration.between(request.start(), request.end()).compareTo(Duration.ofDays(7)) > 0)
            throw new IllegalArgumentException("Maintenance window cannot exceed 7 days");
        if (request.start().isBefore(Instant.now().minus(Duration.ofMinutes(5))))
            throw new IllegalArgumentException("Maintenance window cannot be materially backdated");
        service.scheduleMaintenance(request.start(), request.end(), text(request.reason(), "reason", 600));
        return repository.save(service);
    }

    public Stage15ServiceCatalogEntity get(String id) {
        String key = token(id, "serviceId", 80).toLowerCase(Locale.ROOT);
        return repository.findById(key).orElseThrow(() -> new NoSuchElementException("Unknown Stage 15 service"));
    }
    public List<Stage15ServiceCatalogEntity> list() { return repository.findTop200ByOrderByUpdatedAtDesc(); }

    private Set<String> normalize(Set<String> values) {
        if (values == null) return Set.of();
        Set<String> out = new HashSet<>();
        for (String value : values) out.add(token(value, "action", 48).toUpperCase(Locale.ROOT));
        return Set.copyOf(out);
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

    public record ServiceRegistration(String id, String displayName, String serviceType, String criticality,
                                      int sloTargetBasisPoints, int monthlyErrorBudgetMinutes,
                                      Set<String> allowedActions, Set<String> dependencies, boolean rollbackRequired) {}
    public record MaintenanceRequest(Instant start, Instant end, String reason) {}
}
