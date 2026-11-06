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

/**
 * Review-only cryptographic provenance for synthetic resource-fence leases.
 *
 * NOT a Spring bean, coordinator client, mTLS workload identity, quorum vote,
 * revocation reader, or permission to open any Runtime route. A valid bearer
 * token may be replayed on a stale writable primary until expiry. The key is
 * independently pinned; no jku/jwks or user-provided key is ever trusted.
 */
public final class RuntimePinnedLocalFenceLeaseVerifier {
    private static final String PURPOSE = "runtime-local-fence-review";
    private final RSAPublicKey trustedKey;
    private final String expectedKid;
    private final String expectedIssuer;
    private final String expectedAudience;
    private final Clock clock;

    /** An immutable, expiry-bound type that only this verifier can create. */
    public static final class VerifiedLease {
        private final String endpoint;
        private final long term;
        private final String boundWorkload;
        private final Instant issuedAt;
        private final Instant expiresAt;
        private final Clock clock;

        private VerifiedLease(String endpoint, long term, String boundWorkload,
                Instant issuedAt, Instant expiresAt, Clock clock) {
            this.endpoint = endpoint;
            this.term = term;
            this.boundWorkload = boundWorkload;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
            this.clock = clock;
        }

        public String endpoint() { return endpoint; }
        public long term() { return term; }

        /** Signed name matching two independently verified assertions only. */
        public boolean isBoundTo(
                RuntimePinnedWorkloadIdentityVerifier.VerifiedWorkload workload) {
            return workload != null && workload.isFresh() && isFresh()
                && boundWorkload.equals(workload.subject());
        }

        /** Previously verified signatures are not permanently live grants. */
        public boolean isFresh() {
            Instant now = clock.instant();
            return !now.isBefore(issuedAt) && expiresAt.isAfter(now);
        }
    }

    public RuntimePinnedLocalFenceLeaseVerifier(String publicKeyPem,
            String expectedKid, String expectedIssuer,
            String expectedAudience, Clock clock) {
        if (expectedKid == null
                || !expectedKid.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
                || !validName(expectedIssuer, 255)
                || !validName(expectedAudience, 128)) {
            throw new IllegalArgumentException("Invalid pinned fence trust configuration");
        }
        this.trustedKey = parseRsaPublicKey(publicKeyPem);
        this.expectedKid = expectedKid;
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Verify an operator-pinned RS256 compact JWT (NOT an arbitrary caller
     * endpoint/term). The two signed workload claims must be matched at
     * the consuming probe. Both assertions are replayable bearer artefacts,
     * to rely on for cross-node leadership, HTTP, task execution or SSE.
     */
    public VerifiedLease verifySignedLease(String jwt) {
        if (jwt == null || jwt.isEmpty() || jwt.length() > 4096
                || !jwt.equals(jwt.strip()) || jwt.indexOf(' ') >= 0) {
            throw new IllegalArgumentException("Invalid fence lease envelope");
        }
        try {
            var signed = Jwts.parser().verifyWith(trustedKey).build()
                .parseSignedClaims(jwt);
            if (!"RS256".equals(signed.getHeader().getAlgorithm())
                    || !expectedKid.equals(signed.getHeader().getKeyId())) {
                throw new IllegalArgumentException("Unpinned fence signature metadata");
            }
            Claims claims = signed.getPayload();
            if (!expectedIssuer.equals(claims.getIssuer())
                    || claims.getAudience() == null
                    || claims.getAudience().size() != 1
                    || !claims.getAudience().contains(expectedAudience)
                    || !PURPOSE.equals(claims.get("purpose"))
                    || !"runtime".equals(claims.get("resource"))) {
                throw new IllegalArgumentException("Invalid signed authority domain");
            }
            Object rawEndpoint = claims.get("endpoint");
            Object rawTerm = claims.get("term");
            Object rawWorkload = claims.get("workload");
            if (!(rawEndpoint instanceof String endpoint) || !validEndpoint(endpoint)
                    || !(rawWorkload instanceof String boundWorkload)
                    || !validWorkloadSubject(boundWorkload)
                    || !(rawTerm instanceof Integer || rawTerm instanceof Long)
                    || claims.getIssuedAt() == null || claims.getExpiration() == null) {
                throw new IllegalArgumentException("Missing signed lease fields");
            }
            long term = ((Number) rawTerm).longValue();
            Instant issued = claims.getIssuedAt().toInstant();
            Instant expires = claims.getExpiration().toInstant();
            Instant now = clock.instant();
            if (term <= 0 || issued.isAfter(now)
                    || !expires.isAfter(issued)
                    || expires.isAfter(issued.plusSeconds(90))
                    || !expires.isAfter(now)) {
                throw new IllegalArgumentException("Invalid signed lease lifetime or term");
            }
            return new VerifiedLease(endpoint, term, boundWorkload,
                issued, expires, clock);
        } catch (RuntimeException invalid) {
            // Fail closed without revealing signed identities, tokens or keys.
            throw new IllegalArgumentException("Rejected pinned fence lease");
        }
    }

    private static boolean validWorkloadSubject(String subject) {
        return subject != null && subject.matches("[a-z][a-z0-9-]{0,95}");
    }

    private static boolean validEndpoint(String endpoint) {
        return endpoint != null
            && endpoint.matches("[a-z0-9][a-z0-9-]{0,95}");
    }

    private static boolean validName(String value, int limit) {
        return value != null && !value.isBlank() && value.length() <= limit
            && value.equals(value.strip())
            && value.chars().noneMatch(Character::isISOControl);
    }

    private static RSAPublicKey parseRsaPublicKey(String pem) {
        if (pem == null || !pem.startsWith("-----BEGIN PUBLIC KEY-----")
                || !pem.stripTrailing().endsWith("-----END PUBLIC KEY-----")) {
            throw new IllegalArgumentException("Missing pinned RSA fence key");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", ""));
            var key = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(bytes));
            if (!(key instanceof RSAPublicKey rsa)
                    || rsa.getModulus().bitLength() < 2048) {
                throw new IllegalArgumentException("Weak fence signing key");
            }
            return rsa;
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Invalid pinned fence public key");
        }
    }
}
