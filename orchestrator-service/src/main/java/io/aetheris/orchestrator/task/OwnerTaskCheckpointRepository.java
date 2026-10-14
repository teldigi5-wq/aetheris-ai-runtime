package io.aetheris.orchestrator.task;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OwnerTaskCheckpointRepository extends JpaRepository<OwnerTaskCheckpointEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OwnerTaskCheckpointEntity c where c.id = :id")
    Optional<OwnerTaskCheckpointEntity> lockById(@Param("id") UUID id);
}
