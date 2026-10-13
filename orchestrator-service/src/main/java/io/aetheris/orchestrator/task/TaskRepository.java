package io.aetheris.orchestrator.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {
    List<TaskEntity> findTop50ByOrderByUpdatedAtDesc();
    List<TaskEntity> findTop50ByOwnerUserIdOrderByUpdatedAtDesc(Long ownerUserId);
    List<TaskEntity> findTop50ByOwnerUserIdAndOwnerProjectIdOrderByUpdatedAtDesc(Long ownerUserId, UUID ownerProjectId);
    List<TaskEntity> findTop50ByOwnerUserIdAndOwnerProjectIdIsNullOrderByUpdatedAtDesc(Long ownerUserId);
    List<TaskEntity> findByStateIn(Collection<TaskState> states);
}
