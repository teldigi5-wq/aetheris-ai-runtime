package io.aetheris.orchestrator.stage18;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stage18_target_evidence")
public class Stage18TargetEvidenceEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 80) private String targetId;
    @Column(nullable = false, length = 48) private String kind;
    @Column(nullable = false, length = 100) private String component;
    @Column(nullable = false, length = 32) private String status;
    @Column(nullable = false) private boolean measuredOnTarget;
    @Column(length = 64) private String subjectSha256;
    @Column(nullable = false, length = 64) private String attestationSha256;
    @Column(nullable = false, length = 80) private String source;
    @Column(nullable = false, length = 2000) private String detail;
    @Column(nullable = false) private Instant observedAt;
    @Column(nullable = false) private Instant createdAt;
    @Version private long version;

    protected Stage18TargetEvidenceEntity() {}

    public Stage18TargetEvidenceEntity(UUID id, String targetId, String kind, String component, String status,
                                       boolean measuredOnTarget, String subjectSha256, String attestationSha256,
                                       String source, String detail, Instant observedAt) {
        this.id = id; this.targetId = targetId; this.kind = kind; this.component = component;
        this.status = status; this.measuredOnTarget = measuredOnTarget; this.subjectSha256 = subjectSha256;
        this.attestationSha256 = attestationSha256; this.source = source; this.detail = detail;
        this.observedAt = observedAt; this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getTargetId() { return targetId; }
    public String getKind() { return kind; }
    public String getComponent() { return component; }
    public String getStatus() { return status; }
    public boolean isMeasuredOnTarget() { return measuredOnTarget; }
    public String getSubjectSha256() { return subjectSha256; }
    public String getAttestationSha256() { return attestationSha256; }
    public String getSource() { return source; }
    public String getDetail() { return detail; }
    public Instant getObservedAt() { return observedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
