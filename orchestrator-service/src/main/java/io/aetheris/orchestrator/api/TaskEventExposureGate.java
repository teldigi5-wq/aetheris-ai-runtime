package io.aetheris.orchestrator.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Intentionally deny all ownerless task event history/replay and global SSE.
 *
 * Presence of an arbitrary UUID, route scope or X-Aetheris-* HTTP header is
 * not proof of object ownership. This cannot be enabled with an environment
 * variable or a UI control. Replace only after authoritative persisted
 * owner/project binding and independent caller authentication are deployed
 * and verified across Gateway AND direct-runtime-port access.
 */
public final class TaskEventExposureGate {
    private TaskEventExposureGate() {}

    public static void requireVerifiedOwnerBinding() {
        // No project/owner identity exists on TaskEntity today. A 404
        // avoids disclosing whether a given private task UUID exists.
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
