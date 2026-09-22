package io.aetheris.orchestrator.stage17;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface Stage17QueuedCommandRepository extends JpaRepository<Stage17QueuedCommandEntity, UUID> {
    boolean existsByEnvelopeId(UUID envelopeId);
    List<Stage17QueuedCommandEntity> findTop100ByOrderByQueuedAtDesc();
    List<Stage17QueuedCommandEntity> findTop100ByAdapterIdOrderByQueuedAtAsc(String adapterId);
}
