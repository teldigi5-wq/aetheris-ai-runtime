package io.aetheris.orchestrator.mcp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aetheris_mcp_capability_grants")
public class McpCapabilityGrantEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID serverId;

    @Column(nullable = false, length = 120)
    private String agentId;

    @Column(nullable = false, length = 200)
    private String capability;

    @Column(nullable = false, length = 120)
    private String dataClass;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private Instant createdAt;

    protected McpCapabilityGrantEntity() {
    }

    public McpCapabilityGrantEntity(UUID id, UUID serverId, String agentId, String capability, String dataClass) {
        this.id = id;
        this.serverId = serverId;
        this.agentId = agentId;
        this.capability = capability;
        this.dataClass = dataClass;
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getServerId() { return serverId; }
    public String getAgentId() { return agentId; }
    public String getCapability() { return capability; }
    public String getDataClass() { return dataClass; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }

    public void revoke() { this.enabled = false; }
}
