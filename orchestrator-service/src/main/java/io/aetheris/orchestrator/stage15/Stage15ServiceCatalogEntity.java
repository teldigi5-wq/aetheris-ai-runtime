package io.aetheris.orchestrator.stage15;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "stage15_service_catalog")
public class Stage15ServiceCatalogEntity {
    @Id
    @Column(length = 80)
    private String id;
    @Column(nullable = false, length = 160)
    private String displayName;
    @Column(nullable = false, length = 32)
    private String serviceType;
    @Column(nullable = false, length = 16)
    private String criticality;
    @Column(nullable = false)
    private int sloTargetBasisPoints;
    @Column(nullable = false)
    private int monthlyErrorBudgetMinutes;
    @Column(nullable = false, length = 800)
    private String allowedActionsCsv;
    @Column(nullable = false, length = 1600)
    private String dependenciesCsv;
    @Column(nullable = false)
    private boolean rollbackRequired;
    private Instant maintenanceStart;
    private Instant maintenanceEnd;
    @Column(length = 600)
    private String maintenanceReason;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Stage15ServiceCatalogEntity() {}

    public Stage15ServiceCatalogEntity(String id, String displayName, String serviceType, String criticality,
                                       int sloTargetBasisPoints, int monthlyErrorBudgetMinutes,
                                       Set<String> allowedActions, Set<String> dependencies, boolean rollbackRequired) {
        this.id = id;
        this.displayName = displayName;
        this.serviceType = serviceType;
        this.criticality = criticality;
        this.sloTargetBasisPoints = sloTargetBasisPoints;
        this.monthlyErrorBudgetMinutes = monthlyErrorBudgetMinutes;
        this.allowedActionsCsv = csv(allowedActions);
        this.dependenciesCsv = csv(dependencies);
        this.rollbackRequired = rollbackRequired;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void scheduleMaintenance(Instant start, Instant end, String reason) {
        this.maintenanceStart = start;
        this.maintenanceEnd = end;
        this.maintenanceReason = reason;
        this.updatedAt = Instant.now();
    }

    private static String csv(Set<String> values) {
        if (values == null || values.isEmpty()) return "";
        return values.stream().sorted().collect(Collectors.joining(","));
    }
    private static Set<String> set(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(",")).filter(v -> !v.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getServiceType() { return serviceType; }
    public String getCriticality() { return criticality; }
    public int getSloTargetBasisPoints() { return sloTargetBasisPoints; }
    public int getMonthlyErrorBudgetMinutes() { return monthlyErrorBudgetMinutes; }
    public Set<String> getAllowedActions() { return set(allowedActionsCsv); }
    public Set<String> getDependencies() { return set(dependenciesCsv); }
    public boolean isRollbackRequired() { return rollbackRequired; }
    public Instant getMaintenanceStart() { return maintenanceStart; }
    public Instant getMaintenanceEnd() { return maintenanceEnd; }
    public String getMaintenanceReason() { return maintenanceReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public boolean isMaintenanceActive(Instant now) {
        return maintenanceStart != null && maintenanceEnd != null && !now.isBefore(maintenanceStart) && now.isBefore(maintenanceEnd);
    }
}
