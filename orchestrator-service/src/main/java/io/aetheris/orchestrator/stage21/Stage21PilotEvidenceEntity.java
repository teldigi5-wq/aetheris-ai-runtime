package io.aetheris.orchestrator.stage21;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stage21_pilot_evidence")
public class Stage21PilotEvidenceEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID pilotId;
    @Column(nullable = false, length = 64) private String kind;
    @Column(nullable = false, length = 16) private String status;
    @Column(nullable = false) private boolean measuredOnTarget;
    @Column(nullable = false, length = 100) private String source;
    @Column(length = 64) private String subjectSha256;
    @Column(nullable = false, length = 64) private String evidenceSha256;
    @Column(nullable = false) private Instant observedAt;
    @Column(nullable = false) private Instant recordedAt;
    @Version private long version;

    protected Stage21PilotEvidenceEntity() {}

    public Stage21PilotEvidenceEntity(UUID id, UUID pilotId, String kind, String status,
                                      boolean measuredOnTarget, String source, String subjectSha256,
                                      String evidenceSha256, Instant observedAt) {
        this.id = id;
        this.pilotId = pilotId;
        this.kind = kind;
        this.status = status;
        this.measuredOnTarget = measuredOnTarget;
        this.source = source;
        this.subjectSha256 = subjectSha256;
        this.evidenceSha256 = evidenceSha256;
        this.observedAt = observedAt;
        this.recordedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPilotId() { return pilotId; }
    public String getKind() { return kind; }
    public String getStatus() { return status; }
    public boolean isMeasuredOnTarget() { return measuredOnTarget; }
    public String getSource() { return source; }
    public String getSubjectSha256() { return subjectSha256; }
    public String getEvidenceSha256() { return evidenceSha256; }
    public Instant getObservedAt() { return observedAt; }
    public Instant getRecordedAt() { return recordedAt; }
    public long getVersion() { return version; }
}
