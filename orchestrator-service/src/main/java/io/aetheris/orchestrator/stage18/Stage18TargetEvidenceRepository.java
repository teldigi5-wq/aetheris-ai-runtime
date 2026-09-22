package io.aetheris.orchestrator.stage18;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface Stage18TargetEvidenceRepository extends JpaRepository<Stage18TargetEvidenceEntity, UUID> {
    List<Stage18TargetEvidenceEntity> findTop200ByOrderByObservedAtDesc();
    List<Stage18TargetEvidenceEntity> findTop200ByTargetIdOrderByObservedAtDesc(String targetId);
}
