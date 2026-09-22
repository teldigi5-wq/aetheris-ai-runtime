package io.aetheris.orchestrator.stage13;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aetheris_stage13_provider_health_evidence")
public class Stage13ProviderHealthEvidenceEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 80) private String providerId;
    @Column(nullable = false, length = 24) private String health;
    @Column(nullable = false) private long latencyMs;
    @Column(nullable = false, length = 64) private String attestationSha256;
    @Column(nullable = false) private Instant observedAt;
    @Column(nullable = false) private Instant recordedAt;

    protected Stage13ProviderHealthEvidenceEntity() {}

    public Stage13ProviderHealthEvidenceEntity(UUID id, String providerId, String health, long latencyMs,
                                               String attestationSha256, Instant observedAt) {
        this.id = id;
        this.providerId = providerId;
        this.health = health;
        this.latencyMs = latencyMs;
        this.attestationSha256 = attestationSha256;
        this.observedAt = observedAt;
        this.recordedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getProviderId() { return providerId; }
    public String getHealth() { return health; }
    public long getLatencyMs() { return latencyMs; }
    public String getAttestationSha256() { return attestationSha256; }
    public Instant getObservedAt() { return observedAt; }
    public Instant getRecordedAt() { return recordedAt; }
}
