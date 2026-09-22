package io.aetheris.orchestrator.connector.adapter;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "connector_adapter_configs",
        uniqueConstraints = @UniqueConstraint(name = "uk_connector_adapter_connection", columnNames = "connection_id")
)
public class ConnectorAdapterConfigEntity {
    @Id
    private UUID id;

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private ConnectorAdapterType adapterType;

    @Column(name = "secret_reference", nullable = false, length = 96)
    private String secretReference;

    @Column(name = "max_clock_skew_seconds", nullable = false)
    private int maxClockSkewSeconds;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ConnectorAdapterConfigEntity() {}

    public ConnectorAdapterConfigEntity(UUID id, UUID connectionId, ConnectorAdapterType adapterType,
                                        String secretReference, int maxClockSkewSeconds) {
        Instant now = Instant.now();
        this.id = id;
        this.connectionId = connectionId;
        this.adapterType = adapterType;
        this.secretReference = secretReference;
        this.maxClockSkewSeconds = maxClockSkewSeconds;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void reconfigure(ConnectorAdapterType adapterType, String secretReference, int maxClockSkewSeconds) {
        this.adapterType = adapterType;
        this.secretReference = secretReference;
        this.maxClockSkewSeconds = maxClockSkewSeconds;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getConnectionId() { return connectionId; }
    public ConnectorAdapterType getAdapterType() { return adapterType; }
    public String getSecretReference() { return secretReference; }
    public int getMaxClockSkewSeconds() { return maxClockSkewSeconds; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
