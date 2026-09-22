package io.aetheris.orchestrator.stage15;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface Stage15RecoveryPlanRepository extends JpaRepository<Stage15RecoveryPlanEntity, UUID> {
    List<Stage15RecoveryPlanEntity> findTop100ByOrderByUpdatedAtDesc();
    List<Stage15RecoveryPlanEntity> findTop100ByIncidentIdOrderByUpdatedAtDesc(UUID incidentId);
    List<Stage15RecoveryPlanEntity> findTop100ByServiceIdOrderByUpdatedAtDesc(String serviceId);
}
