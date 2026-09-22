package io.aetheris.orchestrator.stage14;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface Stage14OperationalSignalRepository extends JpaRepository<Stage14OperationalSignalEntity, UUID> {
    List<Stage14OperationalSignalEntity> findTop100ByOrderByObservedAtDesc();
    List<Stage14OperationalSignalEntity> findTop100ByIncidentIdOrderByObservedAtAsc(UUID incidentId);
}
