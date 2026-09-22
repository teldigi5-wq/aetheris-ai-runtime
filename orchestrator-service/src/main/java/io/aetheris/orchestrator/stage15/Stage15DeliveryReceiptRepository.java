package io.aetheris.orchestrator.stage15;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface Stage15DeliveryReceiptRepository extends JpaRepository<Stage15DeliveryReceiptEntity, UUID> {
    List<Stage15DeliveryReceiptEntity> findTop100ByOrderByObservedAtDesc();
    List<Stage15DeliveryReceiptEntity> findTop100ByIncidentIdOrderByObservedAtDesc(UUID incidentId);
}
