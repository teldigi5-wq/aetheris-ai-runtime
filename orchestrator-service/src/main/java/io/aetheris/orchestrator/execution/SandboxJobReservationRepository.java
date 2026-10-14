package io.aetheris.orchestrator.execution;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SandboxJobReservationRepository
        extends JpaRepository<SandboxJobReservationEntity, UUID> {
    boolean existsByNonce(UUID nonce);
    List<SandboxJobReservationEntity> findByWorkerIdAndRevokedFalse(String workerId);
}
