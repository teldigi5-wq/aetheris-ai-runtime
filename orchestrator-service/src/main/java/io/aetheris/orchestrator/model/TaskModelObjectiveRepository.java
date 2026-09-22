package io.aetheris.orchestrator.model;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface TaskModelObjectiveRepository extends JpaRepository<TaskModelObjectiveEntity,UUID>{}
