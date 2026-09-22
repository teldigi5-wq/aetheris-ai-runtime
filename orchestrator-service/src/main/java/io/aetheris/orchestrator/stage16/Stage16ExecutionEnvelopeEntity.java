package io.aetheris.orchestrator.stage16;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stage16_execution_envelopes", uniqueConstraints = @UniqueConstraint(name = "uk_stage16_nonce", columnNames = "nonce"))
public class Stage16ExecutionEnvelopeEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID planId;
    @Column(nullable = false, length = 80) private String adapterId;
    @Column(nullable = false, length = 48) private String action;
    @Column(nullable = false, length = 80) private String target;
    @Column(nullable = false, length = 120) private String nonce;
    @Column(nullable = false, length = 64) private String planSha256;
    @Column(nullable = false, length = 80) private String signerKeyId;
    @Column(nullable = false, length = 64) private String signatureSha256;
    @Column(nullable = false) private Instant issuedAt;
    @Column(nullable = false) private Instant expiresAt;
    @Column(nullable = false, length = 56) private String status;
    @Column(length = 600) private String cancellationReason;
    @Column(length = 80) private String outcome;
    @Column(length = 64) private String receiptSha256;
    @Column(nullable = false) private boolean externalActionAttempted;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected Stage16ExecutionEnvelopeEntity() {}

    public Stage16ExecutionEnvelopeEntity(UUID id, UUID planId, String adapterId, String action, String target,
                                          String nonce, String planSha256, String signerKeyId,
                                          String signatureSha256, Instant issuedAt, Instant expiresAt) {
        this.id = id; this.planId = planId; this.adapterId = adapterId; this.action = action; this.target = target;
        this.nonce = nonce; this.planSha256 = planSha256; this.signerKeyId = signerKeyId;
        this.signatureSha256 = signatureSha256; this.issuedAt = issuedAt; this.expiresAt = expiresAt;
        this.status = "ADMITTED"; this.externalActionAttempted = false;
        this.createdAt = Instant.now(); this.updatedAt = this.createdAt;
    }

    public void cancel(String reason) {
        if (!"ADMITTED".equals(status)) throw new IllegalStateException("Only admitted Stage 16 envelopes can be cancelled");
        this.status = "CANCELLED"; this.cancellationReason = reason; this.updatedAt = Instant.now();
    }
    public void complete(String outcome, String receiptSha256) {
        if (!"ADMITTED".equals(status)) throw new IllegalStateException("Only admitted Stage 16 envelopes can execute");
        this.status = "SIMULATION_COMPLETED"; this.outcome = outcome; this.receiptSha256 = receiptSha256;
        this.externalActionAttempted = false; this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPlanId() { return planId; }
    public String getAdapterId() { return adapterId; }
    public String getAction() { return action; }
    public String getTarget() { return target; }
    public String getNonce() { return nonce; }
    public String getPlanSha256() { return planSha256; }
    public String getSignerKeyId() { return signerKeyId; }
    public String getSignatureSha256() { return signatureSha256; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getStatus() { return status; }
    public String getCancellationReason() { return cancellationReason; }
    public String getOutcome() { return outcome; }
    public String getReceiptSha256() { return receiptSha256; }
    public boolean isExternalActionAttempted() { return externalActionAttempted; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
