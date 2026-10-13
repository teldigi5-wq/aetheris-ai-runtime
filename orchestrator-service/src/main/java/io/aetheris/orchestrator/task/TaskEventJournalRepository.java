package io.aetheris.orchestrator.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

public interface TaskEventJournalRepository extends JpaRepository<TaskEventJournalEntity, UUID> {
    List<TaskEventJournalEntity> findTop200ByTaskIdOrderByEventAtDesc(UUID taskId);

    // New *projection-only* queries: serialized event message/metadata never
    // leave the database via this read path.
    @Query("""
        select new io.aetheris.orchestrator.task.TaskJournalCursorRow(
            e.id, e.taskId, e.eventAt, e.state, e.agentId)
        from TaskEventJournalEntity e
        where e.taskId = :taskId
        order by e.eventAt asc, e.id asc
        """)
    List<TaskJournalCursorRow> firstCursorPage(
            @Param("taskId") UUID taskId, Pageable page);

    @Query("""
        select new io.aetheris.orchestrator.task.TaskJournalCursorRow(
            e.id, e.taskId, e.eventAt, e.state, e.agentId)
        from TaskEventJournalEntity e
        where e.taskId = :taskId
          and (e.eventAt > :afterTime
               or (e.eventAt = :afterTime and e.id > :afterId))
        order by e.eventAt asc, e.id asc
        """)
    List<TaskJournalCursorRow> cursorPageAfter(
            @Param("taskId") UUID taskId,
            @Param("afterTime") Instant afterTime,
            @Param("afterId") UUID afterId,
            Pageable page);

    @Query("""
        select new io.aetheris.orchestrator.task.TaskJournalCursorRow(
            e.id, e.taskId, e.eventAt, e.state, e.agentId)
        from TaskEventJournalEntity e
        where e.id = :eventId and e.taskId = :taskId
        """)
    Optional<TaskJournalCursorRow> cursorForTask(
            @Param("eventId") UUID eventId, @Param("taskId") UUID taskId);

}
