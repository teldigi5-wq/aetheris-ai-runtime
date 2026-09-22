package io.aetheris.orchestrator.runtime;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WorkItemRepository extends JpaRepository<WorkItemEntity, UUID> {
    List<WorkItemEntity> findTop100ByOrderByUpdatedAtDesc();
    List<WorkItemEntity> findTop100ByStateInOrderByCreatedAtAsc(Collection<WorkItemState> states);
    List<WorkItemEntity> findTop100ByTaskIdOrderByCreatedAtDesc(UUID taskId);
}
