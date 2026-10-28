package io.aetheris.orchestrator.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Standalone, no-network Identity-issued RS256 principal verifier.
 *
 * Deliberately NOT a Spring bean, request filter or route grant. Until
 * persistence-backed task/mission ownership is proven, the separate early
 * HTTP lockdown must remain in force. A JWT identity or orchestrator scope
 * alone does NOT confer ownership of any particular object.
 */
public final class RuntimePinnedPrincipalVerifier {
    private static final Map<String, Set<String>> ROLE_SCOPE_CEILINGS = Map.of(
        "ADMIN", Set.of("users:read", "users:write", "services:read",
            "identity:read", "identity:write", "orchestrator:read", "orchestrator:write"),
        "DEVELOPER", Set.of("users:read", "users:write", "services:read",
            "identity:read", "orchestrator:read", "orchestrator:write"),
        "API_CONSUMER", Set.of("users:read", "services:read", "orchestrator:read")
    );
    private final RSAPublicKey trustedKey;
    private final String expectedKid;
    private final String expectedIssuer;
    private final String expectedAudience;
    private final Clock clock;

    /**
     * Capability: can only be created by this verifier after a real, pinned
     * RS256 signature, issuer and audience check. Neither an HTTP body nor
     * another Java caller can manufacture this "verified" type directly.
     * Even this type does NOT prove tenant or project membership.
     */
    public static final class VerifiedPrincipal {
        private final long identityUid;
        private final String subject;
        private final String role;
        private final Set<String> scopes;

        private VerifiedPrincipal(long identityUid, String subject,
                                  String role, Set<String> scopes) {
            if (identityUid <= 0 || subject == null || subject.isBlank()
                    || !ROLE_SCOPE_CEILINGS.containsKey(role)
                    || scopes == null || scopes.isEmpty()
                    || !ROLE_SCOPE_CEILINGS.get(role).containsAll(scopes)) {
                throw new IllegalArgumentException("Invalid independently signed principal");
            }
            this.identityUid = identityUid;
            this.subject = subject;
            this.role = role;
            this.scopes = Set.copyOf(scopes);
        }

        public long identityUid() { return identityUid; }
        public String subject() { return subject; }
        public String role() { return role; }
        public Set<String> scopes() { return scopes; }

        /** Scope is only a capability ceiling: it never grants object ownership. */
        public boolean hasScope(String scope) {
            return scope != null && scopes.contains(scope);
        }
    }

    public RuntimePinnedPrincipalVerifier(String publicKeyPem, String expectedKid,
                                          String expectedIssuer, String expectedAudience,
                                          Clock clock) {
        if (expectedKid == null || !expectedKid.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
                || !validName(expectedIssuer, 255) || !validName(expectedAudience, 128)) {
            throw new IllegalArgumentException("Runtime RS256 requires pinned key ID, issuer and audience");
        }
        this.trustedKey = parseRsaKey(publicKeyPem);
        this.expectedKid = expectedKid;
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public VerifiedPrincipal verifyBearer(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.length() > 8192
                || !authorizationHeader.startsWith("Bearer ")
                || authorizationHeader.indexOf(' ', 7) != -1) {
            throw new IllegalArgumentException("Invalid bearer envelope");
        }
        String token = authorizationHeader.substring(7);
        if (token.isEmpty() || !token.equals(token.strip())) {
            throw new IllegalArgumentException("Invalid bearer token");
        }
        try {
            // Key is operator-pinned, never obtained from kid/jku/jwks or URL.
            // The signed claims are inspected only after cryptographic verification.
            var signed = Jwts.parser().verifyWith(trustedKey).build()
                .parseSignedClaims(token);
            if (!"RS256".equals(signed.getHeader().getAlgorithm())
                    || !expectedKid.equals(signed.getHeader().getKeyId())) {
                throw new IllegalArgumentException("Unpinned signed JWT algorithm or key ID");
            }
            Claims claims = signed.getPayload();
            if (!expectedIssuer.equals(claims.getIssuer())
                    || claims.getAudience() == null
                    || claims.getAudience().size() != 1
                    || !claims.getAudience().contains(expectedAudience)) {
                throw new IllegalArgumentException("Invalid signed issuer or target audience");
            }
            String subject = claims.getSubject();
            Object uid = claims.get("uid");
            Object rawRole = claims.get("role");
            Object rawScopes = claims.get("scopes");
            if (!validName(subject, 254)
                    || !(uid instanceof Long || uid instanceof Integer)
                    || !(rawRole instanceof String role)
                    || !ROLE_SCOPE_CEILINGS.containsKey(rawRole)
                    || !(rawScopes instanceof Collection<?> entries)
                    || entries.isEmpty() || entries.size() > 16
                    || claims.getIssuedAt() == null || claims.getExpiration() == null) {
                throw new IllegalArgumentException("Malformed signed principal claims");
            }
            long number = ((Number) uid).longValue();
            if (number <= 0) {
                throw new IllegalArgumentException("Invalid signed Identity UID");
            }
            Instant issued = claims.getIssuedAt().toInstant();
            Instant expires = claims.getExpiration().toInstant();
            Instant now = clock.instant();
            if (issued.isAfter(now.plusSeconds(30)) || expires.isAfter(issued.plusSeconds(86400))
                    || !expires.isAfter(issued) || !expires.isAfter(now)) {
                throw new IllegalArgumentException("Invalid signed token lifetime");
            }
            Set<String> scopes = new LinkedHashSet<>();
            for (Object item : (Collection<?>) rawScopes) {
                if (!(item instanceof String scope) || scope.isBlank()
                        || !scope.equals(scope.strip()) || !scopes.add(scope)) {
                    throw new IllegalArgumentException("Invalid signed scope");
                }
            }
            if (!ROLE_SCOPE_CEILINGS.get(rawRole).containsAll(scopes)) {
                throw new IllegalArgumentException("Signed role-scope ceiling exceeded");
            }
            return new VerifiedPrincipal(number, subject, (String) rawRole, scopes);
        } catch (RuntimeException untrusted) {
            // Never expose signature, private JWT fields or configuration in
            // an error sent to client or caller logs.
            throw new IllegalArgumentException("Rejected signed Identity principal");
        }
    }

    private static boolean validName(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength
            && value.equals(value.strip()) && value.chars().noneMatch(Character::isISOControl);
    }

    private static RSAPublicKey parseRsaKey(String pem) {
        if (pem == null || !pem.startsWith("-----BEGIN PUBLIC KEY-----")
                || !pem.stripTrailing().endsWith("-----END PUBLIC KEY-----")) {
            throw new IllegalArgumentException("Runtime public signing key is missing or invalid");
        }
        try {
            String base64 = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
            byte[] bytes = Base64.getDecoder().decode(base64);
            var key = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(bytes));
            if (!(key instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() < 2048) {
                throw new IllegalArgumentException("Runtime RS256 signing key must be RSA 2048+");
            }
            return rsa;
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Invalid pinned runtime public signing key");
        }
    }
}
