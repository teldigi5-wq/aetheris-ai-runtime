package io.aetheris.orchestrator.connector;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConnectorEventReceiptRepository extends JpaRepository<ConnectorEventReceiptEntity, UUID> {
    Optional<ConnectorEventReceiptEntity> findByConnectionIdAndExternalEventId(
            UUID connectionId, String externalEventId);

    List<ConnectorEventReceiptEntity> findTop100ByOrderByReceivedAtDesc();
}
