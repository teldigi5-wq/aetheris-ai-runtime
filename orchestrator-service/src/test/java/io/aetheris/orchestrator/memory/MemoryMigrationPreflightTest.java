package io.aetheris.orchestrator.memory;

import io.aetheris.orchestrator.memory.MemoryEnvelopeInventoryService.InventoryReport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.aetheris.orchestrator.memory.MemoryMigrationPreflight.Blocker.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryMigrationPreflightTest {
    private InventoryReport report(int scanned, boolean complete, int legacy, int v2,
                                   int malformed, int sensitivePlain, int encryptedNotSensitive,
                                   int normalPlain, Map<String,Integer> keyCounts, int overflow) {
        return new InventoryReport(scanned, complete, legacy, v2, malformed,
                sensitivePlain, encryptedNotSensitive, normalPlain,
                keyCounts, overflow, "INVENTORY_ONLY_NOT_MIGRATION_READY");
    }

    @Test
    void evenCleanCompleteSyntheticInventoryCannotAuthorizeMigration() {
        var result = MemoryMigrationPreflight.evaluate(
                report(2, true, 0, 2, 0, 0, 0, 0, Map.of("active", 2), 0));
        assertThat(result.status()).isEqualTo("BLOCKED_NO_MIGRATION_AUTHORITY");
        assertThat(result.blockers()).contains(KEY_CUSTODY_UNVERIFIED,
                POSTGRESQL_BACKUP_RESTORE_UNVERIFIED, OWNER_BOUND_IDENTITY_UNVERIFIED,
                INDEPENDENT_APPROVAL_REQUIRED);
        assertThat(result.blockers()).doesNotContain(INVALID_INVENTORY);
        assertThatThrownBy(() -> result.blockers().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void partialScanLegacyPlaintextAndMalformedMetadataAllBlockMigration() {
        var result = MemoryMigrationPreflight.evaluate(
                report(6, false, 1, 1, 1, 1, 1, 2, Map.of("old", 1), 0));
        assertThat(result.blockers()).contains(INCOMPLETE_SCAN,
                LEGACY_V1_MIGRATION_REQUIRED, SENSITIVE_PLAINTEXT_PRESENT,
                MALFORMED_ENCRYPTED_HEADERS, ENCRYPTION_FLAGS_INCONSISTENT);
        assertThat(result.status()).isEqualTo("BLOCKED_NO_MIGRATION_AUTHORITY");
    }

    @Test
    void suspiciousInconsistentCountOrFabricatedInventoryStatusIsRejected() {
        var badCount = report(10, true, 0, 2, 0, 0, 0, 0, Map.of("k", 2), 0);
        assertThat(MemoryMigrationPreflight.evaluate(badCount).blockers()).contains(INVALID_INVENTORY);
        var negative = report(0, true, -1, 0, 0, 0, 0, 1, Map.of(), 0);
        assertThat(MemoryMigrationPreflight.evaluate(negative).blockers()).contains(INVALID_INVENTORY);
        var unexpectedStatus = new InventoryReport(0, true, 0, 0, 0, 0, 0,
                0, Map.of(), 0, "READY");
        assertThat(MemoryMigrationPreflight.evaluate(unexpectedStatus).blockers()).contains(INVALID_INVENTORY);
        var suspiciousId = report(1, true, 0, 1, 0, 0, 0, 0, Map.of("../../key", 1), 0);
        assertThat(MemoryMigrationPreflight.evaluate(suspiciousId).blockers()).contains(INVALID_INVENTORY);
    }

    @Test
    void truncatedKeyDistributionIsBothIncompleteAndUnfitForKeyRetirement() {
        var report = report(4, true, 0, 4, 0, 0, 0, 0, Map.of("a", 3), 1);
        var decision = MemoryMigrationPreflight.evaluate(report);
        assertThat(decision.blockers()).contains(KEY_ID_LIST_TRUNCATED, KEY_CUSTODY_UNVERIFIED);
        assertThat(decision.scannedRows()).isEqualTo(4);
    }

    @Test
    void wrongKeyDistributionAndSensitiveFlagOverlapInvalidateTheAggregate() {
        assertThat(MemoryMigrationPreflight.evaluate(
                report(2, true, 0, 2, 0, 0, 0, 0, Map.of("a", 1), 0)).blockers())
                .contains(INVALID_INVENTORY);
        assertThat(MemoryMigrationPreflight.evaluate(
                report(1, true, 0, 0, 0, 1, 1, 0, Map.of(), 0)).blockers())
                .contains(INVALID_INVENTORY);
    }

    @Test
    void noDataAndNullInputRemainFailClosed() {
        var empty = MemoryMigrationPreflight.evaluate(
                report(0, true, 0, 0, 0, 0, 0, 0, Map.of(), 0));
        assertThat(empty.status()).isEqualTo("BLOCKED_NO_MIGRATION_AUTHORITY");
        assertThatThrownBy(() -> MemoryMigrationPreflight.evaluate(null))
                .isInstanceOf(NullPointerException.class);
    }
}
