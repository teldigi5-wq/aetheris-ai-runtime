package io.aetheris.orchestrator.stage21;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface Stage21PilotEvidenceRepository extends JpaRepository<Stage21PilotEvidenceEntity, UUID> {
    List<Stage21PilotEvidenceEntity> findTop200ByOrderByObservedAtDesc();
    List<Stage21PilotEvidenceEntity> findTop100ByPilotIdOrderByObservedAtDesc(UUID pilotId);
}
