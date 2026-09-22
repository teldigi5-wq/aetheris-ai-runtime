package io.aetheris.orchestrator.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aetheris_provider_usage")
public class ProviderUsageEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String providerId;

    private UUID taskId;

    @Column(nullable = false)
    private long units;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal estimatedCostUsd;

    @Column(nullable = false)
    private Instant createdAt;

    protected ProviderUsageEntity() {
    }

    public ProviderUsageEntity(UUID id, String providerId, UUID taskId, long units, BigDecimal estimatedCostUsd) {
        this.id = id;
        this.providerId = providerId;
        this.taskId = taskId;
        this.units = units;
        this.estimatedCostUsd = estimatedCostUsd;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getProviderId() { return providerId; }
    public UUID getTaskId() { return taskId; }
    public long getUnits() { return units; }
    public BigDecimal getEstimatedCostUsd() { return estimatedCostUsd; }
    public Instant getCreatedAt() { return createdAt; }
}
