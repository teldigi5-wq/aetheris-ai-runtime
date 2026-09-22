package io.aetheris.orchestrator.stage19;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stage19_canary_observations")
public class Stage19CanaryObservationEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID proposalId;
    @Column(nullable = false, length = 80) private String targetId;
    @Column(nullable = false) private int windowSeconds;
    @Column(nullable = false) private double availabilityPct;
    @Column(nullable = false) private double errorRatePct;
    @Column(nullable = false) private double p95LatencyMs;
    @Column(nullable = false) private int crashCount;
    @Column(nullable = false, length = 40) private String status;
    @Column(nullable = false) private boolean measuredOnTarget;
    @Column(nullable = false, length = 80) private String source;
    @Column(nullable = false, length = 64) private String attestationSha256;
    @Column(nullable = false) private Instant observedAt;
    @Column(nullable = false) private boolean externalActionAttempted;
    @Version private long version;

    protected Stage19CanaryObservationEntity() {}

    public Stage19CanaryObservationEntity(UUID id, UUID proposalId, String targetId, int windowSeconds,
                                          double availabilityPct, double errorRatePct, double p95LatencyMs,
                                          int crashCount, String status, boolean measuredOnTarget,
                                          String source, String attestationSha256, Instant observedAt) {
        this.id = id; this.proposalId = proposalId; this.targetId = targetId; this.windowSeconds = windowSeconds;
        this.availabilityPct = availabilityPct; this.errorRatePct = errorRatePct; this.p95LatencyMs = p95LatencyMs;
        this.crashCount = crashCount; this.status = status; this.measuredOnTarget = measuredOnTarget;
        this.source = source; this.attestationSha256 = attestationSha256; this.observedAt = observedAt;
        this.externalActionAttempted = false;
    }

    public UUID getId() { return id; }
    public UUID getProposalId() { return proposalId; }
    public String getTargetId() { return targetId; }
    public int getWindowSeconds() { return windowSeconds; }
    public double getAvailabilityPct() { return availabilityPct; }
    public double getErrorRatePct() { return errorRatePct; }
    public double getP95LatencyMs() { return p95LatencyMs; }
    public int getCrashCount() { return crashCount; }
    public String getStatus() { return status; }
    public boolean isMeasuredOnTarget() { return measuredOnTarget; }
    public String getSource() { return source; }
    public String getAttestationSha256() { return attestationSha256; }
    public Instant getObservedAt() { return observedAt; }
    public boolean isExternalActionAttempted() { return externalActionAttempted; }
    public long getVersion() { return version; }
}
