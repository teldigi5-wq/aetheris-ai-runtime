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
 * Review-only independently pinned synthetic workload identity verifier.
 *
 * A verified JWT subject is NOT a workload authenticated by mTLS, SPIFFE,
 * a hardware key, or the actual resource process. Its bearer assertion may
 * be replayed, so it can only establish two-signer claim binding in CI.
 * No Spring bean, HTTP filter, actual runtime route or production connector.
 */
public final class RuntimePinnedWorkloadIdentityVerifier {
    private static final String PURPOSE = "runtime-workload-review";
    private final RSAPublicKey trustedKey;
    private final String expectedKid;
    private final String expectedIssuer;
    private final String expectedAudience;
    private final Clock clock;

    public static final class VerifiedWorkload {
        private final String subject;
        private final Instant issuedAt;
        private final Instant expiresAt;
        private final Clock clock;

        private VerifiedWorkload(String subject, Instant issuedAt,
                Instant expiresAt, Clock clock) {
            this.subject = subject;
            this.issuedAt = issuedAt;
            this.expiresAt = expiresAt;
            this.clock = clock;
        }

        public String subject() { return subject; }

        public boolean isFresh() {
            Instant now = clock.instant();
            return !now.isBefore(issuedAt) && expiresAt.isAfter(now);
        }
    }

    public RuntimePinnedWorkloadIdentityVerifier(String publicKeyPem,
            String expectedKid, String expectedIssuer,
            String expectedAudience, Clock clock) {
        if (expectedKid == null
                || !expectedKid.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
                || !validName(expectedIssuer, 255)
                || !validName(expectedAudience, 128)) {
            throw new IllegalArgumentException("Invalid pinned workload trust configuration");
        }
        this.trustedKey = parseRsaPublicKey(publicKeyPem);
        this.expectedKid = expectedKid;
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public VerifiedWorkload verifySignedWorkload(String jwt) {
        if (jwt == null || jwt.isEmpty() || jwt.length() > 4096
                || !jwt.equals(jwt.strip()) || jwt.indexOf(' ') >= 0) {
            throw new IllegalArgumentException("Invalid signed workload envelope");
        }
        try {
            var signed = Jwts.parser().verifyWith(trustedKey).build()
                .parseSignedClaims(jwt);
            if (!"RS256".equals(signed.getHeader().getAlgorithm())
                    || !expectedKid.equals(signed.getHeader().getKeyId())) {
                throw new IllegalArgumentException("Unpinned workload signing metadata");
            }
            Claims claims = signed.getPayload();
            if (!expectedIssuer.equals(claims.getIssuer())
                    || claims.getAudience() == null
                    || claims.getAudience().size() != 1
                    || !claims.getAudience().contains(expectedAudience)
                    || !PURPOSE.equals(claims.get("purpose"))
                    || !"runtime".equals(claims.get("resource"))
                    || !validSubject(claims.getSubject())
                    || claims.getIssuedAt() == null
                    || claims.getExpiration() == null) {
                throw new IllegalArgumentException("Invalid signed workload domain");
            }
            Instant issued = claims.getIssuedAt().toInstant();
            Instant expires = claims.getExpiration().toInstant();
            Instant now = clock.instant();
            if (issued.isAfter(now) || !expires.isAfter(issued)
                    || expires.isAfter(issued.plusSeconds(90))
                    || !expires.isAfter(now)) {
                throw new IllegalArgumentException("Invalid workload assertion lifetime");
            }
            return new VerifiedWorkload(claims.getSubject(), issued, expires, clock);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Rejected pinned signed workload");
        }
    }

    private static boolean validSubject(String subject) {
        return subject != null
            && subject.matches("[a-z][a-z0-9-]{0,95}");
    }

    private static boolean validName(String name, int limit) {
        return name != null && !name.isBlank() && name.length() <= limit
            && name.equals(name.strip())
            && name.chars().noneMatch(Character::isISOControl);
    }

    private static RSAPublicKey parseRsaPublicKey(String pem) {
        if (pem == null || !pem.startsWith("-----BEGIN PUBLIC KEY-----")
                || !pem.stripTrailing().endsWith("-----END PUBLIC KEY-----")) {
            throw new IllegalArgumentException("Missing pinned RSA workload key");
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
                throw new IllegalArgumentException("Weak workload signing key");
            }
            return rsa;
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Invalid pinned workload public key");
        }
    }
}
