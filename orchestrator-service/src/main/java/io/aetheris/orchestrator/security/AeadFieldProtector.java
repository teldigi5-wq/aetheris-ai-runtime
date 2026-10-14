package io.aetheris.orchestrator.security;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Unregistered field-encryption primitive for future reviewed storage use.
 *
 * Requires an EXTERNAL 256-bit key: never embedded in the application,
 * auto-generated on startup or defaulted to development credentials.
 * Caller AAD MUST bind exact owner/project/table/record/field context.
 *
 * This is not yet wired to JPA, backups, audit logs or Syntra memory and
 * must NOT be described as "the entire database is encrypted".
 */
public final class AeadFieldProtector implements AutoCloseable {
    private static final String VERSION = "A1";
    private static final int MAX_FIELD_BYTES = 1024 * 1024;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final String keyId;
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();
    private boolean closed;

    public AeadFieldProtector(String keyId, byte[] externalKey) {
        if (keyId == null || !keyId.matches("[A-Za-z0-9_-]{1,24}")
                || externalKey == null || externalKey.length != 32) {
            throw new IllegalArgumentException("Invalid external AEAD key configuration");
        }
        this.keyId = keyId;
        this.key = externalKey.clone();
    }

    /** Returns version.keyID.randomNonce.ciphertext+tag. Not a plaintext fallback. */
    public synchronized String encrypt(byte[] plaintext, byte[] authenticatedContext) {
        requireOpen();
        validateContext(authenticatedContext);
        if (plaintext == null || plaintext.length > MAX_FIELD_BYTES) {
            throw new IllegalArgumentException("Field size invalid");
        }
        byte[] nonce = new byte[IV_BYTES];
        random.nextBytes(nonce);
        try {
            byte[] cipher = crypt(Cipher.ENCRYPT_MODE, plaintext, nonce, authenticatedContext);
            return VERSION + "." + keyId + "." + encode(nonce) + "." + encode(cipher);
        } catch (GeneralSecurityException error) {
            throw new SecurityException("ENCRYPTION_UNAVAILABLE");
        } finally {
            Arrays.fill(nonce, (byte) 0);
        }
    }

    /** Fails closed on any wrong key, record context, tag, encoding or version. */
    public synchronized byte[] decrypt(String envelope, byte[] authenticatedContext) {
        requireOpen();
        validateContext(authenticatedContext);
        if (envelope == null || envelope.length() > (MAX_FIELD_BYTES * 2) + 256) {
            throw new SecurityException("ENCRYPTED_FIELD_REJECTED");
        }
        String[] parts = envelope.split("\\.", -1);
        if (parts.length != 4 || !VERSION.equals(parts[0]) ||
                !keyId.equals(parts[1])) {
            throw new SecurityException("ENCRYPTED_FIELD_REJECTED");
        }
        byte[] nonce = null;
        byte[] encrypted = null;
        try {
            nonce = Base64.getUrlDecoder().decode(parts[2]);
            encrypted = Base64.getUrlDecoder().decode(parts[3]);
            if (nonce.length != IV_BYTES || encrypted.length < TAG_BITS / 8 ||
                    encrypted.length > MAX_FIELD_BYTES + TAG_BITS / 8) {
                throw new SecurityException("ENCRYPTED_FIELD_REJECTED");
            }
            return crypt(Cipher.DECRYPT_MODE, encrypted, nonce, authenticatedContext);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new SecurityException("ENCRYPTED_FIELD_REJECTED");
        } finally {
            if (nonce != null) Arrays.fill(nonce, (byte) 0);
            if (encrypted != null) Arrays.fill(encrypted, (byte) 0);
        }
    }

    private byte[] crypt(int mode, byte[] input, byte[] nonce, byte[] aad)
            throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(input);
    }

    private static String encode(byte[] input) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(input);
    }

    private static void validateContext(byte[] aad) {
        if (aad == null || aad.length < 8 || aad.length > 512) {
            throw new IllegalArgumentException("Owner-bound authenticated context required");
        }
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("AEAD_FIELD_KEY_CLOSED");
    }

    @Override
    public synchronized void close() {
        Arrays.fill(key, (byte) 0);
        closed = true;
    }
}
