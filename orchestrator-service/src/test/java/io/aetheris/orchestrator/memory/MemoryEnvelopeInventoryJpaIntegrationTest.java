package io.aetheris.orchestrator.memory;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H2/JPA projection smoke test using synthetic memory records only.
 * No original, owner or production database is inspected.
 */
@SpringBootTest(properties = "aetheris.memory.retention-sweep-ms=3600000")
class MemoryEnvelopeInventoryJpaIntegrationTest {
    @Autowired MemoryRecordRepository records;
    @Autowired MemoryEnvelopeInventoryService inventory;

    @Test
    void actualJpaProjectionReadsOnlyHeaderMetadataAndNeverMutatesRows() {
        String owner = "synthetic-owner-" + UUID.randomUUID();
        String project = "synthetic-project-" + UUID.randomUUID();
        long beforeCount = records.count();
        try (var encryptor = new MemoryEncryptionService(
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                new SimpleMeterRegistry(), true,
                "YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
                "inventory-ci")) {

            UUID v2Id = UUID.randomUUID();
            String v2 = encryptor.protect("synthetic v2 protected memory", true,
                    v2Id, owner, MemoryScope.PROJECT, project).payload();
            UUID v1Id = UUID.randomUUID();
            String v1 = encryptor.protect("synthetic historical memory", true).payload();

            records.saveAndFlush(new MemoryRecordEntity(v2Id, owner, MemoryScope.PROJECT,
                    project, "v2", v2, true, true, Set.of(), "SYNTHETIC_TEST", null, null));
            records.saveAndFlush(new MemoryRecordEntity(v1Id, owner, MemoryScope.PROJECT,
                    project, "v1", v1, true, true, Set.of(), "SYNTHETIC_TEST", null, null));
            records.saveAndFlush(new MemoryRecordEntity(UUID.randomUUID(), owner, MemoryScope.PROJECT,
                    project, "exposure", "synthetic-unprotected-sensitive-text", false, true,
                    Set.of(), "SYNTHETIC_TEST", null, null));
            records.saveAndFlush(new MemoryRecordEntity(UUID.randomUUID(), owner, MemoryScope.PROJECT,
                    project, "unknown", "malformed-ciphertext", true, true,
                    Set.of(), "SYNTHETIC_TEST", null, null));

            long countAfterSetup = records.count();
            assertThat(countAfterSetup).isEqualTo(beforeCount + 4);

            var report = inventory.inspect();
            assertThat(report.complete()).isTrue();
            assertThat(report.scannedRows()).isEqualTo(Math.toIntExact(countAfterSetup));
            assertThat(report.boundV2Headers()).isGreaterThanOrEqualTo(1);
            assertThat(report.legacyV1Headers()).isGreaterThanOrEqualTo(1);
            assertThat(report.sensitiveWithoutEncryption()).isGreaterThanOrEqualTo(1);
            assertThat(report.unrecognizedEncryptedHeaders()).isGreaterThanOrEqualTo(1);
            assertThat(report.v2EnvelopeKeyIdCounts()).containsKey("inventory-ci");
            assertThat(report.toString()).doesNotContain(owner)
                    .doesNotContain(project)
                    .doesNotContain("synthetic-unprotected-sensitive-text")
                    .doesNotContain("synthetic v2 protected memory");
            assertThat(records.count()).isEqualTo(countAfterSetup);
            assertThat(records.findById(v2Id).orElseThrow().getPayload()).isEqualTo(v2);
            assertThat(records.findById(v1Id).orElseThrow().getPayload()).isEqualTo(v1);
        }
    }
}
