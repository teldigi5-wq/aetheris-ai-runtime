package io.aetheris.orchestrator.scheduler;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface WorkerHeartbeatRepository extends JpaRepository<WorkerHeartbeatEntity,String>{List<WorkerHeartbeatEntity> findTop100ByOrderByUpdatedAtDesc();}
