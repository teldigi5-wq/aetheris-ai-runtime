package io.aetheris.orchestrator.trading;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Row-level serialization of an existing offline synthetic replay anchor. */
public interface OfflinePaperReplayAnchorRepository
        extends JpaRepository<OfflinePaperReplayAnchorEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select anchor from OfflinePaperReplayAnchorEntity anchor " +
           "where anchor.accountKey = :accountKey")
    Optional<OfflinePaperReplayAnchorEntity> locked(
            @Param("accountKey") String accountKey);
}
