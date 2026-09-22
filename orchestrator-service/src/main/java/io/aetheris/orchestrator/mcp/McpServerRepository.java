package io.aetheris.orchestrator.mcp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface McpServerRepository extends JpaRepository<McpServerEntity, UUID> {
    Optional<McpServerEntity> findByServerKey(String serverKey);
    List<McpServerEntity> findTop100ByOrderByDisplayNameAsc();
}
