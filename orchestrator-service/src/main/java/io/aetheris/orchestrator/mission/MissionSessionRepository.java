package io.aetheris.orchestrator.mission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface MissionSessionRepository extends JpaRepository<MissionSessionEntity,UUID>{List<MissionSessionEntity> findTop100ByOrderByUpdatedAtDesc();}
