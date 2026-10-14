package io.aetheris.orchestrator.execution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface SandboxWorkerFenceRepository extends JpaRepository<SandboxWorkerFenceEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from SandboxWorkerFenceEntity w where w.workerId = :workerId")
    Optional<SandboxWorkerFenceEntity> lockWorker(@Param("workerId") String workerId);
}
