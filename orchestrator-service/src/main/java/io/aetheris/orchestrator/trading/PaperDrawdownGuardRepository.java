package io.aetheris.orchestrator.trading;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

/** Pessimistic row lock; no automatic reset or delete method is exposed. */
public interface PaperDrawdownGuardRepository extends JpaRepository<PaperDrawdownGuardEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select guard from PaperDrawdownGuardEntity guard where guard.accountKey = :accountKey")
    Optional<PaperDrawdownGuardEntity> locked(@Param("accountKey") String accountKey);
}
