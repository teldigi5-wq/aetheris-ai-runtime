package io.aetheris.orchestrator.executive;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ExecutiveBriefingRepository extends JpaRepository<ExecutiveBriefingEntity, UUID> {
    List<ExecutiveBriefingEntity> findTop50ByOrderByGeneratedAtDesc();
}
