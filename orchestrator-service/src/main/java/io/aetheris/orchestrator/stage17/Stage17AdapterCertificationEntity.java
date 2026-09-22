package io.aetheris.orchestrator.stage17;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stage17_adapter_certifications")
public class Stage17AdapterCertificationEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 80) private String adapterId;
    @Column(nullable = false) private UUID manifestId;
    @Column(nullable = false) private int protocolVersion;
    @Column(nullable = false, length = 64) private String capabilityPolicySha256;
    @Column(nullable = false, length = 64) private String transportAttestationSha256;
    @Column(nullable = false) private UUID receiptId;
    @Column(nullable = false) private int score;
    @Column(nullable = false, length = 48) private String status;
    @Column(nullable = false) private boolean productionActivationAllowed;
    @Column(nullable = false) private boolean externalActionAttempted;
    @Column(nullable = false) private Instant certifiedAt;
    @Column(nullable = false) private Instant expiresAt;
    @Version private long version;

    protected Stage17AdapterCertificationEntity() {}

    public Stage17AdapterCertificationEntity(UUID id, String adapterId, UUID manifestId, int protocolVersion,
                                             String capabilityPolicySha256, String transportAttestationSha256,
                                             UUID receiptId, int score, Instant expiresAt) {
        this.id = id; this.adapterId = adapterId; this.manifestId = manifestId; this.protocolVersion = protocolVersion;
        this.capabilityPolicySha256 = capabilityPolicySha256; this.transportAttestationSha256 = transportAttestationSha256;
        this.receiptId = receiptId; this.score = score; this.status = "CERTIFIED_SIMULATION_ONLY";
        this.productionActivationAllowed = false; this.externalActionAttempted = false;
        this.certifiedAt = Instant.now(); this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public String getAdapterId() { return adapterId; }
    public UUID getManifestId() { return manifestId; }
    public int getProtocolVersion() { return protocolVersion; }
    public String getCapabilityPolicySha256() { return capabilityPolicySha256; }
    public String getTransportAttestationSha256() { return transportAttestationSha256; }
    public UUID getReceiptId() { return receiptId; }
    public int getScore() { return score; }
    public String getStatus() { return status; }
    public boolean isProductionActivationAllowed() { return productionActivationAllowed; }
    public boolean isExternalActionAttempted() { return externalActionAttempted; }
    public Instant getCertifiedAt() { return certifiedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public long getVersion() { return version; }
}
