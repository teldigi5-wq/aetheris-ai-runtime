package io.aetheris.orchestrator.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Independently pinned synthetic resource-closure acknowledgement proof.
 *
 * REVIEW ONLY: Not a Spring bean, remote signer, credential provisioner,
 * workload authenticator, fencing controller, quorum vote or API grant.
 * In particular, a signature does NOT prove that a different physical
 * old primary is fenced. Bearer copies remain replayable without a
 * separate durable resource-side one-time-consumption mechanism.
 */
public final class RuntimePinnedClosureAckVerifier {
    private static final String PURPOSE = "runtime-resource-closure-review";
    private static final String NODE_PATTERN = "[a-z0-9][a-z0-9-]{0,95}";

    private final RSAPublicKey trustedKey;
    private final String expectedKid;
    private final String expectedIssuer;
    private final String expectedAudience;
    private final Clock clock;

    /** Signature-derived fact, not authority to release another resource. */
    public static final class VerifiedAck {
        private final String node;
        private final long term;
        private final UUID receipt;
        private final UUID challenge;
        private final Instant issuedAt;
        private final Instant expiresAt;
        private final Clock clock;

        private VerifiedAck(String node, long term, UUID receipt,
                UUID challenge, Instant issuedAt, Instant expiresAt,
                Clock clock) {
            this.node = node;
            this.term = term;
            this.receipt = receipt;
            this.challenge = challenge;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
            this.clock = clock;
        }

        public boolean isFresh() {
            Instant now = clock.instant();
            return !now.isBefore(issuedAt) && expiresAt.isAfter(now);
        }

        /** Compare with caller-pinned expectations; NEVER infer these from JWT. */
        public boolean matches(String expectedNode, long expectedTerm,
                UUID expectedReceipt, UUID expectedChallenge) {
            return isFresh() && node.equals(expectedNode)
                && term == expectedTerm && term > 0
                && receipt.equals(expectedReceipt)
                && challenge.equals(expectedChallenge);
        }

        String node() { return node; }
        long term() { return term; }
        UUID receipt() { return receipt; }
        UUID challenge() { return challenge; }
    }

    public RuntimePinnedClosureAckVerifier(String publicKeyPem,
            String expectedKid, String expectedIssuer,
            String expectedAudience, Clock clock) {
        if (expectedKid == null
                || !expectedKid.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
                || !validName(expectedIssuer, 255)
                || !validName(expectedAudience, 128)) {
            throw new IllegalArgumentException("Invalid pinned ACK configuration");
        }
        this.trustedKey = parseKey(publicKeyPem);
        this.expectedKid = expectedKid;
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public VerifiedAck verifySignedAck(String jwt) {
        if (jwt == null || jwt.isEmpty() || jwt.length() > 4096
                || !jwt.equals(jwt.strip()) || jwt.indexOf(' ') >= 0) {
            throw new IllegalArgumentException("Invalid ACK envelope");
        }
        try {
            var signed = Jwts.parser().verifyWith(trustedKey).build()
                .parseSignedClaims(jwt);
            if (!"RS256".equals(signed.getHeader().getAlgorithm())
                    || !expectedKid.equals(signed.getHeader().getKeyId())) {
                throw new IllegalArgumentException("Unpinned ACK metadata");
            }
            Claims claims = signed.getPayload();
            Object rawNode = claims.get("node");
            Object rawTerm = claims.get("term");
            Object rawReceipt = claims.get("receipt");
            Object rawChallenge = claims.get("challenge");
            if (!expectedIssuer.equals(claims.getIssuer())
                    || claims.getAudience() == null
                    || claims.getAudience().size() != 1
                    || !claims.getAudience().contains(expectedAudience)
                    || !PURPOSE.equals(claims.get("purpose"))
                    || !"runtime".equals(claims.get("resource"))
                    || !(rawNode instanceof String node)
                    || !node.matches(NODE_PATTERN)
                    || !node.equals(claims.getSubject())
                    || !(rawTerm instanceof Integer || rawTerm instanceof Long)
                    || !(rawReceipt instanceof String receiptValue)
                    || !(rawChallenge instanceof String challengeValue)
                    || claims.getIssuedAt() == null
                    || claims.getExpiration() == null) {
                throw new IllegalArgumentException("Invalid signed ACK claims");
            }
            long term = ((Number) rawTerm).longValue();
            UUID receipt = parseCanonicalUuid(receiptValue);
            UUID challenge = parseCanonicalUuid(challengeValue);
            Instant issued = claims.getIssuedAt().toInstant();
            Instant expires = claims.getExpiration().toInstant();
            Instant now = clock.instant();
            if (term <= 0 || issued.isAfter(now)
                    || !expires.isAfter(issued)
                    || expires.isAfter(issued.plusSeconds(90))
                    || !expires.isAfter(now)) {
                throw new IllegalArgumentException("Invalid ACK lifetime or term");
            }
            return new VerifiedAck(node, term, receipt, challenge,
                issued, expires, clock);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Rejected pinned closure ACK");
        }
    }

    private static UUID parseCanonicalUuid(String value) {
        UUID uuid = UUID.fromString(value);
        if (!uuid.toString().equals(value)) {
            throw new IllegalArgumentException("Noncanonical ACK UUID");
        }
        return uuid;
    }

    private static boolean validName(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max
            && value.equals(value.strip())
            && value.chars().noneMatch(Character::isISOControl);
    }

    private static RSAPublicKey parseKey(String pem) {
        if (pem == null || !pem.startsWith("-----BEGIN PUBLIC KEY-----")
                || !pem.stripTrailing().endsWith("-----END PUBLIC KEY-----")) {
            throw new IllegalArgumentException("Missing pinned closure ACK key");
        }
        try {
            byte[] encoded = Base64.getDecoder().decode(pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", ""));
            var publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(encoded));
            if (!(publicKey instanceof RSAPublicKey rsa)
                    || rsa.getModulus().bitLength() < 2048) {
                throw new IllegalArgumentException("Weak ACK signing key");
            }
            return rsa;
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Invalid pinned ACK RSA key");
        }
    }
}
