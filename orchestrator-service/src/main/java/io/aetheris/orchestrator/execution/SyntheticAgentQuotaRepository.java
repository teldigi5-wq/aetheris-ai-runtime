package io.aetheris.orchestrator.execution;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SyntheticAgentQuotaRepository
        extends JpaRepository<SyntheticAgentQuotaEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from SyntheticAgentQuotaEntity q where q.scopeKey = :key")
    Optional<SyntheticAgentQuotaEntity> lockScope(@Param("key") String key);
}
