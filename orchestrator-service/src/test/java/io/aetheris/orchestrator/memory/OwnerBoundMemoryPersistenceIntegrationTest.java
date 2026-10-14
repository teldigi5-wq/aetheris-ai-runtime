package io.aetheris.orchestrator.memory;

import io.aetheris.orchestrator.memory.MemoryContracts.MemoryView;
import io.aetheris.orchestrator.memory.MemoryContracts.WriteRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Actual JPA storage boundary, not just an isolated crypto library test. */
@SpringBootTest(properties = {
        "aetheris.memory.master-key-b64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "aetheris.memory.owner-bound-aead-enabled=true",
        "aetheris.memory.owner-bound-key-b64=YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=",
        "aetheris.memory.owner-bound-key-id=stage44-test",
        "aetheris.memory.retention-sweep-ms=3600000"
})
class OwnerBoundMemoryPersistenceIntegrationTest {
    @Autowired DurableMemoryService memory;
    @Autowired MemoryRecordRepository records;

    @Test
    void sensitiveJPARowContainsAuthenticatedCiphertextRatherThanPayload() {
        String owner = "owner-" + UUID.randomUUID();
        String project = "proj-" + UUID.randomUUID();
        String secret = "STAGE44-SECRET-" + UUID.randomUUID();
        MemoryView result = memory.write(owner, new WriteRequest(MemoryScope.PROJECT,
                project, "private-memory", secret, true, Set.of("private"),
                "OWNER_INPUT", null, null));
        var raw = records.findById(result.id()).orElseThrow();
        assertThat(raw.isEncrypted()).isTrue();
        assertThat(raw.getPayload()).startsWith("enc:v2:A1.stage44-test.")
                .doesNotContain(secret);
        assertThat(memory.get(owner, project, result.id()).content()).isEqualTo(secret);
        assertThatThrownBy(() -> memory.get("other-owner", project, result.id()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void swappingCiphertextBetweenExistingRowsFailsEvenForSameOwnerProject() {
        String owner = "owner-" + UUID.randomUUID();
        String project = "project-" + UUID.randomUUID();
        var a = memory.write(owner, new WriteRequest(MemoryScope.PROJECT,
                project, "a", "unique secret a", true, Set.of(), "OWNER_INPUT", null, null));
        var b = memory.write(owner, new WriteRequest(MemoryScope.PROJECT,
                project, "b", "unique secret b", true, Set.of(), "OWNER_INPUT", null, null));
        var rawA = records.findById(a.id()).orElseThrow();
        var rawB = records.findById(b.id()).orElseThrow();
        rawB.update(rawB.getScope(), rawB.getProjectId(), rawB.getMemoryKey(),
                rawA.getPayload(), true, true, rawB.getTags(),
                rawB.getProvenanceType(), rawB.getProvenanceReference(), rawB.getExpiresAt());
        records.saveAndFlush(rawB);
        assertThatThrownBy(() -> memory.get(owner, project, b.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authentication failed");
    }

    @Test
    void olderV1RowsStillReadUnderControlledMigrationCompatibility() {
        String owner = "owner-" + UUID.randomUUID();
        String project = "project-" + UUID.randomUUID();
        try (var old = new MemoryEncryptionService(
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                new SimpleMeterRegistry())) {
            UUID id = UUID.randomUUID();
            String oldCipher = old.protect("historic protected memory", true).payload();
            records.saveAndFlush(new MemoryRecordEntity(id, owner, MemoryScope.PROJECT,
                    project, "historical", oldCipher, true, true, Set.of(),
                    "OWNER_INPUT", null, null));
            assertThat(memory.get(owner, project, id).content()).isEqualTo("historic protected memory");
        }
    }
}
