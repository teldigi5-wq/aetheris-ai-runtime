package io.aetheris.orchestrator.memory;

import io.aetheris.orchestrator.security.AeadFieldProtector;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * The legacy v1 memory envelope remains readable until an audited migration.
 * Opt-in v2 uses the Stage 43 AES-256-GCM primitive and authenticates the
 * immutable memory UUID, owner, scope and project as AAD. No implicit v2 key.
 * This covers only the durable memory payload column, not metadata or backups.
 */
@Service
public class MemoryEncryptionService implements AutoCloseable {
    private static final String LEGACY_PREFIX = "enc:v1:";
    private static final String BOUND_PREFIX = "enc:v2:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecretKey legacyKey;
    private final AeadFieldProtector ownerBound;
    private final SecureRandom random = new SecureRandom();
    private final MeterRegistry metrics;

    /** For existing direct unit callers only: legacy mode remains unchanged. */
    public MemoryEncryptionService(String encodedKey, MeterRegistry metrics) {
        this(encodedKey, metrics, false, "", "");
    }

    @Autowired
    public MemoryEncryptionService(
            @Value("${aetheris.memory.master-key-b64:}") String legacyKeyB64,
            MeterRegistry metrics,
            @Value("${aetheris.memory.owner-bound-aead-enabled:false}") boolean enabled,
            @Value("${aetheris.memory.owner-bound-key-b64:}") String boundKeyB64,
            @Value("${aetheris.memory.owner-bound-key-id:}") String boundKeyId) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.legacyKey = parseLegacy(legacyKeyB64);
        if (enabled) {
            byte[] key = decodeKey(boundKeyB64, "Owner-bound memory key");
            try {
                this.ownerBound = new AeadFieldProtector(boundKeyId, key);
            } finally {
                Arrays.fill(key, (byte) 0);
            }
        } else {
            this.ownerBound = null;
        }
    }

    /** Legacy API retained to permit a deliberate, separately tested v1 migration. */
    public ProtectedPayload protect(String plaintext, boolean sensitive) {
        if (!sensitive) return new ProtectedPayload(plaintext, false);
        if (legacyKey == null) {
            fail("encrypt");
            throw new IllegalStateException("Sensitive memory requires aetheris.memory.master-key-b64");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, legacyKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(Objects.requireNonNull(plaintext, "plaintext")
                    .getBytes(StandardCharsets.UTF_8));
            return new ProtectedPayload(LEGACY_PREFIX + Base64.getEncoder().encodeToString(iv)
                    + ":" + Base64.getEncoder().encodeToString(ciphertext), true);
        } catch (Exception ex) {
            fail("encrypt");
            throw new IllegalStateException("Unable to encrypt memory payload", ex);
        }
    }

    /** New durable writes bind ciphertext to its owner and immutable row identity. */
    public ProtectedPayload protect(String plaintext, boolean sensitive, UUID recordId,
                                    String ownerId, MemoryScope scope, String projectId) {
        if (!sensitive) return new ProtectedPayload(plaintext, false);
        if (ownerBound == null) return protect(plaintext, true);
        try {
            byte[] aad = context(recordId, ownerId, scope, projectId);
            byte[] data = Objects.requireNonNull(plaintext, "plaintext").getBytes(StandardCharsets.UTF_8);
            try {
                return new ProtectedPayload(BOUND_PREFIX + ownerBound.encrypt(data, aad), true);
            } finally {
                Arrays.fill(data, (byte) 0);
                Arrays.fill(aad, (byte) 0);
            }
        } catch (RuntimeException ex) {
            fail("encrypt");
            throw new IllegalStateException("Unable to encrypt owner-bound memory payload", ex);
        }
    }

    /** Legacy API cannot decrypt owner-bound v2 ciphertext without row context. */
    public String reveal(String payload, boolean encrypted) {
        if (!encrypted) return payload;
        if (payload != null && payload.startsWith(BOUND_PREFIX)) {
            fail("decrypt");
            throw new IllegalStateException("Owner-bound context required to decrypt memory payload");
        }
        return revealLegacy(payload);
    }

    public String reveal(String payload, boolean encrypted, UUID recordId,
                         String ownerId, MemoryScope scope, String projectId) {
        if (!encrypted) return payload;
        if (payload != null && payload.startsWith(BOUND_PREFIX)) {
            if (ownerBound == null) {
                fail("decrypt");
                throw new IllegalStateException("Owner-bound memory key is not configured");
            }
            try {
                byte[] aad = context(recordId, ownerId, scope, projectId);
                try {
                    byte[] decrypted = ownerBound.decrypt(payload.substring(BOUND_PREFIX.length()), aad);
                    try {
                        return new String(decrypted, StandardCharsets.UTF_8);
                    } finally {
                        Arrays.fill(decrypted, (byte) 0);
                    }
                } finally {
                    Arrays.fill(aad, (byte) 0);
                }
            } catch (RuntimeException ex) {
                fail("decrypt");
                throw new IllegalStateException("Owner-bound memory authentication failed", ex);
            }
        }
        // Intentional backwards-compatible read. Legacy rows are not AAD-bound.
        // Disable this path only after a reviewed, backup-safe migration.
        return revealLegacy(payload);
    }

    public boolean configured() { return legacyKey != null || ownerBound != null; }
    public boolean ownerBoundEnabled() { return ownerBound != null; }

    private String revealLegacy(String payload) {
        if (legacyKey == null) {
            fail("decrypt");
            throw new IllegalStateException("Memory decryption key is not configured");
        }
        try {
            if (payload == null || !payload.startsWith(LEGACY_PREFIX))
                throw new IllegalArgumentException("Unsupported encrypted memory payload");
            String[] parts = payload.substring(LEGACY_PREFIX.length()).split(":", -1);
            if (parts.length != 2) throw new IllegalArgumentException("Malformed encrypted memory payload");
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] cipherText = Base64.getDecoder().decode(parts[1]);
            if (iv.length != IV_BYTES || cipherText.length < TAG_BITS / 8)
                throw new IllegalArgumentException("Malformed encrypted memory payload");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, legacyKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            fail("decrypt");
            throw new IllegalStateException("Unable to decrypt memory payload", ex);
        }
    }

    /** Length-prefixed domain separation prevents ambiguous owner/project encodings. */
    private static byte[] context(UUID recordId, String ownerId, MemoryScope scope, String projectId) {
        Objects.requireNonNull(recordId, "recordId");
        Objects.requireNonNull(scope, "scope");
        if (ownerId == null || ownerId.isBlank() || ownerId.length() > 120)
            throw new IllegalArgumentException("ownerId required");
        if (scope == MemoryScope.PROJECT && (projectId == null || projectId.isBlank()))
            throw new IllegalArgumentException("projectId required");
        if (projectId != null && projectId.length() > 160)
            throw new IllegalArgumentException("projectId too long");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream writer = new DataOutputStream(bytes);
            writer.writeUTF("aetheris:durable-memory:payload:v2");
            writer.writeUTF(recordId.toString());
            writer.writeUTF(ownerId);
            writer.writeUTF(scope.name());
            writer.writeUTF(projectId == null ? "" : projectId);
            writer.flush();
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Memory context unavailable", ex);
        }
    }

    private SecretKey parseLegacy(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        byte[] raw = decodeKey(encoded, "Legacy memory key");
        try {
            return new SecretKeySpec(raw, "AES");
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }

    private static byte[] decodeKey(String encoded, String label) {
        if (encoded == null || encoded.isBlank())
            throw new IllegalStateException(label + " must be externally provisioned");
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(label + " must be valid Base64", ex);
        }
        if (raw.length != 32) {
            Arrays.fill(raw, (byte) 0);
            throw new IllegalStateException(label + " must decode to exactly 32 bytes");
        }
        return raw;
    }

    private void fail(String operation) {
        metrics.counter("aetheris_memory_crypto_failures_total", "operation", operation).increment();
    }

    @PreDestroy
    @Override
    public void close() {
        if (ownerBound != null) ownerBound.close();
    }

    public record ProtectedPayload(String payload, boolean encrypted) {}
}
