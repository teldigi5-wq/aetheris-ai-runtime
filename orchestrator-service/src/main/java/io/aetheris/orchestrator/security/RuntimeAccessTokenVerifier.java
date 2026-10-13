package io.aetheris.orchestrator.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Isolated runtime-side signature validation matching the platform identity
 * issuer's JJWT 0.12.6 HS256 access-token format.
 *
 * NOT installed as a Spring bean, servlet filter or web endpoint. The entire
 * task/mission HTTP quarantine stays active until durable creation ownership,
 * full endpoint authorization and cross-account SSE filtering are verified.
 *
 * Never trust X-Aetheris-* headers or unsigned JWT claim decodes for identity.
 */
public final class RuntimeAccessTokenVerifier {
    public static final String READ_SCOPE = "orchestrator:read";
    public static final String WRITE_SCOPE = "orchestrator:write";
    private static final String INSECURE_DEV_DEFAULT = "change-me-local-development-secret-32chars";
    private static final long MAX_TOKEN_LIFETIME_SECONDS = 24 * 60 * 60;
    private final SecretKey verificationKey;

    /** Requires the explicitly provisioned same signing secret as identity. */
    public RuntimeAccessTokenVerifier(String explicitlyConfiguredSecret) {
        if (explicitlyConfiguredSecret == null
                || explicitlyConfiguredSecret.equals(INSECURE_DEV_DEFAULT)
                || explicitlyConfiguredSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("Verified signing key configuration required");
        }
        this.verificationKey = Keys.hmacShaKeyFor(
                explicitlyConfiguredSecret.getBytes(StandardCharsets.UTF_8));
    }

    public VerifiedPrincipal verify(String signedAccessToken, String requiredScope) {
        if (signedAccessToken == null || signedAccessToken.isBlank()
                || signedAccessToken.length() > 8192
                || (!READ_SCOPE.equals(requiredScope) && !WRITE_SCOPE.equals(requiredScope))) {
            throw new RejectedToken();
        }
        final Claims claims;
        try {
            Jws<Claims> parsed = Jwts.parser()
                    .verifyWith(verificationKey)
                    .build()
                    .parseSignedClaims(signedAccessToken);
            // The current identity issuer chooses HS256 for its 32-byte key.
            // Refuse algorithm substitution even if the library could verify it.
            if (!"HS256".equals(parsed.getHeader().getAlgorithm())) {
                throw new RejectedToken();
            }
            claims = parsed.getPayload();
        } catch (JwtException | IllegalArgumentException failure) {
            throw new RejectedToken();
        }
        try {
            String email = claims.getSubject();
            String role = claims.get("role", String.class);
            Object uid = claims.get("uid");
            Collection<?> rawScopes = claims.get("scopes", Collection.class);
            Date expiresAt = claims.getExpiration();
            Date issuedAt = claims.getIssuedAt();
            if (email == null || email.length() > 180 || email.isBlank() ||
                    !email.contains("@") || email.chars().anyMatch(Character::isWhitespace) ||
                    !Set.of("ADMIN", "DEVELOPER", "API_CONSUMER").contains(role) ||
                    !(uid instanceof Integer || uid instanceof Long) ||
                    ((Number) uid).longValue() <= 0L ||
                    rawScopes == null || rawScopes.isEmpty() || rawScopes.size() > 64 ||
                    expiresAt == null || issuedAt == null) {
                throw new RejectedToken();
            }
            Instant issued = issuedAt.toInstant();
            Instant expires = expiresAt.toInstant();
            Instant now = Instant.now();
            if (issued.isAfter(now.plusSeconds(30)) || !expires.isAfter(now) ||
                    !expires.isAfter(issued) ||
                    Duration.between(issued, expires).getSeconds() > MAX_TOKEN_LIFETIME_SECONDS) {
                throw new RejectedToken();
            }

            Set<String> verifiedScopes = new HashSet<>();
            for (Object item : rawScopes) {
                if (!(item instanceof String scope) || scope.length() > 80 ||
                        !scope.matches("[a-z][a-z0-9-]{0,38}:[a-z][a-z0-9-]{0,38}")) {
                    throw new RejectedToken();
                }
                verifiedScopes.add(scope);
            }
            if (!verifiedScopes.contains(requiredScope)) {
                throw new RejectedToken();
            }
            return new VerifiedPrincipal(((Number) uid).longValue(), email, role,
                    Set.copyOf(verifiedScopes), expires);
        } catch (RuntimeException invalidClaims) {
            // No claim details, tokens, signature bytes or exception causes
            // should escape into any desktop IPC/logging boundary.
            throw new RejectedToken();
        }
    }

    public static final class VerifiedPrincipal {
        private final long identityAccountId;
        private final String email;
        private final String role;
        private final Set<String> scopes;
        private final Instant expiresAt;

        private VerifiedPrincipal(long identityAccountId, String email, String role,
                                  Set<String> scopes, Instant expiresAt) {
            this.identityAccountId = identityAccountId;
            this.email = Objects.requireNonNull(email);
            this.role = Objects.requireNonNull(role);
            this.scopes = Set.copyOf(scopes);
            this.expiresAt = Objects.requireNonNull(expiresAt);
        }
        public long identityAccountId() { return identityAccountId; }
        public boolean hasScope(String scope) { return scopes.contains(scope); }
        public Instant expiresAt() { return expiresAt; }
        // Purposefully no Debug/loggable string, JWT getter or serialized principal.
    }

    public static final class RejectedToken extends RuntimeException {
        public RejectedToken() {
            super("ACCESS_TOKEN_REJECTED", null, false, false);
        }
    }
}
