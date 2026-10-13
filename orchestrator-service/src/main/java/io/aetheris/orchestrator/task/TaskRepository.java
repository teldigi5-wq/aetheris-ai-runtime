package io.aetheris.orchestrator.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {
    // Serializes journal sequence allocations PER task across transactions.
    // The pessimistic lock is held through commit/rollback by the caller.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TaskEntity t where t.id = :taskId")
    Optional<TaskEntity> lockForJournalSequence(@Param("taskId") UUID taskId);

    List<TaskEntity> findTop50ByOrderByUpdatedAtDesc();
    List<TaskEntity> findTop50ByOwnerUserIdOrderByUpdatedAtDesc(Long ownerUserId);
    List<TaskEntity> findTop50ByOwnerUserIdAndOwnerProjectIdOrderByUpdatedAtDesc(Long ownerUserId, UUID ownerProjectId);
    List<TaskEntity> findTop50ByOwnerUserIdAndOwnerProjectIdIsNullOrderByUpdatedAtDesc(Long ownerUserId);
    List<TaskEntity> findByStateIn(Collection<TaskState> states);
}
