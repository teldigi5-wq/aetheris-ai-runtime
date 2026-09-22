package io.aetheris.orchestrator.mission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface MissionMessageRepository extends JpaRepository<MissionMessageEntity,UUID>{List<MissionMessageEntity> findTop200ByMissionIdOrderByCreatedAtAsc(UUID missionId);}
