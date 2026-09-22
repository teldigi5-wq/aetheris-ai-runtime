package io.aetheris.orchestrator.stage11;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aetheris_stage11_remote_bindings")
public class Stage11RemoteBindingEntity {
    @Id private UUID sessionId;
    @Column(nullable = false, length = 64) private String certificateSha256;
    @Column(nullable = false) private Instant createdAt;

    protected Stage11RemoteBindingEntity() {}

    public Stage11RemoteBindingEntity(UUID sessionId, String certificateSha256) {
        this.sessionId = sessionId;
        this.certificateSha256 = certificateSha256;
        this.createdAt = Instant.now();
    }

    public UUID getSessionId() { return sessionId; }
    public String getCertificateSha256() { return certificateSha256; }
    public Instant getCreatedAt() { return createdAt; }
}
