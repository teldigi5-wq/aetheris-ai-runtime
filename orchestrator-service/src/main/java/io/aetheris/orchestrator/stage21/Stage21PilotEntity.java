package io.aetheris.orchestrator.stage21;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "stage21_physical_pilots",
        uniqueConstraints = @UniqueConstraint(name = "uk_stage21_pilot_sha", columnNames = "pilotSha256"))
public class Stage21PilotEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID stage20AuthorizationId;
    @Column(nullable = false, length = 80) private String targetId;
    @Column(nullable = false, length = 80) private String adapterId;
    @Column(nullable = false, length = 64) private String stage20AuthorizationSha256;
    @Column(nullable = false, length = 64) private String packageSha256;
    @Column(nullable = false, length = 64) private String deviceCertificateSha256;
    @Column(nullable = false, length = 1200) private String capabilitiesCsv;
    @Column(nullable = false, length = 64) private String pilotSha256;
    @Column(nullable = false, length = 64) private String status;
    @Column(nullable = false) private boolean hardwareRequired;
    @Column(nullable = false) private boolean physicalPilotComplete;
    @Column(nullable = false) private boolean productionActivationAllowed;
    @Column(nullable = false) private boolean targetMutated;
    @Column(nullable = false) private boolean externalActionAttempted;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected Stage21PilotEntity() {}

    public Stage21PilotEntity(UUID id, UUID stage20AuthorizationId, String targetId, String adapterId,
                              String stage20AuthorizationSha256, String packageSha256,
                              String deviceCertificateSha256, Set<String> capabilities,
                              String pilotSha256) {
        this.id = id;
        this.stage20AuthorizationId = stage20AuthorizationId;
        this.targetId = targetId;
        this.adapterId = adapterId;
        this.stage20AuthorizationSha256 = stage20AuthorizationSha256;
        this.packageSha256 = packageSha256;
        this.deviceCertificateSha256 = deviceCertificateSha256;
        this.capabilitiesCsv = csv(capabilities);
        this.pilotSha256 = pilotSha256;
        this.status = "BLOCKED_PENDING_HARDWARE";
        this.hardwareRequired = true;
        this.physicalPilotComplete = false;
        this.productionActivationAllowed = false;
        this.targetMutated = false;
        this.externalActionAttempted = false;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    private static String csv(Set<String> values) {
        return values == null ? "" : values.stream().sorted().collect(Collectors.joining(","));
    }
    private static Set<String> set(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(",")).filter(v -> !v.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    public UUID getId() { return id; }
    public UUID getStage20AuthorizationId() { return stage20AuthorizationId; }
    public String getTargetId() { return targetId; }
    public String getAdapterId() { return adapterId; }
    public String getStage20AuthorizationSha256() { return stage20AuthorizationSha256; }
    public String getPackageSha256() { return packageSha256; }
    public String getDeviceCertificateSha256() { return deviceCertificateSha256; }
    public Set<String> getCapabilities() { return set(capabilitiesCsv); }
    public String getPilotSha256() { return pilotSha256; }
    public String getStatus() { return status; }
    public boolean isHardwareRequired() { return hardwareRequired; }
    public boolean isPhysicalPilotComplete() { return physicalPilotComplete; }
    public boolean isProductionActivationAllowed() { return productionActivationAllowed; }
    public boolean isTargetMutated() { return targetMutated; }
    public boolean isExternalActionAttempted() { return externalActionAttempted; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
