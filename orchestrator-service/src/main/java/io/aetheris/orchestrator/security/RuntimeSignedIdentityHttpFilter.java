package io.aetheris.orchestrator.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Objects;

/**
 * Independently checks a Runtime HTTP bearer request, even when the caller
 * bypasses Platform Gateway. REVIEW ONLY: intentionally NOT annotated as a
 * Spring component, NOT registered against production URLs, and NOT sufficient
 * to authorize task/mission objects. Existing unconditional HTTP quarantine
 * must stay enabled until approved registry and workload transport exist.
 *
 * A signed access token is necessary but not sufficient: an independently
 * supplied authoritative session check MUST succeed on EVERY request.
 * Missing authorities and errors fail closed. Never authorize based on
 * X-Aetheris-* or forwarded user headers.
 */
public final class RuntimeSignedIdentityHttpFilter implements Filter {
    public static final String VERIFIED_PRINCIPAL_ATTRIBUTE =
        RuntimeSignedIdentityHttpFilter.class.getName() + ".signedPrincipal";

    @FunctionalInterface
    public interface SessionAuthority {
        /**
         * True ONLY after checking an authoritative, currently active
         * session/JTI + signer + principal epoch. No caching and no caller
         * supplied session claims. Exceptions cause a fail-closed 503.
         *
         * This preflight is NOT object ownership or a long-lived grant;
         * resource SQL must independently recheck authorizations atomically.
         */
        boolean isActive(RuntimePinnedPrincipalVerifier.VerifiedRevocationFacts signed)
            throws Exception;
    }

    private final RuntimePinnedPrincipalVerifier verifier;
    private final SessionAuthority authority;
    private final String requiredScope;

    /** Safe default: no authority connected means no possible admission. */
    public RuntimeSignedIdentityHttpFilter(
            RuntimePinnedPrincipalVerifier verifier, String requiredScope) {
        this(verifier, signed -> false, requiredScope);
    }

    public RuntimeSignedIdentityHttpFilter(
            RuntimePinnedPrincipalVerifier verifier,
            SessionAuthority authority,
            String requiredScope) {
        this.verifier = Objects.requireNonNull(verifier, "pinned verifier");
        this.authority = Objects.requireNonNull(authority, "authoritative session checker");
        if (!"orchestrator:read".equals(requiredScope)
                && !"orchestrator:write".equals(requiredScope)) {
            throw new IllegalArgumentException("Unapproved runtime HTTP scope");
        }
        this.requiredScope = requiredScope;
    }

    @Override
    public void doFilter(ServletRequest input, ServletResponse output,
                         FilterChain chain) throws IOException, ServletException {
        if (!(input instanceof HttpServletRequest request)
                || !(output instanceof HttpServletResponse response)) {
            throw new ServletException("Runtime HTTP authentication requires HTTP");
        }
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        // Reject an attribute left by an upstream integration or dispatch.
        request.removeAttribute(VERIFIED_PRINCIPAL_ATTRIBUTE);

        if (hasUntrustedIdentityHeaders(request)) {
            deny(response, HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String authorization = singleBearerHeader(request);
        if (authorization == null) {
            deny(response, HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        final RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal;
        try {
            principal = verifier.verifyBearerForRevocation(authorization);
        } catch (RuntimeException rejected) {
            deny(response, HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        final boolean sessionActive;
        try {
            sessionActive = authority.isActive(principal.requireRevocationFacts());
        } catch (Exception unavailable) {
            // No exception messages, session IDs, tokens, or database details
            // are returned, logged or made visible to the HTTP client.
            deny(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        if (!sessionActive || !principal.isFresh()) {
            deny(response, HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        if (!principal.hasScope(requiredScope)) {
            deny(response, HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // This typed attribute is only valid for the synchronous chain
        // execution. It MUST NOT be treated as membership or object ownership.
        request.setAttribute(VERIFIED_PRINCIPAL_ATTRIBUTE, principal);
        try {
            chain.doFilter(request, response);
        } finally {
            request.removeAttribute(VERIFIED_PRINCIPAL_ATTRIBUTE);
        }
    }

    private static String singleBearerHeader(HttpServletRequest request) {
        Enumeration<String> values = request.getHeaders("Authorization");
        if (values == null || !values.hasMoreElements()) return null;
        String first = values.nextElement();
        if (values.hasMoreElements()) return null;
        return first;
    }

    private static boolean hasUntrustedIdentityHeaders(HttpServletRequest request) {
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) return false;
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (name == null) continue;
            String normalized = name.toLowerCase(Locale.ROOT);
            if (normalized.startsWith("x-aetheris-")
                    || normalized.equals("x-forwarded-user")
                    || normalized.equals("x-forwarded-email")
                    || normalized.equals("x-forwarded-roles")
                    || normalized.equals("x-remote-user")
                    || normalized.equals("x-authenticated-user")
                    || normalized.equals("x-original-user")) {
                return true;
            }
        }
        return false;
    }

    private static void deny(HttpServletResponse response, int status) {
        response.setStatus(status);
        response.setContentLength(0);
        if (status == HttpServletResponse.SC_UNAUTHORIZED) {
            response.setHeader("WWW-Authenticate",
                "Bearer realm=\"aetheris-runtime\"");
        }
    }
}
