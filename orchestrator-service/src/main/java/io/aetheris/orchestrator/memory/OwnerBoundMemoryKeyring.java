package io.aetheris.orchestrator.memory;

import io.aetheris.orchestrator.security.AeadFieldProtector;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Small, fail-closed *pilot* keyring for the owner-bound v2 memory envelope.
 *
 * The active ID encrypts new writes; retained IDs decrypt their own older
 * envelopes only. Keys MUST be independently provisioned, protected and
 * recoverable by an external secret-management process. This class is NOT a
 * KMS, does not mutate stored records or validate backup recoverability.
 */
public final class OwnerBoundMemoryKeyring implements AutoCloseable {
    private static final int MAX_KEYS = 8;
    private static final int MAX_CONFIG_CHARS = 2048;
    private static final String VERSION = "A1";
    private final String activeKeyId;
    private final Map<String, AeadFieldProtector> protectors;
    private boolean closed;

    private OwnerBoundMemoryKeyring(String activeKeyId, Map<String, AeadFieldProtector> protectors) {
        this.activeKeyId = activeKeyId;
        this.protectors = Map.copyOf(protectors);
    }

    /**
     * previousKeysSpec format: key-id:BASE64,key-id:BASE64.
     * Never log the contents or use source-controlled/CI test keys for real data.
     * A missing/invalid active key is fatal when this keyring is requested.
     */
    public static OwnerBoundMemoryKeyring fromConfig(String activeKeyId, String activeBase64,
                                                     String previousKeysSpec) {
        if (!validId(activeKeyId)) {
            throw new IllegalStateException("Invalid active memory key ID");
        }
        String previous = previousKeysSpec == null ? "" : previousKeysSpec;
        if (previous.length() > MAX_CONFIG_CHARS) {
            throw new IllegalStateException("Invalid memory keyring configuration");
        }
        Map<String, AeadFieldProtector> protectors = new LinkedHashMap<>();
        Map<String, byte[]> fingerprints = new LinkedHashMap<>();
        try {
            addKey(protectors, fingerprints, activeKeyId, activeBase64);
            if (!previous.isBlank()) {
                String[] entries = previous.split(",", -1);
                if (entries.length >= MAX_KEYS) {
                    throw new IllegalStateException("Too many memory decryption keys");
                }
                for (String entry : entries) {
                    int separator = entry.indexOf(':');
                    if (separator < 1 || separator == entry.length() - 1 ||
                            entry.indexOf(':', separator + 1) != -1) {
                        throw new IllegalStateException("Invalid memory keyring entry");
                    }
                    addKey(protectors, fingerprints, entry.substring(0, separator),
                            entry.substring(separator + 1));
                }
            }
            return new OwnerBoundMemoryKeyring(activeKeyId, protectors);
        } catch (RuntimeException ex) {
            for (AeadFieldProtector cipher : protectors.values()) cipher.close();
            throw ex;
        } finally {
            for (byte[] fingerprint : fingerprints.values()) {
                Arrays.fill(fingerprint, (byte) 0);
            }
        }
    }

    public synchronized String encrypt(byte[] plaintext, byte[] context) {
        requireOpen();
        return protectors.get(activeKeyId).encrypt(plaintext, context);
    }

    /** Never tries another key after an unknown ID or a failed AEAD tag. */
    public synchronized byte[] decrypt(String envelope, byte[] context) {
        requireOpen();
        if (envelope == null || envelope.length() > 1500000) {
            throw new SecurityException("ENCRYPTED_FIELD_REJECTED");
        }
        String[] parts = envelope.split("\\.", -1);
        if (parts.length != 4 || !VERSION.equals(parts[0]) || !validId(parts[1])) {
            throw new SecurityException("ENCRYPTED_FIELD_REJECTED");
        }
        AeadFieldProtector protector = protectors.get(parts[1]);
        if (protector == null) throw new SecurityException("ENCRYPTED_FIELD_REJECTED");
        return protector.decrypt(envelope, context);
    }

    public boolean containsKeyId(String id) {
        return id != null && protectors.containsKey(id);
    }

    private static void addKey(Map<String, AeadFieldProtector> protectors,
                               Map<String, byte[]> fingerprints, String id, String encoded) {
        if (!validId(id) || protectors.containsKey(id)) {
            throw new IllegalStateException("Duplicate or invalid memory key ID");
        }
        byte[] raw = decodeKey(encoded);
        try {
            for (byte[] earlier : fingerprints.values()) {
                if (MessageDigest.isEqual(raw, earlier)) {
                    throw new IllegalStateException("Memory key material must differ across key IDs");
                }
            }
            protectors.put(id, new AeadFieldProtector(id, raw));
            fingerprints.put(id, raw.clone());
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }

    private static byte[] decodeKey(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("Owner-bound memory key must be externally provisioned");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Owner-bound memory key must be valid Base64");
        }
        if (raw.length != 32) {
            Arrays.fill(raw, (byte) 0);
            throw new IllegalStateException("Owner-bound memory key must decode to exactly 32 bytes");
        }
        return raw;
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,24}");
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("MEMORY_KEYRING_CLOSED");
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            for (AeadFieldProtector cipher : protectors.values()) cipher.close();
            closed = true;
        }
    }
}
