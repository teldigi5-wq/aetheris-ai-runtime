package io.aetheris.orchestrator.stage16;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface Stage16AdapterRepository extends JpaRepository<Stage16AdapterEntity, String> {
    List<Stage16AdapterEntity> findTop100ByOrderByUpdatedAtDesc();
}
