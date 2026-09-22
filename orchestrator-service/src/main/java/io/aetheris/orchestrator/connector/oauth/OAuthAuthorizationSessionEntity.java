package io.aetheris.orchestrator.connector.oauth;

import io.aetheris.orchestrator.connector.ConnectorProvider;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "connector_oauth_sessions", indexes = {
        @Index(name = "idx_connector_oauth_connection", columnList = "connection_id"),
        @Index(name = "idx_connector_oauth_state_hash", columnList = "state_hash")
})
public class OAuthAuthorizationSessionEntity {
    @Id
    private UUID id;

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ConnectorProvider provider;

    @Column(name = "state_hash", nullable = false, length = 64)
    private String stateHash;

    @Column(name = "pkce_reference", nullable = false, length = 180)
    private String pkceReference;

    @Column(name = "redirect_uri", nullable = false, length = 900)
    private String redirectUri;

    @Column(name = "scopes_csv", nullable = false, length = 2000)
    private String scopesCsv;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OAuthSessionStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant completedAt;

    protected OAuthAuthorizationSessionEntity() {}

    public OAuthAuthorizationSessionEntity(UUID id, UUID connectionId, ConnectorProvider provider,
                                           String stateHash, String pkceReference, String redirectUri,
                                           String scopesCsv, Instant expiresAt) {
        this.id = id;
        this.connectionId = connectionId;
        this.provider = provider;
        this.stateHash = stateHash;
        this.pkceReference = pkceReference;
        this.redirectUri = redirectUri;
        this.scopesCsv = scopesCsv;
        this.status = OAuthSessionStatus.PENDING;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public void complete() {
        this.status = OAuthSessionStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void expire() { this.status = OAuthSessionStatus.EXPIRED; }
    public void fail() { this.status = OAuthSessionStatus.FAILED; }

    public UUID getId() { return id; }
    public UUID getConnectionId() { return connectionId; }
    public ConnectorProvider getProvider() { return provider; }
    public String getStateHash() { return stateHash; }
    public String getPkceReference() { return pkceReference; }
    public String getRedirectUri() { return redirectUri; }
    public String getScopesCsv() { return scopesCsv; }
    public OAuthSessionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCompletedAt() { return completedAt; }
}
