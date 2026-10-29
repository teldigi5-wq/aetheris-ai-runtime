package io.aetheris.orchestrator.security;

import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Conservative S0-E6B policy evaluation scaffold.
 *
 * The real Runtime API quarantine stays unconditional. This class does not
 * register an HTTP endpoint or request filter. JWT validation is identity
 * evidence only; no HTTP access is granted from a scope alone.
 */
@Component
public final class RuntimeRouteAdmissionPolicy {
    public enum RouteGroup {
        TASK_MISSION, EVENT_REPLAY, CONNECTOR_SECRET,
        EXECUTION_CONTROL, OPERATOR_MONITORING, OTHER_API, UNREVIEWED
    }

    public enum MissingEvidence {
        VERIFIED_USER, LIVE_SESSION, TRUSTED_WORKLOAD,
        TENANT_MEMBERSHIP, DURABLE_OBJECT_OWNER,
        ACTION_APPROVAL, PRIVATE_OPERATIONS
    }

    public enum Verdict { QUARANTINED }

    public record ReviewDecision(
        Verdict verdict, RouteGroup routeGroup, Set<MissingEvidence> prerequisites
    ) {
        public ReviewDecision {
            prerequisites = Set.copyOf(prerequisites);
        }
    }

    private static boolean prefix(String path, String root) {
        return path != null && path.startsWith(root)
            && (path.length() == root.length()
                || path.charAt(root.length()) == '/'
                || path.charAt(root.length()) == ';');
    }

    public static RouteGroup groupFor(String path) {
        if (prefix(path, "/api/orchestrator/tasks")
                || prefix(path, "/api/orchestrator/missions")) {
            return RouteGroup.TASK_MISSION;
        }
        if (prefix(path, "/api/orchestrator/live")
                || (prefix(path, "/api/orchestrator") && path.contains("/events"))) {
            return RouteGroup.EVENT_REPLAY;
        }
        if (prefix(path, "/api/orchestrator/connectors")
                || prefix(path, "/api/orchestrator/mcp")) {
            return RouteGroup.CONNECTOR_SECRET;
        }
        if (prefix(path, "/api/orchestrator/models")
                || prefix(path, "/api/orchestrator/hosts")
                || prefix(path, "/api/orchestrator/tools")
                || prefix(path, "/api/orchestrator/stage8")
                || prefix(path, "/api/orchestrator/stage9")
                || prefix(path, "/api/orchestrator/stage10")
                || prefix(path, "/api/orchestrator/stage11")
                || prefix(path, "/api/orchestrator/stage12")
                || prefix(path, "/api/orchestrator/stage13")
                || prefix(path, "/api/orchestrator/stage14")
                || prefix(path, "/api/orchestrator/stage15")
                || prefix(path, "/api/orchestrator/stage16")
                || prefix(path, "/api/orchestrator/stage17")
                || prefix(path, "/api/orchestrator/stage18")
                || prefix(path, "/api/orchestrator/stage19")
                || prefix(path, "/api/orchestrator/stage20")
                || prefix(path, "/api/orchestrator/stage21")) {
            return RouteGroup.EXECUTION_CONTROL;
        }
        if (prefix(path, "/actuator")) {
            return RouteGroup.OPERATOR_MONITORING;
        }
        if (prefix(path, "/api")) {
            return RouteGroup.OTHER_API;
        }
        return RouteGroup.UNREVIEWED;
    }

    /**
     * Deliberately no ALLOW verdict. Caller, method and signed claims cannot
     * reduce the release requirements before independent source-level tests.
     */
    public ReviewDecision assess(
            RuntimePinnedPrincipalVerifier.VerifiedPrincipal verifiedUser,
            String requestMethod,
            String requestPath) {
        RouteGroup group = groupFor(requestPath);
        EnumSet<MissingEvidence> needs = EnumSet.of(
            MissingEvidence.LIVE_SESSION,
            MissingEvidence.TRUSTED_WORKLOAD
        );
        if (verifiedUser == null) {
            needs.add(MissingEvidence.VERIFIED_USER);
        }
        switch (group) {
            case TASK_MISSION, EVENT_REPLAY -> {
                needs.add(MissingEvidence.TENANT_MEMBERSHIP);
                needs.add(MissingEvidence.DURABLE_OBJECT_OWNER);
            }
            case CONNECTOR_SECRET, EXECUTION_CONTROL -> {
                needs.add(MissingEvidence.TENANT_MEMBERSHIP);
                needs.add(MissingEvidence.ACTION_APPROVAL);
            }
            case OPERATOR_MONITORING -> needs.add(MissingEvidence.PRIVATE_OPERATIONS);
            case OTHER_API -> needs.add(MissingEvidence.TENANT_MEMBERSHIP);
            case UNREVIEWED -> {
                needs.add(MissingEvidence.DURABLE_OBJECT_OWNER);
                needs.add(MissingEvidence.ACTION_APPROVAL);
                needs.add(MissingEvidence.PRIVATE_OPERATIONS);
            }
        }
        return new ReviewDecision(Verdict.QUARANTINED, group, needs);
    }
}
