package io.aetheris.orchestrator.stage20;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stage20_target_receipts",
        uniqueConstraints = @UniqueConstraint(name = "uk_stage20_receipt_sha", columnNames = "receiptSha256"))
public class Stage20TargetReceiptEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID authorizationId;
    @Column(nullable = false) private UUID leaseId;
    @Column(nullable = false, length = 80) private String targetId;
    @Column(nullable = false, length = 64) private String leaseSha256;
    @Column(nullable = false, length = 64) private String packageSha256;
    @Column(nullable = false, length = 64) private String deviceCertificateSha256;
    @Column(nullable = false, length = 64) private String targetAttestationSha256;
    @Column(nullable = false, length = 48) private String resultCode;
    @Column(nullable = false, length = 64) private String receiptSha256;
    @Column(nullable = false, length = 64) private String signatureSha256;
    @Column(nullable = false, length = 64) private String status;
    @Column(nullable = false) private boolean measuredOnTarget;
    @Column(nullable = false) private boolean simulationOnly;
    @Column(nullable = false) private boolean productionActivationAllowed;
    @Column(nullable = false) private boolean targetMutated;
    @Column(nullable = false) private boolean externalActionAttempted;
    @Column(nullable = false) private Instant observedAt;
    @Column(nullable = false) private Instant recordedAt;
    @Version private long version;

    protected Stage20TargetReceiptEntity() {}

    public Stage20TargetReceiptEntity(UUID id, UUID authorizationId, UUID leaseId, String targetId,
                                      String leaseSha256, String packageSha256, String deviceCertificateSha256,
                                      String targetAttestationSha256, String resultCode, String receiptSha256,
                                      String signatureSha256, String status, boolean measuredOnTarget,
                                      Instant observedAt) {
        this.id = id; this.authorizationId = authorizationId; this.leaseId = leaseId; this.targetId = targetId;
        this.leaseSha256 = leaseSha256; this.packageSha256 = packageSha256;
        this.deviceCertificateSha256 = deviceCertificateSha256; this.targetAttestationSha256 = targetAttestationSha256;
        this.resultCode = resultCode; this.receiptSha256 = receiptSha256; this.signatureSha256 = signatureSha256;
        this.status = status; this.measuredOnTarget = measuredOnTarget; this.simulationOnly = true;
        this.productionActivationAllowed = false; this.targetMutated = false; this.externalActionAttempted = false;
        this.observedAt = observedAt; this.recordedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAuthorizationId() { return authorizationId; }
    public UUID getLeaseId() { return leaseId; }
    public String getTargetId() { return targetId; }
    public String getLeaseSha256() { return leaseSha256; }
    public String getPackageSha256() { return packageSha256; }
    public String getDeviceCertificateSha256() { return deviceCertificateSha256; }
    public String getTargetAttestationSha256() { return targetAttestationSha256; }
    public String getResultCode() { return resultCode; }
    public String getReceiptSha256() { return receiptSha256; }
    public String getSignatureSha256() { return signatureSha256; }
    public String getStatus() { return status; }
    public boolean isMeasuredOnTarget() { return measuredOnTarget; }
    public boolean isSimulationOnly() { return simulationOnly; }
    public boolean isProductionActivationAllowed() { return productionActivationAllowed; }
    public boolean isTargetMutated() { return targetMutated; }
    public boolean isExternalActionAttempted() { return externalActionAttempted; }
    public Instant getObservedAt() { return observedAt; }
    public Instant getRecordedAt() { return recordedAt; }
    public long getVersion() { return version; }
}
