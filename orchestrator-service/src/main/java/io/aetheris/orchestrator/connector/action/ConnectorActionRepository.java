package io.aetheris.orchestrator.connector.action;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConnectorActionRepository extends JpaRepository<ConnectorActionEntity, UUID> {
    Optional<ConnectorActionEntity> findByConnectionIdAndIdempotencyKey(UUID connectionId, String idempotencyKey);
    List<ConnectorActionEntity> findTop100ByOrderByCreatedAtDesc();
}
