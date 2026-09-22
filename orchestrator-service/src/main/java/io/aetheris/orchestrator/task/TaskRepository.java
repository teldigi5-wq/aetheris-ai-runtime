package io.aetheris.orchestrator.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {
    List<TaskEntity> findTop50ByOrderByUpdatedAtDesc();
    List<TaskEntity> findByStateIn(Collection<TaskState> states);
}
