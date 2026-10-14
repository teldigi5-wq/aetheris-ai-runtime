package io.aetheris.orchestrator.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import java.time.Instant;
import java.util.*;

public interface MemoryRecordRepository extends JpaRepository<MemoryRecordEntity,UUID> {
    List<MemoryRecordEntity> findTop500ByOwnerIdOrderByUpdatedAtDesc(String ownerId);
    List<MemoryRecordEntity> findByExpiresAtLessThanEqual(Instant expiresAt);

    /**
     * Never selects unencrypted payloads or identifying owner/project metadata.
     * Header inspection does NOT authenticate ciphertext or establish migration
     * readiness. Read-only in the calling service's snapshot transaction.
     */
    @Query("""
            select new io.aetheris.orchestrator.memory.MemoryEnvelopeInventoryRow(
                m.id, m.encrypted, m.sensitive,
                case when m.encrypted = true then substring(m.payload, 1, 96) else null end,
                case when m.encrypted = true then length(m.payload) else null end)
            from MemoryRecordEntity m
            order by m.id asc
            """)
    Slice<MemoryEnvelopeInventoryRow> inspectEncryptionHeaders(Pageable pageable);
}
