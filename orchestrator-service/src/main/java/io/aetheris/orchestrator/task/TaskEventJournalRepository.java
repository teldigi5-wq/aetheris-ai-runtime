package io.aetheris.orchestrator.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskEventJournalRepository extends JpaRepository<TaskEventJournalEntity, UUID> {
    List<TaskEventJournalEntity> findTop200ByTaskIdOrderByEventAtDesc(UUID taskId);
}
