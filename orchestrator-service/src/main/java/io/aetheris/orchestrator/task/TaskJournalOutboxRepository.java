package io.aetheris.orchestrator.task;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskJournalOutboxRepository extends JpaRepository<TaskJournalOutboxEntity, UUID> {
    Optional<TaskJournalOutboxEntity> findByJournalEventId(UUID journalEventId);

    /** Pessimistic DB row lock; claim happens inside its own transaction.
     * The at-most-one-page selection prevents a large unbounded scan. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select o from TaskJournalOutboxEntity o
        where (o.state = :pending and o.nextAttemptAt <= :now)
           or (o.state = :inFlight and o.leaseUntil <= :now)
        order by o.createdAt asc, o.id asc
        """)
    List<TaskJournalOutboxEntity> claimable(
            @Param("pending") TaskJournalOutboxEntity.State pending,
            @Param("inFlight") TaskJournalOutboxEntity.State inFlight,
            @Param("now") Instant now,
            Pageable page);

    long countByState(TaskJournalOutboxEntity.State state);
}
