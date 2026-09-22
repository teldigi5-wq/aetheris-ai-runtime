package io.aetheris.orchestrator.stage19;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "stage19_activation_proposals",
        uniqueConstraints = @UniqueConstraint(name = "uk_stage19_proposal_sha", columnNames = "proposalSha256"))
public class Stage19ActivationProposalEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 80) private String targetId;
    @Column(nullable = false, length = 80) private String adapterId;
    @Column(nullable = false, length = 64) private String manifestSha256;
    @Column(nullable = false, length = 64) private String evidenceBundleSha256;
    @Column(nullable = false, length = 64) private String packageSha256;
    @Column(nullable = false, length = 64) private String deviceCertificateSha256;
    @Column(nullable = false, length = 1200) private String canaryCapabilitiesCsv;
    @Column(nullable = false, length = 64) private String proposalSha256;
    @Column(nullable = false, length = 64) private String status;
    @Column(nullable = false) private boolean ownerApproved;
    @Column(length = 40) private String approvedBy;
    private Instant approvalExpiresAt;
    @Column(length = 64) private String refreshedAttestationSha256;
    private Instant attestationRefreshedAt;
    @Column(nullable = false) private boolean simulationOnly;
    @Column(nullable = false) private boolean productionActivationAllowed;
    @Column(nullable = false) private boolean targetMutated;
    @Column(nullable = false) private boolean externalActionAttempted;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected Stage19ActivationProposalEntity() {}

    public Stage19ActivationProposalEntity(UUID id, String targetId, String adapterId,
                                           String manifestSha256, String evidenceBundleSha256,
                                           String packageSha256, String deviceCertificateSha256,
                                           Set<String> canaryCapabilities, String proposalSha256,
                                           String status) {
        this.id = id; this.targetId = targetId; this.adapterId = adapterId;
        this.manifestSha256 = manifestSha256; this.evidenceBundleSha256 = evidenceBundleSha256;
        this.packageSha256 = packageSha256; this.deviceCertificateSha256 = deviceCertificateSha256;
        this.canaryCapabilitiesCsv = csv(canaryCapabilities); this.proposalSha256 = proposalSha256;
        this.status = status; this.ownerApproved = false; this.simulationOnly = true;
        this.productionActivationAllowed = false; this.targetMutated = false;
        this.externalActionAttempted = false; this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }

    public void approve(String approver, Instant expiresAt) {
        this.ownerApproved = true; this.approvedBy = approver; this.approvalExpiresAt = expiresAt;
        this.status = "OWNER_APPROVED_SIMULATION_ONLY"; this.updatedAt = Instant.now();
    }
    public void startCanary() { this.status = "CANARY_ACTIVE_SIMULATION"; this.updatedAt = Instant.now(); }
    public void markValidated() { this.status = "CANARY_VALIDATED_SIMULATION_ONLY"; this.updatedAt = Instant.now(); }
    public void revoke() { this.status = "REVOKED_SIMULATION"; this.updatedAt = Instant.now(); }
    public void rehearseRollback() { this.status = "ROLLBACK_REHEARSED_SIMULATION"; this.updatedAt = Instant.now(); }
    public void refreshAttestation(String sha256) {
        this.refreshedAttestationSha256 = sha256; this.attestationRefreshedAt = Instant.now(); this.updatedAt = this.attestationRefreshedAt;
    }

    private static String csv(Set<String> values) {
        return values == null ? "" : values.stream().sorted().collect(Collectors.joining(","));
    }
    private static Set<String> set(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(",")).filter(v -> !v.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    public UUID getId() { return id; }
    public String getTargetId() { return targetId; }
    public String getAdapterId() { return adapterId; }
    public String getManifestSha256() { return manifestSha256; }
    public String getEvidenceBundleSha256() { return evidenceBundleSha256; }
    public String getPackageSha256() { return packageSha256; }
    public String getDeviceCertificateSha256() { return deviceCertificateSha256; }
    public Set<String> getCanaryCapabilities() { return set(canaryCapabilitiesCsv); }
    public String getProposalSha256() { return proposalSha256; }
    public String getStatus() { return status; }
    public boolean isOwnerApproved() { return ownerApproved; }
    public String getApprovedBy() { return approvedBy; }
    public Instant getApprovalExpiresAt() { return approvalExpiresAt; }
    public String getRefreshedAttestationSha256() { return refreshedAttestationSha256; }
    public Instant getAttestationRefreshedAt() { return attestationRefreshedAt; }
    public boolean isSimulationOnly() { return simulationOnly; }
    public boolean isProductionActivationAllowed() { return productionActivationAllowed; }
    public boolean isTargetMutated() { return targetMutated; }
    public boolean isExternalActionAttempted() { return externalActionAttempted; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
