package io.aetheris.orchestrator.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Objects;

/**
 * S0-E1 stand-alone servlet authentication candidate; deliberately NOT a
 * Spring-managed component, controller or filter registration. Only the fixed
 * synthetic /__s0/auth-probe route is permitted in a test-only Tomcat server.
 *
 * It NEVER authenticates/unlocks customer Task or Mission HTTP routes.
 * This checks a signed identity/scopes, not object ownership, membership,
 * session revocation, transport mTLS or production workload identity.
 */
public final class RuntimePinnedHttpBearerGate extends OncePerRequestFilter {
    private static final String PROBE_PATH = "/__s0/auth-probe";
    private final RuntimePinnedPrincipalVerifier verifier;

    public RuntimePinnedHttpBearerGate(RuntimePinnedPrincipalVerifier verifier) {
        this.verifier = Objects.requireNonNull(verifier, "pinned verifier");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Require exact agreement between raw URI and mapped servlet path.
        // Ambiguous proxy encodings, path parameters and unsupported verbs
        // fail closed even if a caller presents a valid signed bearer.
        String raw = request.getRequestURI();
        String context = request.getContextPath();
        String path = context == null || context.isEmpty() ? raw
            : raw != null && raw.startsWith(context)
                ? raw.substring(context.length()) : null;
        if (!"GET".equals(request.getMethod())
                || !PROBE_PATH.equals(path)
                || !PROBE_PATH.equals(request.getServletPath())) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Enumeration<String> headers = request.getHeaders("Authorization");
        if (headers == null || !headers.hasMoreElements()) {
            rejectUnauthorized(response);
            return;
        }
        String bearer = headers.nextElement();
        // Multiple Authorization fields are invalid even if first one is good.
        if (headers.hasMoreElements()) {
            rejectUnauthorized(response);
            return;
        }

        final RuntimePinnedPrincipalVerifier.VerifiedPrincipal principal;
        try {
            principal = verifier.verifyBearer(bearer);
        } catch (IllegalArgumentException rejected) {
            rejectUnauthorized(response);
            return;
        }
        if (!principal.hasScope("orchestrator:read")) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // No subject, token, UID or role is copied to a client-visible header
        // or an untrusted caller header. Downstream test servlet returns 204.
        chain.doFilter(request, response);
    }

    private static void rejectUnauthorized(HttpServletResponse response)
            throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer realm=\"aetheris-s0-probe\"");
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
