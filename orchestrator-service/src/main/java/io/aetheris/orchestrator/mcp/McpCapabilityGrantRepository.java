package io.aetheris.orchestrator.mcp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface McpCapabilityGrantRepository extends JpaRepository<McpCapabilityGrantEntity, UUID> {
    List<McpCapabilityGrantEntity> findTop200ByServerIdOrderByCreatedAtDesc(UUID serverId);
    Optional<McpCapabilityGrantEntity> findTopByServerIdAndAgentIdAndCapabilityAndDataClassAndEnabledTrueOrderByCreatedAtDesc(
            UUID serverId, String agentId, String capability, String dataClass);
}
