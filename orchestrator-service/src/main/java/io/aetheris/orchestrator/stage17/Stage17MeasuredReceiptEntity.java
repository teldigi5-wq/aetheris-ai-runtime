package io.aetheris.orchestrator.stage17;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stage17_measured_receipts",
        uniqueConstraints = @UniqueConstraint(name = "uk_stage17_receipt_envelope", columnNames = "envelopeId"))
public class Stage17MeasuredReceiptEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 80) private String adapterId;
    @Column(nullable = false) private UUID envelopeId;
    @Column(nullable = false, length = 32) private String schemaVersion;
    @Column(nullable = false, length = 80) private String outcome;
    @Column(nullable = false, length = 64) private String receiptSha256;
    @Column(nullable = false, length = 64) private String attestationSha256;
    @Column(nullable = false) private boolean sourceMeasured;
    @Column(nullable = false) private boolean simulationOnly;
    @Column(nullable = false) private boolean targetMutated;
    @Column(nullable = false) private boolean externalActionAttempted;
    @Column(nullable = false) private Instant observedAt;
    @Column(nullable = false) private Instant createdAt;
    @Version private long version;

    protected Stage17MeasuredReceiptEntity() {}

    public Stage17MeasuredReceiptEntity(UUID id, String adapterId, UUID envelopeId, String schemaVersion,
                                        String outcome, String receiptSha256, String attestationSha256,
                                        boolean sourceMeasured, boolean simulationOnly, boolean targetMutated,
                                        boolean externalActionAttempted, Instant observedAt) {
        this.id = id; this.adapterId = adapterId; this.envelopeId = envelopeId; this.schemaVersion = schemaVersion;
        this.outcome = outcome; this.receiptSha256 = receiptSha256; this.attestationSha256 = attestationSha256;
        this.sourceMeasured = sourceMeasured; this.simulationOnly = simulationOnly; this.targetMutated = targetMutated;
        this.externalActionAttempted = externalActionAttempted; this.observedAt = observedAt; this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getAdapterId() { return adapterId; }
    public UUID getEnvelopeId() { return envelopeId; }
    public String getSchemaVersion() { return schemaVersion; }
    public String getOutcome() { return outcome; }
    public String getReceiptSha256() { return receiptSha256; }
    public String getAttestationSha256() { return attestationSha256; }
    public boolean isSourceMeasured() { return sourceMeasured; }
    public boolean isSimulationOnly() { return simulationOnly; }
    public boolean isTargetMutated() { return targetMutated; }
    public boolean isExternalActionAttempted() { return externalActionAttempted; }
    public Instant getObservedAt() { return observedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
