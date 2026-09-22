package io.aetheris.orchestrator.stage14;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface Stage14SelfHealingProposalRepository extends JpaRepository<Stage14SelfHealingProposalEntity, UUID> {
    List<Stage14SelfHealingProposalEntity> findTop100ByOrderByUpdatedAtDesc();
    List<Stage14SelfHealingProposalEntity> findTop100ByIncidentIdOrderByUpdatedAtDesc(UUID incidentId);
}
