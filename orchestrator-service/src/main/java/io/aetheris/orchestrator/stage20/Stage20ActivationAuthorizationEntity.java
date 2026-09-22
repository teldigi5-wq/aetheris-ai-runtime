package io.aetheris.orchestrator.stage20;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "stage20_activation_authorizations",
        uniqueConstraints = @UniqueConstraint(name = "uk_stage20_authorization_sha", columnNames = "authorizationSha256"))
public class Stage20ActivationAuthorizationEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID stage19ProposalId;
    @Column(nullable = false, length = 80) private String targetId;
    @Column(nullable = false, length = 80) private String adapterId;
    @Column(nullable = false, length = 64) private String stage19ProposalSha256;
    @Column(nullable = false, length = 64) private String stage19AttestationSha256;
    @Column(nullable = false, length = 64) private String stage18BundleSha256;
    @Column(nullable = false, length = 64) private String manifestSha256;
    @Column(nullable = false, length = 64) private String packageSha256;
    @Column(nullable = false, length = 64) private String deviceCertificateSha256;
    @Column(nullable = false, length = 80) private String ownerSignerKeyId;
    @Column(nullable = false, length = 80) private String deviceSignerKeyId;
    @Column(nullable = false, length = 1200) private String capabilitiesCsv;
    @Column(nullable = false, length = 64) private String authorizationSha256;
    @Column(nullable = false, length = 64) private String signatureSha256;
    @Column(nullable = false) private Instant issuedAt;
    @Column(nullable = false) private Instant expiresAt;
    @Column(nullable = false) private Instant maintenanceStart;
    @Column(nullable = false) private Instant maintenanceEnd;
    @Column(nullable = false) private int maxLeaseMinutes;
    @Column(nullable = false, length = 64) private String status;
    @Column(nullable = false) private boolean emergencyStopEngaged;
    @Column(length = 64) private String interlockReasonSha256;
    @Column(nullable = false) private boolean simulationOnly;
    @Column(nullable = false) private boolean productionActivationAllowed;
    @Column(nullable = false) private boolean targetMutated;
    @Column(nullable = false) private boolean externalActionAttempted;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected Stage20ActivationAuthorizationEntity() {}

    public Stage20ActivationAuthorizationEntity(UUID id, UUID stage19ProposalId, String targetId, String adapterId,
                                                 String stage19ProposalSha256, String stage19AttestationSha256,
                                                 String stage18BundleSha256, String manifestSha256,
                                                 String packageSha256, String deviceCertificateSha256,
                                                 String ownerSignerKeyId, String deviceSignerKeyId,
                                                 Set<String> capabilities, String authorizationSha256,
                                                 String signatureSha256, Instant issuedAt, Instant expiresAt,
                                                 Instant maintenanceStart, Instant maintenanceEnd, int maxLeaseMinutes) {
        this.id = id; this.stage19ProposalId = stage19ProposalId; this.targetId = targetId; this.adapterId = adapterId;
        this.stage19ProposalSha256 = stage19ProposalSha256; this.stage19AttestationSha256 = stage19AttestationSha256;
        this.stage18BundleSha256 = stage18BundleSha256; this.manifestSha256 = manifestSha256;
        this.packageSha256 = packageSha256; this.deviceCertificateSha256 = deviceCertificateSha256;
        this.ownerSignerKeyId = ownerSignerKeyId; this.deviceSignerKeyId = deviceSignerKeyId;
        this.capabilitiesCsv = csv(capabilities); this.authorizationSha256 = authorizationSha256;
        this.signatureSha256 = signatureSha256; this.issuedAt = issuedAt; this.expiresAt = expiresAt;
        this.maintenanceStart = maintenanceStart; this.maintenanceEnd = maintenanceEnd; this.maxLeaseMinutes = maxLeaseMinutes;
        this.status = "AUTHORIZED_SIMULATION_ONLY"; this.emergencyStopEngaged = false; this.simulationOnly = true;
        this.productionActivationAllowed = false; this.targetMutated = false; this.externalActionAttempted = false;
        this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }

    public void engageEmergencyStop(String reasonSha256) {
        this.emergencyStopEngaged = true; this.interlockReasonSha256 = reasonSha256;
        this.status = "EMERGENCY_STOPPED_SIMULATION"; this.updatedAt = Instant.now();
    }
    public void clearEmergencyStop(String reasonSha256) {
        this.emergencyStopEngaged = false; this.interlockReasonSha256 = reasonSha256;
        this.status = "AUTHORIZED_SIMULATION_ONLY"; this.updatedAt = Instant.now();
    }
    public void revokeForDrift(String reasonSha256) {
        this.emergencyStopEngaged = true; this.interlockReasonSha256 = reasonSha256;
        this.status = "REVOKED_ATTESTATION_DRIFT_SIMULATION"; this.updatedAt = Instant.now();
    }

    private static String csv(Set<String> values) {
        return values == null ? "" : values.stream().sorted().collect(Collectors.joining(","));
    }
    private static Set<String> set(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(",")).filter(v -> !v.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    public UUID getId() { return id; }
    public UUID getStage19ProposalId() { return stage19ProposalId; }
    public String getTargetId() { return targetId; }
    public String getAdapterId() { return adapterId; }
    public String getStage19ProposalSha256() { return stage19ProposalSha256; }
    public String getStage19AttestationSha256() { return stage19AttestationSha256; }
    public String getStage18BundleSha256() { return stage18BundleSha256; }
    public String getManifestSha256() { return manifestSha256; }
    public String getPackageSha256() { return packageSha256; }
    public String getDeviceCertificateSha256() { return deviceCertificateSha256; }
    public String getOwnerSignerKeyId() { return ownerSignerKeyId; }
    public String getDeviceSignerKeyId() { return deviceSignerKeyId; }
    public Set<String> getCapabilities() { return set(capabilitiesCsv); }
    public String getAuthorizationSha256() { return authorizationSha256; }
    public String getSignatureSha256() { return signatureSha256; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getMaintenanceStart() { return maintenanceStart; }
    public Instant getMaintenanceEnd() { return maintenanceEnd; }
    public int getMaxLeaseMinutes() { return maxLeaseMinutes; }
    public String getStatus() { return status; }
    public boolean isEmergencyStopEngaged() { return emergencyStopEngaged; }
    public String getInterlockReasonSha256() { return interlockReasonSha256; }
    public boolean isSimulationOnly() { return simulationOnly; }
    public boolean isProductionActivationAllowed() { return productionActivationAllowed; }
    public boolean isTargetMutated() { return targetMutated; }
    public boolean isExternalActionAttempted() { return externalActionAttempted; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
