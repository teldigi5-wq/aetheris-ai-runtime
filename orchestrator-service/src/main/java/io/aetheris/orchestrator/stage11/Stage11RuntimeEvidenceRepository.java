package io.aetheris.orchestrator.stage11;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface Stage11RuntimeEvidenceRepository extends JpaRepository<Stage11RuntimeEvidenceEntity, UUID> {
    List<Stage11RuntimeEvidenceEntity> findTop200ByOrderByObservedAtDesc();
}
