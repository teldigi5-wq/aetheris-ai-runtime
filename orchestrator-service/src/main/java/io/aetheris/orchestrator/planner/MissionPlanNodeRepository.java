package io.aetheris.orchestrator.planner;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface MissionPlanNodeRepository extends JpaRepository<MissionPlanNodeEntity,UUID>{List<MissionPlanNodeEntity> findByMissionIdOrderByPriorityDescCreatedAtAsc(UUID missionId);}
