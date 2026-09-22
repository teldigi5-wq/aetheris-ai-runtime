package io.aetheris.orchestrator.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.*;

public interface MemoryRecordRepository extends JpaRepository<MemoryRecordEntity,UUID> {
    List<MemoryRecordEntity> findTop500ByOwnerIdOrderByUpdatedAtDesc(String ownerId);
    List<MemoryRecordEntity> findByExpiresAtLessThanEqual(Instant expiresAt);
}
