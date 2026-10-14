package io.aetheris.orchestrator.execution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface SandboxJobReservationRepository
        extends JpaRepository<SandboxJobReservationEntity, UUID> {
    boolean existsByNonce(UUID nonce);
    @Modifying
    @Query("update SandboxJobReservationEntity j set j.revoked = true where j.workerId = :workerId and j.revoked = false")
    int revokeWorkerReservations(@Param("workerId") String workerId);
}
