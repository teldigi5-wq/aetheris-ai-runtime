package io.aetheris.orchestrator.stage19;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface Stage19CanaryObservationRepository extends JpaRepository<Stage19CanaryObservationEntity, UUID> {
    List<Stage19CanaryObservationEntity> findTop200ByOrderByObservedAtDesc();
    List<Stage19CanaryObservationEntity> findTop100ByProposalIdOrderByObservedAtDesc(UUID proposalId);
}
