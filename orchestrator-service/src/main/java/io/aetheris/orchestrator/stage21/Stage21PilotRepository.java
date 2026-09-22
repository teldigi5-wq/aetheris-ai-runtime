package io.aetheris.orchestrator.stage21;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface Stage21PilotRepository extends JpaRepository<Stage21PilotEntity, UUID> {
    boolean existsByPilotSha256(String pilotSha256);
    List<Stage21PilotEntity> findTop100ByOrderByCreatedAtDesc();
}
