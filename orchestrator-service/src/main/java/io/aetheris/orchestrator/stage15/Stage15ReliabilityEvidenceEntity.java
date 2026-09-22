package io.aetheris.orchestrator.stage15;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stage15_reliability_evidence")
public class Stage15ReliabilityEvidenceEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 80) private String serviceId;
    @Column(nullable = false) private long totalSamples;
    @Column(nullable = false) private long failedSamples;
    @Column(nullable = false, length = 64) private String attestationSha256;
    @Column(nullable = false) private boolean sourceMeasured;
    @Column(nullable = false) private Instant observedAt;
    @Column(nullable = false) private Instant createdAt;

    protected Stage15ReliabilityEvidenceEntity() {}
    public Stage15ReliabilityEvidenceEntity(UUID id, String serviceId, long totalSamples, long failedSamples,
                                            String attestationSha256, boolean sourceMeasured, Instant observedAt) {
        this.id = id; this.serviceId = serviceId; this.totalSamples = totalSamples; this.failedSamples = failedSamples;
        this.attestationSha256 = attestationSha256; this.sourceMeasured = sourceMeasured; this.observedAt = observedAt;
        this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public String getServiceId() { return serviceId; }
    public long getTotalSamples() { return totalSamples; }
    public long getFailedSamples() { return failedSamples; }
    public String getAttestationSha256() { return attestationSha256; }
    public boolean isSourceMeasured() { return sourceMeasured; }
    public Instant getObservedAt() { return observedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
