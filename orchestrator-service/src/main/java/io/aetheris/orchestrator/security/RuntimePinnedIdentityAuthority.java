package io.aetheris.orchestrator.security;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.Objects;

/**
 * S0-E6A production-created Identity signature verification capability.
 *
 * Not an HTTP filter or authorization grant. The real early Task/Mission 404
 * and all-/api quarantine continue denying even an independently signed JWT.
 *
 * No configured key: verification is unavailable and MUST fail closed.
 * Partial/malformed configuration: Spring startup fails closed.
 * Complete operator-pinned config: only RuntimePinnedPrincipalVerifier can
 * create a verified principal after RS256, key ID, issuer and audience checks.
 *
 * Do not use a frontend, gateway forwarding header, dynamic JWKS URL or
 * per-request key lookup for this authority.
 */
@Component
public final class RuntimePinnedIdentityAuthority {
    private static final String PREFIX = "aetheris.runtime.identity.";
    private static final int MAX_PEM_B64_LENGTH = 16384;

    private final RuntimePinnedPrincipalVerifier verifier;

    public RuntimePinnedIdentityAuthority(Environment environment) {
        Objects.requireNonNull(environment, "environment");
        String key = configured(environment, PREFIX + "public-key-pem-base64");
        String kid = configured(environment, PREFIX + "key-id");
        String issuer = configured(environment, PREFIX + "issuer");
        String audience = configured(environment, PREFIX + "audience");

        if (key.isEmpty() && kid.isEmpty() && issuer.isEmpty() && audience.isEmpty()) {
            // No default signing material or identity trusted by accident.
            verifier = null;
            return;
        }
        if (key.isEmpty() || kid.isEmpty() || issuer.isEmpty() || audience.isEmpty()) {
            throw new IllegalStateException("Incomplete pinned Runtime Identity trust configuration");
        }
        if (key.length() > MAX_PEM_B64_LENGTH) {
            throw new IllegalStateException("Invalid pinned Runtime Identity trust configuration");
        }

        try {
            // Exactly one operator-owned PEM, base64-encoded only to safely
            // represent multiline public key material in an environment value.
            // This is not a secret and MUST NOT be fetched from incoming JWT.
            String pem = new String(Base64.getDecoder().decode(key),
                StandardCharsets.US_ASCII);
            verifier = new RuntimePinnedPrincipalVerifier(
                pem, kid, issuer, audience, Clock.systemUTC());
        } catch (RuntimeException invalid) {
            // No key bytes, claims, system properties or exception causes in
            // error: avoid leaking configuration through startup diagnostics.
            throw new IllegalStateException("Invalid pinned Runtime Identity trust configuration");
        }
    }

    private static String configured(Environment environment, String property) {
        String value = environment.getProperty(property);
        return value == null ? "" : value.strip();
    }

    public boolean isConfigured() {
        return verifier != null;
    }

    /**
     * Validates only cryptographic identity. This does not check session
     * revocation, resource ownership, membership, workload mTLS or permissions
     * to access any HTTP endpoint. No request filter consumes it in S0-E6A.
     */
    public RuntimePinnedPrincipalVerifier.VerifiedPrincipal requireVerifiedPrincipal(
            String authorizationHeader) {
        if (verifier == null) {
            throw new IllegalStateException("Runtime Identity trust is not configured");
        }
        return verifier.verifyBearer(authorizationHeader);
    }
}
