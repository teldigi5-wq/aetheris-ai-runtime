package io.aetheris.orchestrator.connector;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConnectorConnectionRepository extends JpaRepository<ConnectorConnectionEntity, UUID> {
    Optional<ConnectorConnectionEntity> findByProviderAndExternalAccountRef(
            ConnectorProvider provider, String externalAccountRef);

    List<ConnectorConnectionEntity> findTop100ByOrderByUpdatedAtDesc();
}
