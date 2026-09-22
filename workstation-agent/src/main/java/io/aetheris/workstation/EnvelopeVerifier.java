package io.aetheris.workstation;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

public final class EnvelopeVerifier implements AutoCloseable {
    private final UUID hostId;
    private final byte[] signingKey;
    private final ReplayCache replayCache;

    public EnvelopeVerifier(UUID hostId, byte[] signingKey, ReplayCache replayCache) {
        if (signingKey == null || signingKey.length < 32) throw new IllegalArgumentException("Signing key must be at least 32 bytes");
        this.hostId = Objects.requireNonNull(hostId);
        this.signingKey = signingKey.clone();
        this.replayCache = Objects.requireNonNull(replayCache);
    }

    public void verifyAndClaim(HostCommandEnvelope envelope) {
        verifyAndClaim(envelope, Instant.now());
    }

    void verifyAndClaim(HostCommandEnvelope envelope, Instant now) {
        Objects.requireNonNull(envelope, "Host command envelope is required");
        if (!hostId.equals(envelope.hostId())) throw new SecurityException("Host id mismatch");
        if (!"REMOTE".equalsIgnoreCase(envelope.mode())) throw new SecurityException("Host agent accepts REMOTE envelopes only");
        if (envelope.commandId() == null || envelope.issuedAt() == null || envelope.expiresAt() == null) {
            throw new IllegalArgumentException("Command id and timestamps are required");
        }
        if (envelope.issuedAt().isAfter(now.plusSeconds(5))) throw new SecurityException("Host command was issued too far in the future");
        if (!envelope.expiresAt().isAfter(now)) throw new SecurityException("Host command envelope expired");
        Duration ttl = Duration.between(envelope.issuedAt(), envelope.expiresAt());
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofSeconds(60)) > 0) {
            throw new SecurityException("Host command TTL exceeds policy");
        }
        validateArguments(envelope.arguments());
        String expected = hmac(canonical(envelope));
        if (!MessageDigest.isEqual(signatureBytes(envelope.signature()), signatureBytes(expected))) {
            throw new SecurityException("Host command signature mismatch");
        }
        replayCache.claim(envelope.commandId(), envelope.expiresAt(), now);
    }

    static String canonical(HostCommandEnvelope e) {
        return e.commandId() + "|" + e.hostId() + "|" + e.capability() + "|" + e.action() + "|" +
                e.issuedAt() + "|" + e.expiresAt() + "|" + new TreeMap<>(e.arguments());
    }

    private void validateArguments(Map<String, Object> args) {
        if (args.size() > 16) throw new IllegalArgumentException("Too many host command arguments");
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().matches("[A-Za-z0-9._-]{1,64}")) {
                throw new IllegalArgumentException("Invalid host command argument name");
            }
            Object value = entry.getValue();
            if (!(value == null || value instanceof String || value instanceof Number || value instanceof Boolean)) {
                throw new IllegalArgumentException("Nested or complex host command arguments are not allowed");
            }
            if (value instanceof String text && text.length() > 4096) throw new IllegalArgumentException("Host command argument is too long");
        }
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to verify host command", e);
        }
    }

    private byte[] signatureBytes(String value) {
        try { return Base64.getDecoder().decode(value == null ? "" : value); }
        catch (Exception ignored) { return new byte[0]; }
    }

    @Override public void close() { Arrays.fill(signingKey, (byte) 0); }
}
