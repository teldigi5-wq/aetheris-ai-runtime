package io.aetheris.orchestrator.memory;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Pure synthetic fixtures; no live records, secrets, migration, or decrypt. */
class MemoryEnvelopeInventoryServiceTest {
    @Test
    void classifiesHeadersAndSensitivePlaintextWithoutReportingOwnerOrContent() {
        var repo = mock(MemoryRecordRepository.class);
        var page = PageRequest.of(0, 100);
        var rows = List.of(
                row(true, true, "enc:v1:abcdefghijklmnop:someciphertext", 75),
                row(true, true, "enc:v2:A1.key_2026.abcdefghijklmnop.ABCDEF", 90),
                row(true, false, "enc:v2:A1.key_2026.abcdefghijklmnop.ABCDEF", 90),
                row(true, true, "enc:v2:A1.other-key.abcdefghijklmnop.ABCDEF", 90),
                row(true, true, "enc:v2:A1.bad/key.abcdefghijklmnop.ABCDEF", 90),
                row(true, true, "enc:v2:A1.key_2026.abcdefghijklmnop.", 32),
                row(false, true, null, null),
                row(false, false, null, null));
        when(repo.inspectEncryptionHeaders(any(Pageable.class)))
                .thenReturn(new SliceImpl<>(rows, page, false));

        var report = new MemoryEnvelopeInventoryService(repo).inspect();
        assertThat(report.scannedRows()).isEqualTo(8);
        assertThat(report.complete()).isTrue();
        assertThat(report.legacyV1Headers()).isEqualTo(1);
        assertThat(report.boundV2Headers()).isEqualTo(3);
        assertThat(report.unrecognizedEncryptedHeaders()).isEqualTo(2);
        assertThat(report.encryptedNotMarkedSensitive()).isEqualTo(1);
        assertThat(report.sensitiveWithoutEncryption()).isEqualTo(1);
        assertThat(report.unencryptedNotSensitive()).isEqualTo(1);
        assertThat(report.v2EnvelopeKeyIdCounts()).containsEntry("key_2026", 2)
                .containsEntry("other-key", 1);
        assertThat(report.status()).isEqualTo("INVENTORY_ONLY_NOT_MIGRATION_READY");
        assertThat(report.toString()).doesNotContain("owner:")
                .doesNotContain("someciphertext")
                .doesNotContain("ABCDEF");
        verify(repo).inspectEncryptionHeaders(page);
        verifyNoMoreInteractions(repo);
    }

    @Test
    void hardScanLimitNeverPretendsFullCoverageWhenMoreRowsRemain() {
        var repo = mock(MemoryRecordRepository.class);
        List<MemoryEnvelopeInventoryRow> rows = new ArrayList<>();
        for (int i = 0; i < 100; i++) rows.add(row(false, false, null, null));
        when(repo.inspectEncryptionHeaders(any(Pageable.class))).thenAnswer(invocation -> {
            Pageable p = invocation.getArgument(0);
            return new SliceImpl<>(rows, p, true);
        });

        var report = new MemoryEnvelopeInventoryService(repo).inspect();
        assertThat(report.scannedRows()).isEqualTo(5_000);
        assertThat(report.complete()).isFalse();
        assertThat(report.unencryptedNotSensitive()).isEqualTo(5_000);
        verify(repo, times(50)).inspectEncryptionHeaders(any(Pageable.class));
        verifyNoMoreInteractions(repo);
    }

    @Test
    void overflowingDistinctKeyIdentifiersAreCountedButNotEmitted() {
        var repo = mock(MemoryRecordRepository.class);
        List<MemoryEnvelopeInventoryRow> rows = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            rows.add(row(true, true,
                    "enc:v2:A1.key" + i + ".abcdefghijklmnop.encryptedtag",
                    95));
        }
        when(repo.inspectEncryptionHeaders(any(Pageable.class)))
                .thenReturn(new SliceImpl<>(rows, PageRequest.of(0, 100), false));

        var report = new MemoryEnvelopeInventoryService(repo).inspect();
        assertThat(report.complete()).isTrue();
        assertThat(report.boundV2Headers()).isEqualTo(25);
        assertThat(report.v2EnvelopeKeyIdCounts()).hasSize(16);
        assertThat(report.undisplayedKeyIdRecords()).isEqualTo(9);
    }

    private static MemoryEnvelopeInventoryRow row(boolean encrypted, boolean sensitive,
                                                   String prefix, Integer length) {
        return new MemoryEnvelopeInventoryRow(UUID.randomUUID(), encrypted,
                sensitive, prefix, length);
    }
}
