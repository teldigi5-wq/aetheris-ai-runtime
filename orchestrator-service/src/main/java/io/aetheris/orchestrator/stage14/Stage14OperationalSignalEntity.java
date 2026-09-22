package io.aetheris.orchestrator.stage14;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stage14_operational_signals")
public class Stage14OperationalSignalEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 32)
    private String sourceType;
    @Column(nullable = false, length = 100)
    private String sourceId;
    @Column(nullable = false, length = 64)
    private String signalType;
    @Column(nullable = false, length = 16)
    private String severity;
    @Column(nullable = false, length = 64)
    private String fingerprint;
    @Column(nullable = false, length = 1200)
    private String summary;
    @Column(length = 64)
    private String attestationSha256;
    @Column(nullable = false)
    private boolean sourceMeasured;
    @Column(nullable = false)
    private Instant observedAt;
    @Column(nullable = false)
    private Instant createdAt;
    private UUID incidentId;

    protected Stage14OperationalSignalEntity() {}

    public Stage14OperationalSignalEntity(UUID id, String sourceType, String sourceId, String signalType,
                                          String severity, String fingerprint, String summary,
                                          String attestationSha256, boolean sourceMeasured, Instant observedAt) {
        this.id = id;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.signalType = signalType;
        this.severity = severity;
        this.fingerprint = fingerprint;
        this.summary = summary;
        this.attestationSha256 = attestationSha256;
        this.sourceMeasured = sourceMeasured;
        this.observedAt = observedAt;
        this.createdAt = Instant.now();
    }

    public void attachIncident(UUID incidentId) { this.incidentId = incidentId; }
    public UUID getId() { return id; }
    public String getSourceType() { return sourceType; }
    public String getSourceId() { return sourceId; }
    public String getSignalType() { return signalType; }
    public String getSeverity() { return severity; }
    public String getFingerprint() { return fingerprint; }
    public String getSummary() { return summary; }
    public String getAttestationSha256() { return attestationSha256; }
    public boolean isSourceMeasured() { return sourceMeasured; }
    public Instant getObservedAt() { return observedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getIncidentId() { return incidentId; }
}
