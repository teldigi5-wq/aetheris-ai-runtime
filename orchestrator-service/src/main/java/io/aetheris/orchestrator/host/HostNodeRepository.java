package io.aetheris.orchestrator.host;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HostNodeRepository extends JpaRepository<HostNodeEntity, UUID> {
    Optional<HostNodeEntity> findByHostKey(String hostKey);
    List<HostNodeEntity> findTop100ByOrderByCreatedAtDesc();
}
