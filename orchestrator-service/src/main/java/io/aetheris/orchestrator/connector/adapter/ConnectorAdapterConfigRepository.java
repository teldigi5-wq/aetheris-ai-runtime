package io.aetheris.orchestrator.connector.adapter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConnectorAdapterConfigRepository extends JpaRepository<ConnectorAdapterConfigEntity, UUID> {
    Optional<ConnectorAdapterConfigEntity> findByConnectionId(UUID connectionId);
}
