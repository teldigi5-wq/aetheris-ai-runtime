package io.aetheris.orchestrator.memory;

import io.aetheris.orchestrator.memory.MemoryEnvelopeInventoryService.InventoryReport;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Pure, conservative migration-planning evaluator over the already aggregated
 * inventory. Never decrypts, touches a repository, authorizes key retirement,
 * or activates database mutation. There is intentionally no "READY" state.
 */
public final class MemoryMigrationPreflight {
    private MemoryMigrationPreflight() {}

    public enum Blocker {
        INVALID_INVENTORY,
        INCOMPLETE_SCAN,
        LEGACY_V1_MIGRATION_REQUIRED,
        MALFORMED_ENCRYPTED_HEADERS,
        SENSITIVE_PLAINTEXT_PRESENT,
        ENCRYPTION_FLAGS_INCONSISTENT,
        KEY_ID_LIST_TRUNCATED,
        KEY_CUSTODY_UNVERIFIED,
        POSTGRESQL_BACKUP_RESTORE_UNVERIFIED,
        OWNER_BOUND_IDENTITY_UNVERIFIED,
        INDEPENDENT_APPROVAL_REQUIRED
    }

    /** This deliberately never returns a migration authorization. */
    public static Report evaluate(InventoryReport inventory) {
        Objects.requireNonNull(inventory, "inventory");
        EnumSet<Blocker> reasons = EnumSet.of(
                Blocker.KEY_CUSTODY_UNVERIFIED,
                Blocker.POSTGRESQL_BACKUP_RESTORE_UNVERIFIED,
                Blocker.OWNER_BOUND_IDENTITY_UNVERIFIED,
                Blocker.INDEPENDENT_APPROVAL_REQUIRED);

        if (!valid(inventory)) reasons.add(Blocker.INVALID_INVENTORY);
        if (!inventory.complete()) reasons.add(Blocker.INCOMPLETE_SCAN);
        if (inventory.legacyV1Headers() > 0) reasons.add(Blocker.LEGACY_V1_MIGRATION_REQUIRED);
        if (inventory.unrecognizedEncryptedHeaders() > 0) reasons.add(Blocker.MALFORMED_ENCRYPTED_HEADERS);
        if (inventory.sensitiveWithoutEncryption() > 0) reasons.add(Blocker.SENSITIVE_PLAINTEXT_PRESENT);
        if (inventory.encryptedNotMarkedSensitive() > 0) reasons.add(Blocker.ENCRYPTION_FLAGS_INCONSISTENT);
        if (inventory.undisplayedKeyIdRecords() > 0) reasons.add(Blocker.KEY_ID_LIST_TRUNCATED);

        return new Report("BLOCKED_NO_MIGRATION_AUTHORITY",
                inventory.scannedRows(), inventory.complete(),
                Collections.unmodifiableSet(EnumSet.copyOf(reasons)));
    }

    private static boolean valid(InventoryReport v) {
        if (!"INVENTORY_ONLY_NOT_MIGRATION_READY".equals(v.status())
                || v.scannedRows() < 0 || v.scannedRows() > 5_000
                || v.legacyV1Headers() < 0 || v.boundV2Headers() < 0
                || v.unrecognizedEncryptedHeaders() < 0
                || v.sensitiveWithoutEncryption() < 0
                || v.encryptedNotMarkedSensitive() < 0
                || v.unencryptedNotSensitive() < 0
                || v.undisplayedKeyIdRecords() < 0
                || v.v2EnvelopeKeyIdCounts() == null
                || v.v2EnvelopeKeyIdCounts().size() > 16) return false;

        long total = (long) v.legacyV1Headers() + v.boundV2Headers()
                + v.unrecognizedEncryptedHeaders()
                + v.sensitiveWithoutEncryption() + v.unencryptedNotSensitive();
        if (total != v.scannedRows()
                || v.encryptedNotMarkedSensitive() >
                   (long) v.legacyV1Headers() + v.boundV2Headers()
                           + v.unrecognizedEncryptedHeaders()) return false;

        long included = v.undisplayedKeyIdRecords();
        for (var entry : v.v2EnvelopeKeyIdCounts().entrySet()) {
            if (entry.getKey() == null
                    || !entry.getKey().matches("[A-Za-z0-9_-]{1,24}")
                    || entry.getValue() == null || entry.getValue() <= 0) return false;
            included += entry.getValue();
        }
        return included == v.boundV2Headers();
    }

    public record Report(String status, int scannedRows, boolean inventoryComplete,
                         Set<Blocker> blockers) {
        public Report {
            Objects.requireNonNull(status);
            blockers = Set.copyOf(Objects.requireNonNull(blockers));
        }
    }
}
