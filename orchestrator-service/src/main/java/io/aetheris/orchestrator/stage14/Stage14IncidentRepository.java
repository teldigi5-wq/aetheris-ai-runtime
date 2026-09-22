package io.aetheris.orchestrator.stage14;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface Stage14IncidentRepository extends JpaRepository<Stage14IncidentEntity, UUID> {
    List<Stage14IncidentEntity> findTop100ByOrderByLastSeenAtDesc();
    List<Stage14IncidentEntity> findTop20ByCorrelationKeyOrderByLastSeenAtDesc(String correlationKey);
}
