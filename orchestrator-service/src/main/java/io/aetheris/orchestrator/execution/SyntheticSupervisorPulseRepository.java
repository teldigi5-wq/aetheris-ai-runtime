package io.aetheris.orchestrator.execution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface SyntheticSupervisorPulseRepository
        extends JpaRepository<SyntheticSupervisorPulseEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SyntheticSupervisorPulseEntity p where p.jobId = :id")
    Optional<SyntheticSupervisorPulseEntity> lockPulse(@Param("id") UUID id);
}
