package io.aetheris.orchestrator.stage12;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aetheris_stage12_providers")
public class Stage12ProviderEntity {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 80) private String providerId;
    @Column(nullable = false, length = 32) private String type;
    @Column(nullable = false, length = 180) private String endpoint;
    @Column(nullable = false, length = 120) private String credentialAlias;
    @Column(nullable = false) private boolean enabled;
    @Column(nullable = false, length = 40) private String status;
    @Column(nullable = false, length = 512) private String capabilitiesCsv;
    @Column(nullable = false) private boolean readOnly;
    @Column(nullable = false) private Instant updatedAt;

    protected Stage12ProviderEntity() {}

    public Stage12ProviderEntity(UUID id, String providerId, String type, String endpoint, String credentialAlias,
                                 boolean enabled, String status, String capabilitiesCsv, boolean readOnly) {
        this.id = id;
        this.providerId = providerId;
        this.type = type;
        this.endpoint = endpoint;
        this.credentialAlias = credentialAlias;
        this.enabled = enabled;
        this.status = status;
        this.capabilitiesCsv = capabilitiesCsv;
        this.readOnly = readOnly;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getProviderId() { return providerId; }
    public String getType() { return type; }
    public String getEndpoint() { return endpoint; }
    public String getCredentialAlias() { return credentialAlias; }
    public boolean isEnabled() { return enabled; }
    public String getStatus() { return status; }
    public String getCapabilitiesCsv() { return capabilitiesCsv; }
    public boolean isReadOnly() { return readOnly; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String endpoint, String credentialAlias, boolean enabled, String status,
                       String capabilitiesCsv, boolean readOnly) {
        this.endpoint = endpoint;
        this.credentialAlias = credentialAlias;
        this.enabled = enabled;
        this.status = status;
        this.capabilitiesCsv = capabilitiesCsv;
        this.readOnly = readOnly;
        this.updatedAt = Instant.now();
    }
}
