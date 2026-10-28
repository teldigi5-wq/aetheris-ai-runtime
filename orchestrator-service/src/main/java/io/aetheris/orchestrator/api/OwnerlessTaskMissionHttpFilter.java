package io.aetheris.orchestrator.api;

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
 * Temporary SaaS safety containment: task and mission API requests cannot
 * be authorized without verified principal/project binding. In the current
 * runtime both the principal and durable task ownership are absent.
 *
 * This is an unconditional HTTP boundary: no JWT-shaped string, supplied
 * owner header, API key, tenant query parameter or environment toggle can
 * bypass it. Internal service operations are NOT changed by this filter.
 *
 * Remove only in the same reviewed change that demonstrates genuine
 * independent runtime authentication, persistent ownership, historic
 * ownerless record policy and two-tenant regression tests.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class OwnerlessTaskMissionHttpFilter extends OncePerRequestFilter {
    private static final String TASK_ROOT = "/api/orchestrator/tasks";
    private static final String MISSION_ROOT = "/api/orchestrator/missions";

    static boolean isProtected(String path) {
        return boundary(path, TASK_ROOT) || boundary(path, MISSION_ROOT);
    }

    private static boolean boundary(String path, String prefix) {
        if (path == null || !path.startsWith(prefix)) return false;
        if (path.length() == prefix.length()) return true;
        char delimiter = path.charAt(prefix.length());
        return delimiter == '/' || delimiter == ';';
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        // ServletPath is the mapped, decoded path; the raw request URI is
        // checked as well to prevent proxy/context-path disagreement.
        String context = request.getContextPath();
        String raw = request.getRequestURI();
        String contextRelative = raw != null && context != null
            && raw.startsWith(context) ? raw.substring(context.length()) : raw;
        if (isProtected(request.getServletPath()) || isProtected(contextRelative)) {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }
}
