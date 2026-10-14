package io.aetheris.orchestrator.memory;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only, on-demand diagnostic for a future explicitly approved migration.
 * No controller, CLI, scheduled task or startup hook calls this service.
 *
 * Classifies encrypted *headers only*; it NEVER decrypts, accesses unencrypted
 * payload contents, writes rows, tests keys against ciphertext, claims
 * authenticated integrity, or declares a database safe for key retirement.
 */
@Service
public class MemoryEnvelopeInventoryService {
    private static final int BATCH_SIZE = 100;
    private static final int MAX_SCAN_ROWS = 5_000;
    private static final int MAX_REPORTED_KEY_IDS = 16;
    private static final Pattern V2_HEADER = Pattern.compile(
            "^enc:v2:A1\\.([A-Za-z0-9_-]{1,24})\\.[A-Za-z0-9_-]{16}\\.");
    private final MemoryRecordRepository records;

    public MemoryEnvelopeInventoryService(MemoryRecordRepository records) {
        this.records = Objects.requireNonNull(records, "records");
    }

    /**
     * A single database snapshot and a hard ceiling avoid misleading partial
     * results, unbounded memory use, and concurrent-write pagination drift.
     * Database snapshot semantics must be separately verified for PostgreSQL.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public InventoryReport inspect() {
        int total = 0;
        int v1 = 0;
        int v2 = 0;
        int unrecognized = 0;
        int sensitivePlaintext = 0;
        int encryptedNotSensitive = 0;
        int nonSensitivePlaintext = 0;
        int undisplayedKeyRecords = 0;
        Map<String, Integer> keyHeaders = new LinkedHashMap<>();
        boolean complete = false;

        for (int pageIndex = 0; pageIndex < MAX_SCAN_ROWS / BATCH_SIZE; pageIndex++) {
            Slice<MemoryEnvelopeInventoryRow> page = records.inspectEncryptionHeaders(
                    PageRequest.of(pageIndex, BATCH_SIZE));
            List<MemoryEnvelopeInventoryRow> rows = page.getContent();
            // Unexpected repository over-delivery must never turn into a
            // false complete inventory or bypass the scan ceiling.
            if (rows.size() > BATCH_SIZE) {
                throw new IllegalStateException("INVENTORY_PAGE_LIMIT_EXCEEDED");
            }
            for (MemoryEnvelopeInventoryRow row : rows) {
                total++;
                if (!row.encrypted()) {
                    if (row.sensitive()) sensitivePlaintext++;
                    else nonSensitivePlaintext++;
                    continue;
                }

                if (!row.sensitive()) encryptedNotSensitive++;
                String header = row.encryptedHeader();
                if (header == null || row.encryptedCharacters() == null) {
                    unrecognized++;
                    continue;
                }
                if (header.startsWith("enc:v1:")) {
                    v1++;
                    continue;
                }

                Matcher match = V2_HEADER.matcher(header);
                // At minimum a 128-bit GCM tag requires 22 unpadded Base64
                // characters; parsing a prefix is NOT integrity validation.
                if (match.find() && row.encryptedCharacters() >= match.end() + 22) {
                    v2++;
                    String keyId = match.group(1);
                    if (keyHeaders.containsKey(keyId)) {
                        keyHeaders.merge(keyId, 1, Integer::sum);
                    } else if (keyHeaders.size() < MAX_REPORTED_KEY_IDS) {
                        keyHeaders.put(keyId, 1);
                    } else {
                        undisplayedKeyRecords++;
                    }
                } else {
                    unrecognized++;
                }
            }
            if (!page.hasNext()) {
                complete = true;
                break;
            }
        }

        return new InventoryReport(total, complete, v1, v2, unrecognized,
                sensitivePlaintext, encryptedNotSensitive, nonSensitivePlaintext,
                Collections.unmodifiableMap(new LinkedHashMap<>(keyHeaders)),
                undisplayedKeyRecords, "INVENTORY_ONLY_NOT_MIGRATION_READY");
    }

    /**
     * No owner/project IDs, memory keys, user text, ciphertext, or UUIDs ever
     * leave the inventory; map keys are header-only, syntactically constrained
     * *public* key identifiers, never encryption key material.
     */
    public record InventoryReport(int scannedRows, boolean complete,
                                  int legacyV1Headers, int boundV2Headers,
                                  int unrecognizedEncryptedHeaders,
                                  int sensitiveWithoutEncryption,
                                  int encryptedNotMarkedSensitive,
                                  int unencryptedNotSensitive,
                                  Map<String, Integer> v2EnvelopeKeyIdCounts,
                                  int undisplayedKeyIdRecords,
                                  String status) {
    }
}
