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

/**
 * S0-E4 release quarantine for ALL uncertified application API and actuator
 * HTTP routes. This is a REAL unconditional production-registered filter.
 *
 * The original OwnerlessTaskMissionHttpFilter runs at HIGHEST_PRECEDENCE,
 * before this filter. Every currently unreviewed /api endpoint (including
 * execution, connectors and streams) is blocked with 404 regardless of JWT,
 * bearer scope, forwarded identity or environment configuration.
 *
 * This deliberately makes application APIs unavailable until independent
 * production authentication/authorization and release certification exist.
 * It is NOT a resource-server and MUST NOT be merged/deployed for customer
 * availability without an explicit, separately reviewed operational decision.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class RuntimeUncertifiedApiHttpFilter extends OncePerRequestFilter {
    static boolean quarantinedPath(String path) {
        return segment(path, "/api") || segment(path, "/actuator");
    }

    private static boolean segment(String path, String base) {
        if (path == null || !path.startsWith(base)) return false;
        if (path.length() == base.length()) return true;
        char next = path.charAt(base.length());
        return next == '/' || next == ';';
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        String raw = request.getRequestURI();
        String context = request.getContextPath();
        String relative = raw != null && context != null
            && raw.startsWith(context) ? raw.substring(context.length()) : raw;
        if (quarantinedPath(request.getServletPath())
                || quarantinedPath(relative)) {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }
}
