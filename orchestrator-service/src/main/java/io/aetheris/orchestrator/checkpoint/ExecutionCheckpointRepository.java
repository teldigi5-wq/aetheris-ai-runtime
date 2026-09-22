package io.aetheris.orchestrator.checkpoint;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ExecutionCheckpointRepository extends JpaRepository<ExecutionCheckpointEntity, UUID> {
    List<ExecutionCheckpointEntity> findTop100ByTaskIdOrderByCreatedAtDesc(UUID taskId);
}
