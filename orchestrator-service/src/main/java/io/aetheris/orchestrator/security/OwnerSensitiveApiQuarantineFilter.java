package io.aetheris.orchestrator.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Temporary server-side quarantine for owner-sensitive mission/task APIs.
 *
 * The runtime currently does not validate a cryptographic principal from its
 * direct HTTP boundary, and existing TaskEntity/MissionSessionEntity records
 * are not authoritatively bound to an owner. A gateway role/scope or an
 * X-Aetheris-User-Id header is NOT task ownership proof.
 *
 * Keep all external task/mission/history/live event routes inaccessible until
 * that missing identity/ownership design is implemented and security-tested.
 * Internal Spring service-to-service method calls are unaffected.
 *
 * SECURITY: This class intentionally exposes NO configuration override,
 * trusted-header shortcut, test-only activation toggle or external allowlist.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class OwnerSensitiveApiQuarantineFilter extends OncePerRequestFilter {
    public static final String BLOCK_STATUS = "BLOCKED_OWNER_AUTH_NOT_VERIFIED";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!isOwnerSensitivePath(request.getRequestURI(), request.getServletPath())) {
            chain.doFilter(request, response);
            return;
        }
        // Independent of method, JWT-looking strings, forwarded identity headers,
        // Origin, Referer, cookies, loopback source address or proxy topology.
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write("{\"status\":\"" + BLOCK_STATUS + "\"}");
    }

    public static boolean isOwnerSensitivePath(String rawUri, String servletPath) {
        return matchesCandidate(servletPath) || matchesCandidate(rawUri) ||
               matchesCandidate(decodeSafely(rawUri)) ||
               matchesCandidate(decodeSafely(decodeSafely(rawUri)));
    }

    private static String decodeSafely(String value) {
        if (value == null) return "";
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            // Never use malformed percent-encoding as an authorization bypass.
            return value;
        }
    }

    private static boolean matchesCandidate(String path) {
        if (path == null) return false;
        // Conservative prefix matching also covers matrix params/alternate
        // route suffixes without relying on framework route matching order.
        return path.startsWith("/api/orchestrator/tasks")
                || path.startsWith("/api/orchestrator/missions")
                || path.startsWith("/api/orchestrator/live")
                || path.contains("/api/orchestrator/tasks/")
                || path.contains("/api/orchestrator/missions/")
                || path.contains("/api/orchestrator/live/");
    }
}
