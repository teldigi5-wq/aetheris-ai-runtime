package io.aetheris.orchestrator.memory;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OwnerBoundMemoryAeadTest {
    private static final String LEGACY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String V2 = "YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=";

    private MemoryEncryptionService secure() {
        return new MemoryEncryptionService(LEGACY, new SimpleMeterRegistry(),
                true, V2, "owner-key-1");
    }

    @Test
    void newCiphertextAuthenticatesOwnerProjectScopeAndImmutableRecordId() {
        UUID id = UUID.randomUUID();
        try (var service = secure()) {
            var first = service.protect("OWNER_PRIVATE_MEMORY", true, id,
                    "alice", MemoryScope.PROJECT, "project-a");
            var second = service.protect("OWNER_PRIVATE_MEMORY", true, id,
                    "alice", MemoryScope.PROJECT, "project-a");
            assertThat(first.encrypted()).isTrue();
            assertThat(first.payload()).startsWith("enc:v2:A1.owner-key-1.")
                    .doesNotContain("OWNER_PRIVATE_MEMORY");
            assertThat(first.payload()).isNotEqualTo(second.payload());
            assertThat(service.reveal(first.payload(), true, id, "alice",
                    MemoryScope.PROJECT, "project-a")).isEqualTo("OWNER_PRIVATE_MEMORY");
            assertThatThrownBy(() -> service.reveal(first.payload(), true, id,
                    "bob", MemoryScope.PROJECT, "project-a")).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> service.reveal(first.payload(), true, id,
                    "alice", MemoryScope.PROJECT, "project-b")).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> service.reveal(first.payload(), true, id,
                    "alice", MemoryScope.OWNER, "project-a")).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> service.reveal(first.payload(), true, UUID.randomUUID(),
                    "alice", MemoryScope.PROJECT, "project-a")).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> service.reveal(first.payload(), true))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void tamperedCiphertextAndWrongKeyCannotReveal() {
        UUID id = UUID.randomUUID();
        try (var service = secure();
             var wrong = new MemoryEncryptionService(LEGACY, new SimpleMeterRegistry(),
                     true, LEGACY, "owner-key-1")) {
            String envelope = service.protect("sensitive", true, id,
                    "alice", MemoryScope.OWNER, null).payload();
            String tampered = envelope.substring(0, envelope.length() - 3) + "zz" +
                    envelope.charAt(envelope.length() - 1);
            assertThatThrownBy(() -> service.reveal(tampered, true, id,
                    "alice", MemoryScope.OWNER, null)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> wrong.reveal(envelope, true, id,
                    "alice", MemoryScope.OWNER, null)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void legacyRowsRemainReadableUntilAnExplicitSafeMigration() {
        UUID id = UUID.randomUUID();
        try (var old = new MemoryEncryptionService(LEGACY, new SimpleMeterRegistry());
             var current = secure()) {
            String legacyEnvelope = old.protect("existing historical memory", true).payload();
            assertThat(legacyEnvelope).startsWith("enc:v1:");
            assertThat(current.reveal(legacyEnvelope, true, id,
                    "alice", MemoryScope.PROJECT, "project-a"))
                    .isEqualTo("existing historical memory");
        }
    }

    @Test
    void enabledWithoutExternalKeyFailsClosedAndNoImplicitKeyIsGenerated() {
        assertThatThrownBy(() -> new MemoryEncryptionService(LEGACY,
                new SimpleMeterRegistry(), true, "", "owner-key-1"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("externally provisioned");
        assertThatThrownBy(() -> new MemoryEncryptionService(LEGACY,
                new SimpleMeterRegistry(), true, "not-base64!", "owner-key-1"))
                .isInstanceOf(IllegalStateException.class);
        try (var service = new MemoryEncryptionService("", new SimpleMeterRegistry(),
                false, "", "")) {
            assertThatThrownBy(() -> service.protect("sensitive", true, UUID.randomUUID(),
                    "alice", MemoryScope.OWNER, null))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(service.protect("normal", false, UUID.randomUUID(),
                    "alice", MemoryScope.OWNER, null).encrypted()).isFalse();
        }
    }

    @Test
    void closedAeadCipherCanNeverBeReused() {
        var service = secure();
        UUID id = UUID.randomUUID();
        var cipher = service.protect("secret", true, id, "alice", MemoryScope.OWNER, null);
        service.close();
        assertThatThrownBy(() -> service.reveal(cipher.payload(), true,
                id, "alice", MemoryScope.OWNER, null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.protect("new", true, UUID.randomUUID(),
                "alice", MemoryScope.OWNER, null)).isInstanceOf(IllegalStateException.class);
    }
}
