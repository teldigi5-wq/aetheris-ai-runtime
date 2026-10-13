package io.aetheris.orchestrator.mission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface MissionSessionRepository extends JpaRepository<MissionSessionEntity,UUID>{List<MissionSessionEntity> findTop100ByOrderByUpdatedAtDesc();
List<MissionSessionEntity> findTop100ByOwnerUserIdOrderByUpdatedAtDesc(UUID ownerUserId); List<MissionSessionEntity> findTop100ByOwnerUserIdAndOwnerProjectIdOrderByUpdatedAtDesc(UUID ownerUserId, UUID ownerProjectId); List<MissionSessionEntity> findTop100ByOwnerUserIdAndOwnerProjectIdIsNullOrderByUpdatedAtDesc(UUID ownerUserId);}
