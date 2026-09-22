package io.aetheris.orchestrator.stage20;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "stage20_activation_leases",
        uniqueConstraints = @UniqueConstraint(name = "uk_stage20_lease_sha", columnNames = "leaseSha256"))
public class Stage20ActivationLeaseEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID authorizationId;
    @Column(nullable = false, length = 80) private String targetId;
    @Column(nullable = false, length = 1200) private String capabilitiesCsv;
    @Column(nullable = false, length = 64) private String leaseSha256;
    @Column(nullable = false, length = 64) private String lastAttestationSha256;
    @Column(length = 64) private String lastRenewalSha256;
    @Column(nullable = false, length = 64) private String status;
    @Column(nullable = false) private Instant startedAt;
    @Column(nullable = false) private Instant expiresAt;
    @Column(nullable = false) private int renewalCount;
    @Column(length = 64) private String revocationReasonSha256;
    @Column(nullable = false) private boolean simulationOnly;
    @Column(nullable = false) private boolean productionActivationAllowed;
    @Column(nullable = false) private boolean targetMutated;
    @Column(nullable = false) private boolean externalActionAttempted;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected Stage20ActivationLeaseEntity() {}

    public Stage20ActivationLeaseEntity(UUID id, UUID authorizationId, String targetId, Set<String> capabilities,
                                        String leaseSha256, String attestationSha256, Instant expiresAt) {
        this.id = id; this.authorizationId = authorizationId; this.targetId = targetId;
        this.capabilitiesCsv = csv(capabilities); this.leaseSha256 = leaseSha256;
        this.lastAttestationSha256 = attestationSha256; this.status = "LEASE_ACTIVE_SIMULATION_ONLY";
        this.startedAt = Instant.now(); this.expiresAt = expiresAt; this.renewalCount = 0;
        this.simulationOnly = true; this.productionActivationAllowed = false; this.targetMutated = false;
        this.externalActionAttempted = false; this.updatedAt = this.startedAt;
    }

    public void renew(Instant newExpiry, String attestationSha256, String renewalSha256) {
        this.expiresAt = newExpiry; this.lastAttestationSha256 = attestationSha256;
        this.lastRenewalSha256 = renewalSha256; this.renewalCount++;
        this.status = "LEASE_RENEWED_SIMULATION_ONLY"; this.updatedAt = Instant.now();
    }
    public void revoke(String reasonSha256) {
        this.revocationReasonSha256 = reasonSha256; this.status = "LEASE_REVOKED_SIMULATION_ONLY";
        this.updatedAt = Instant.now();
    }

    private static String csv(Set<String> values) {
        return values == null ? "" : values.stream().sorted().collect(Collectors.joining(","));
    }
    private static Set<String> set(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(",")).filter(v -> !v.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    public UUID getId() { return id; }
    public UUID getAuthorizationId() { return authorizationId; }
    public String getTargetId() { return targetId; }
    public Set<String> getCapabilities() { return set(capabilitiesCsv); }
    public String getLeaseSha256() { return leaseSha256; }
    public String getLastAttestationSha256() { return lastAttestationSha256; }
    public String getLastRenewalSha256() { return lastRenewalSha256; }
    public String getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getRenewalCount() { return renewalCount; }
    public String getRevocationReasonSha256() { return revocationReasonSha256; }
    public boolean isSimulationOnly() { return simulationOnly; }
    public boolean isProductionActivationAllowed() { return productionActivationAllowed; }
    public boolean isTargetMutated() { return targetMutated; }
    public boolean isExternalActionAttempted() { return externalActionAttempted; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
