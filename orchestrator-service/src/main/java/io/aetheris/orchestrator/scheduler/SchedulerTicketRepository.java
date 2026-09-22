package io.aetheris.orchestrator.scheduler;
import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import jakarta.persistence.LockModeType;import java.util.*;
public interface SchedulerTicketRepository extends JpaRepository<SchedulerTicketEntity,UUID>{
    Optional<SchedulerTicketEntity> findByWorkItemId(UUID workItemId);
    List<SchedulerTicketEntity> findTop200ByStateOrderByPriorityDescCreatedAtAsc(SchedulerTicketState state);
    long countByState(SchedulerTicketState state);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from SchedulerTicketEntity t where t.state=:state order by t.priority desc,t.createdAt asc") List<SchedulerTicketEntity> findQueuedForUpdate(@Param("state") SchedulerTicketState state);
}
