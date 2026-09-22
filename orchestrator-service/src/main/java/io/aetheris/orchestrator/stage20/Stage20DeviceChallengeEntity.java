package io.aetheris.orchestrator.stage20;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stage20_device_challenges",
        uniqueConstraints = @UniqueConstraint(name = "uk_stage20_challenge_nonce_sha", columnNames = "nonceSha256"))
public class Stage20DeviceChallengeEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID authorizationId;
    @Column(nullable = false, length = 80) private String targetId;
    @Column(nullable = false, length = 64) private String nonceSha256;
    @Column(nullable = false, length = 64) private String status;
    @Column(nullable = false) private Instant issuedAt;
    @Column(nullable = false) private Instant expiresAt;
    @Column(length = 64) private String attestationSha256;
    private Instant verifiedAt;
    private Instant consumedAt;
    @Column(nullable = false) private boolean simulationOnly;
    @Column(nullable = false) private boolean productionActivationAllowed;
    @Version private long version;

    protected Stage20DeviceChallengeEntity() {}

    public Stage20DeviceChallengeEntity(UUID id, UUID authorizationId, String targetId,
                                        String nonceSha256, Instant expiresAt) {
        this.id = id; this.authorizationId = authorizationId; this.targetId = targetId;
        this.nonceSha256 = nonceSha256; this.status = "PENDING_DEVICE_ATTESTATION";
        this.issuedAt = Instant.now(); this.expiresAt = expiresAt;
        this.simulationOnly = true; this.productionActivationAllowed = false;
    }

    public void verify(String attestationSha256) {
        this.attestationSha256 = attestationSha256; this.verifiedAt = Instant.now();
        this.status = "DEVICE_ATTESTATION_VERIFIED_SIMULATION_ONLY";
    }
    public void consume() {
        if (this.consumedAt != null) throw new IllegalStateException("Stage 20 hardware challenge was already consumed");
        this.consumedAt = Instant.now(); this.status = "DEVICE_ATTESTATION_CONSUMED_SIMULATION_ONLY";
    }

    public UUID getId() { return id; }
    public UUID getAuthorizationId() { return authorizationId; }
    public String getTargetId() { return targetId; }
    public String getNonceSha256() { return nonceSha256; }
    public String getStatus() { return status; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getAttestationSha256() { return attestationSha256; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public boolean isSimulationOnly() { return simulationOnly; }
    public boolean isProductionActivationAllowed() { return productionActivationAllowed; }
    public long getVersion() { return version; }
}
