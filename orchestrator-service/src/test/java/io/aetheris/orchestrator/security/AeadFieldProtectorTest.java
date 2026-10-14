package io.aetheris.orchestrator.security;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AeadFieldProtectorTest {
    private static final byte[] KEY = "0123456789abcdef0123456789abcdef"
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] OTHER_KEY = "abcdef0123456789abcdef0123456789"
            .getBytes(StandardCharsets.UTF_8);

    private static byte[] context(long owner, String field) {
        return ("owner:" + owner + "|task:123|field:" + field)
                .getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void authenticatedEncryptionIsNonDeterministicAndRoundTripsOnlyForSameContext() {
        try (var protector = new AeadFieldProtector("key2026", KEY)) {
            byte[] plain = "PRIVATE_AGENT_MEMORY_NEVER_LOGGED"
                    .getBytes(StandardCharsets.UTF_8);
            String first = protector.encrypt(plain, context(101, "memory"));
            String second = protector.encrypt(plain, context(101, "memory"));
            assertThat(first).startsWith("A1.key2026.");
            assertThat(second).isNotEqualTo(first);
            assertThat(first).doesNotContain("PRIVATE_AGENT_MEMORY");
            assertThat(protector.decrypt(first, context(101, "memory")))
                    .isEqualTo(plain);
            assertThat(protector.decrypt(second, context(101, "memory")))
                    .isEqualTo(plain);
        }
    }

    @Test
    void crossOwnerCrossFieldWrongKeyAndTamperingCannotDecrypt() {
        try (var protector = new AeadFieldProtector("key2026", KEY);
             var wrong = new AeadFieldProtector("key2026", OTHER_KEY)) {
            String envelope = protector.encrypt("private".getBytes(StandardCharsets.UTF_8),
                    context(101,"memory"));
            assertThatThrownBy(() -> protector.decrypt(envelope, context(202,"memory")))
                    .isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> protector.decrypt(envelope, context(101,"password")))
                    .isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> wrong.decrypt(envelope, context(101,"memory")))
                    .isInstanceOf(SecurityException.class);
            String[] parts=envelope.split("\\.",-1);
            assertThat(parts).hasSize(4);
            String tagTampered = parts[0]+"."+parts[1]+"."+parts[2]+"."+
                    (parts[3].charAt(0)=='A'?'B':'A')+parts[3].substring(1);
            assertThatThrownBy(() -> protector.decrypt(tagTampered, context(101,"memory")))
                    .isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> protector.decrypt("plaintext", context(101,"memory")))
                    .isInstanceOf(SecurityException.class);
        }
    }

    @Test
    void keyIdentifierVersionMissingAadAndTooLargePayloadFailClosed() {
        assertThatThrownBy(() -> new AeadFieldProtector("demo", new byte[16]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AeadFieldProtector("../malicious", KEY))
                .isInstanceOf(IllegalArgumentException.class);
        try (var cipher = new AeadFieldProtector("key2026", KEY)) {
            assertThatThrownBy(() -> cipher.encrypt("x".getBytes(StandardCharsets.UTF_8),
                    new byte[0])).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> cipher.encrypt(new byte[1024*1024 + 1],
                    context(101,"memory"))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> cipher.decrypt("A0.key2026.AAAA.AAAA",
                    context(101,"memory"))).isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> cipher.decrypt("A1.different.AAAA.AAAA",
                    context(101,"memory"))).isInstanceOf(SecurityException.class);
        }
    }

    @Test
    void closedProtectorRejectsEncryptAndDecryptAndDoesNotUseDefaultKey() {
        var cipher = new AeadFieldProtector("k1", KEY);
        String envelope = cipher.encrypt("test".getBytes(StandardCharsets.UTF_8),
                context(101,"memory"));
        cipher.close();
        assertThatThrownBy(() -> cipher.encrypt(new byte[]{1}, context(101,"memory")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(envelope, context(101,"memory")))
                .isInstanceOf(IllegalStateException.class);
    }
}
