package io.aetheris.orchestrator.connector.oauth;

import io.aetheris.orchestrator.connector.ConnectorProvider;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "connector_provider_credentials", uniqueConstraints = @UniqueConstraint(
        name = "uk_connector_provider_credential_connection", columnNames = "connection_id"))
public class ProviderCredentialEntity {
    @Id
    private UUID id;

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ConnectorProvider provider;

    @Column(name = "access_token_reference", length = 180)
    private String accessTokenReference;

    @Column(name = "refresh_token_reference", length = 180)
    private String refreshTokenReference;

    @Column(name = "scopes_csv", nullable = false, length = 2000)
    private String scopesCsv;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ProviderCredentialStatus status;

    private Instant expiresAt;
    private Instant lastValidatedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ProviderCredentialEntity() {}

    public ProviderCredentialEntity(UUID id, UUID connectionId, ConnectorProvider provider,
                                    String accessTokenReference, String refreshTokenReference,
                                    String scopesCsv, Instant expiresAt) {
        Instant now = Instant.now();
        this.id = id;
        this.connectionId = connectionId;
        this.provider = provider;
        this.accessTokenReference = accessTokenReference;
        this.refreshTokenReference = refreshTokenReference;
        this.scopesCsv = scopesCsv;
        this.status = ProviderCredentialStatus.ACTIVE;
        this.expiresAt = expiresAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void rotate(String accessTokenReference, String refreshTokenReference,
                       String scopesCsv, Instant expiresAt) {
        this.accessTokenReference = accessTokenReference;
        if (refreshTokenReference != null && !refreshTokenReference.isBlank()) {
            this.refreshTokenReference = refreshTokenReference;
        }
        this.scopesCsv = scopesCsv;
        this.expiresAt = expiresAt;
        this.status = ProviderCredentialStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void validated(boolean healthy) {
        this.lastValidatedAt = Instant.now();
        if (!healthy) this.status = ProviderCredentialStatus.ERROR;
        else if (this.status != ProviderCredentialStatus.REVOKED) this.status = ProviderCredentialStatus.ACTIVE;
        this.updatedAt = this.lastValidatedAt;
    }

    public void revoke() {
        this.accessTokenReference = null;
        this.refreshTokenReference = null;
        this.status = ProviderCredentialStatus.REVOKED;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getConnectionId() { return connectionId; }
    public ConnectorProvider getProvider() { return provider; }
    public String getAccessTokenReference() { return accessTokenReference; }
    public String getRefreshTokenReference() { return refreshTokenReference; }
    public String getScopesCsv() { return scopesCsv; }
    public ProviderCredentialStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getLastValidatedAt() { return lastValidatedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
