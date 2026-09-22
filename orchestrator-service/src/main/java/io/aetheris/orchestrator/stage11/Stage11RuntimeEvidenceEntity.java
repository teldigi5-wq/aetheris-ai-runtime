package io.aetheris.orchestrator.stage11;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aetheris_stage11_runtime_evidence")
public class Stage11RuntimeEvidenceEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 32) private String kind;
    @Column(nullable = false, length = 48) private String status;
    @Column(nullable = false, length = 80) private String source;
    @Column(nullable = false) private boolean targetMeasured;
    @Column(length = 64) private String attestationSha256;
    @Column(nullable = false, length = 2000) private String detail;
    @Column(nullable = false) private Instant observedAt;

    protected Stage11RuntimeEvidenceEntity() {}

    public Stage11RuntimeEvidenceEntity(UUID id, String kind, String status, String source, boolean targetMeasured,
                                        String attestationSha256, String detail) {
        this.id = id;
        this.kind = kind;
        this.status = status;
        this.source = source;
        this.targetMeasured = targetMeasured;
        this.attestationSha256 = attestationSha256;
        this.detail = detail;
        this.observedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getKind() { return kind; }
    public String getStatus() { return status; }
    public String getSource() { return source; }
    public boolean isTargetMeasured() { return targetMeasured; }
    public String getAttestationSha256() { return attestationSha256; }
    public String getDetail() { return detail; }
    public Instant getObservedAt() { return observedAt; }
}
