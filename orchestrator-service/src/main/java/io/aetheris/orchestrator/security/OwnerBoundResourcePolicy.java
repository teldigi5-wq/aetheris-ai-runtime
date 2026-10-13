package io.aetheris.orchestrator.security;

import java.util.UUID;

/**
 * Pure resource-ownership comparison, not an authentication implementation.
 *
 * Verified caller identity MUST come from a future cryptographic runtime
 * principal resolver. Do not derive it from X-Aetheris-* headers, request JSON,
 * cookies, arbitrary JWT payload strings or user-controlled local settings.
 * Unowned legacy rows always fail closed.
 */
public final class OwnerBoundResourcePolicy {
    private OwnerBoundResourcePolicy() {}

    public enum Result {
        UNVERIFIED_CALLER,
        OWNERLESS_LEGACY_ROW,
        OWNER_MISMATCH,
        PROJECT_MISMATCH,
        MATCH
    }

    public static Result evaluate(UUID storedOwnerUserId, UUID storedProjectId,
                                  UUID verifiedCallerUserId, UUID verifiedProjectId) {
        if (verifiedCallerUserId == null) return Result.UNVERIFIED_CALLER;
        if (storedOwnerUserId == null) return Result.OWNERLESS_LEGACY_ROW;
        if (!storedOwnerUserId.equals(verifiedCallerUserId)) return Result.OWNER_MISMATCH;
        // A project-bound resource cannot be read via a missing or foreign
        // project context. Owner-global resources legitimately have no project.
        if (storedProjectId != null && !storedProjectId.equals(verifiedProjectId)) {
            return Result.PROJECT_MISMATCH;
        }
        return Result.MATCH;
    }

    public static boolean permitted(UUID storedOwnerUserId, UUID storedProjectId,
                                    UUID verifiedCallerUserId, UUID verifiedProjectId) {
        return evaluate(storedOwnerUserId, storedProjectId,
                verifiedCallerUserId, verifiedProjectId) == Result.MATCH;
    }
}
