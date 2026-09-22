package io.aetheris.orchestrator.stage14;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stage14_incidents")
public class Stage14IncidentEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 180)
    private String correlationKey;
    @Column(nullable = false, length = 240)
    private String title;
    @Column(nullable = false, length = 16)
    private String severity;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(nullable = false)
    private int signalCount;
    @Column(nullable = false)
    private Instant firstSeenAt;
    @Column(nullable = false)
    private Instant lastSeenAt;
    private Instant acknowledgedAt;
    private Instant resolvedAt;
    @Column(length = 1200)
    private String acknowledgementNote;
    @Column(length = 1200)
    private String resolutionNote;
    @Version
    private long version;

    protected Stage14IncidentEntity() {}

    public Stage14IncidentEntity(UUID id, String correlationKey, String title, String severity, Instant observedAt) {
        this.id = id;
        this.correlationKey = correlationKey;
        this.title = title;
        this.severity = severity;
        this.status = "OPEN";
        this.signalCount = 1;
        this.firstSeenAt = observedAt;
        this.lastSeenAt = observedAt;
    }

    public void absorb(String incomingSeverity, Instant observedAt) {
        signalCount++;
        if (observedAt.isAfter(lastSeenAt)) lastSeenAt = observedAt;
        if (rank(incomingSeverity) > rank(severity)) severity = incomingSeverity;
    }

    public void acknowledge(String note) {
        if ("RESOLVED".equals(status)) throw new IllegalStateException("Resolved incident cannot be acknowledged");
        status = "ACKNOWLEDGED";
        acknowledgedAt = Instant.now();
        acknowledgementNote = note;
    }

    public void markMitigationProposed() {
        if (!"RESOLVED".equals(status)) status = "MITIGATION_PROPOSED";
    }

    public void resolve(String note) {
        status = "RESOLVED";
        resolvedAt = Instant.now();
        resolutionNote = note;
    }

    private int rank(String value) {
        return switch (value) { case "CRITICAL" -> 3; case "WARN" -> 2; default -> 1; };
    }

    public UUID getId() { return id; }
    public String getCorrelationKey() { return correlationKey; }
    public String getTitle() { return title; }
    public String getSeverity() { return severity; }
    public String getStatus() { return status; }
    public int getSignalCount() { return signalCount; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getAcknowledgementNote() { return acknowledgementNote; }
    public String getResolutionNote() { return resolutionNote; }
    public long getVersion() { return version; }
}
