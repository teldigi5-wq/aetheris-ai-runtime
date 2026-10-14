package io.aetheris.orchestrator.memory;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Adversarial, wholly synthetic rotation tests; no real KMS or user records. */
class OwnerBoundMemoryKeyringTest {
    private static final String FIRST = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final String SECOND = Base64.getEncoder().encodeToString(
            "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8));
    private static final byte[] AAD = "owner=alice|record=one|project=p1".getBytes(StandardCharsets.UTF_8);

    @Test
    void addingNewActiveKeyPreservesOldDecryptionWithoutChangingOldCiphertext() {
        try (var first = OwnerBoundMemoryKeyring.fromConfig("v1", FIRST, "");
             var rotated = OwnerBoundMemoryKeyring.fromConfig("v2", SECOND, "v1:" + FIRST);
             var retired = OwnerBoundMemoryKeyring.fromConfig("v2", SECOND, "")) {
            String oldEnvelope = first.encrypt("historic secret".getBytes(StandardCharsets.UTF_8), AAD);
            String newEnvelope = rotated.encrypt("new secret".getBytes(StandardCharsets.UTF_8), AAD);
            assertThat(oldEnvelope).startsWith("A1.v1.");
            assertThat(newEnvelope).startsWith("A1.v2.");
            assertThat(new String(rotated.decrypt(oldEnvelope, AAD), StandardCharsets.UTF_8))
                    .isEqualTo("historic secret");
            assertThat(new String(rotated.decrypt(newEnvelope, AAD), StandardCharsets.UTF_8))
                    .isEqualTo("new secret");
            assertThatThrownBy(() -> retired.decrypt(oldEnvelope, AAD))
                    .isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> first.decrypt(newEnvelope, AAD))
                    .isInstanceOf(SecurityException.class);
        }
    }

    @Test
    void neverFallbackToAlternativeKeyOnMissingIdTamperOrWrongOwner() {
        try (var ring = OwnerBoundMemoryKeyring.fromConfig("v2", SECOND, "v1:" + FIRST)) {
            String value = ring.encrypt("private".getBytes(StandardCharsets.UTF_8), AAD);
            assertThatThrownBy(() -> ring.decrypt(value.replace(".v2.", ".v1."), AAD))
                    .isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> ring.decrypt(value.replace(".v2.", ".unknown."), AAD))
                    .isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> ring.decrypt(value, "owner=bob|record=one|project=p1"
                    .getBytes(StandardCharsets.UTF_8))).isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> ring.decrypt("plaintext", AAD))
                    .isInstanceOf(SecurityException.class);
        }
    }

    @Test
    void invalidKeysDuplicatesAndMalformedEntriesFailAtStartup() {
        assertThatThrownBy(() -> OwnerBoundMemoryKeyring.fromConfig("v1", "", ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("externally provisioned");
        assertThatThrownBy(() -> OwnerBoundMemoryKeyring.fromConfig("v1", FIRST, "v1:" + SECOND))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OwnerBoundMemoryKeyring.fromConfig("v1", FIRST, "v2:" + FIRST))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OwnerBoundMemoryKeyring.fromConfig("v1", FIRST, "v2:not-base64"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OwnerBoundMemoryKeyring.fromConfig("v1", FIRST, "v2:"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OwnerBoundMemoryKeyring.fromConfig("../evil", FIRST, ""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OwnerBoundMemoryKeyring.fromConfig("v1", FIRST, "v2:" + SECOND + ","))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void memoryServiceSelectsActiveKeyForWritesAndRetainsHistoricalReadKey() {
        UUID id = UUID.randomUUID();
        try (var older = new MemoryEncryptionService("", new SimpleMeterRegistry(),
                true, FIRST, "v1");
             var rotated = new MemoryEncryptionService("", new SimpleMeterRegistry(),
                     true, SECOND, "v2", "v1:" + FIRST)) {
            String old = older.protect("old owner memory", true, id, "alice",
                    MemoryScope.PROJECT, "work").payload();
            String fresh = rotated.protect("new owner memory", true, id, "alice",
                    MemoryScope.PROJECT, "work").payload();
            assertThat(old).startsWith("enc:v2:A1.v1.");
            assertThat(fresh).startsWith("enc:v2:A1.v2.");
            assertThat(rotated.reveal(old, true, id, "alice", MemoryScope.PROJECT, "work"))
                    .isEqualTo("old owner memory");
            assertThat(rotated.reveal(fresh, true, id, "alice", MemoryScope.PROJECT, "work"))
                    .isEqualTo("new owner memory");
            assertThatThrownBy(() -> rotated.reveal(old, true, id, "bob",
                    MemoryScope.PROJECT, "work")).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void disabledGateCannotHideConfiguredPreviousKeys() {
        assertThatThrownBy(() -> new MemoryEncryptionService("", new SimpleMeterRegistry(),
                false, "", "", "v1:" + FIRST)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void closedKeyringCannotEncryptOrDecrypt() {
        var ring = OwnerBoundMemoryKeyring.fromConfig("v1", FIRST, "");
        String encoded = ring.encrypt("data".getBytes(StandardCharsets.UTF_8), AAD);
        ring.close();
        assertThatThrownBy(() -> ring.decrypt(encoded, AAD)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ring.encrypt("x".getBytes(StandardCharsets.UTF_8), AAD))
                .isInstanceOf(IllegalStateException.class);
    }
}
