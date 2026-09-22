package io.aetheris.orchestrator.stage32;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

public final class RemoteControlGuard {
    private final Set<String> consumedNonces = new HashSet<>();
    private static final Set<String> EMERGENCY_CAPABILITIES = Set.of("STOP", "PAUSE", "TAKE_CONTROL", "READ_STATUS");

    public synchronized RemoteAuthorization authorize(RemoteControlSession session, String nonce, String capability,
                                                      Instant now, EmergencyMode emergencyMode) {
        if (!session.ownerAuthenticated()) return new RemoteAuthorization(false, "owner-auth-required");
        if (now.isBefore(session.issuedAt()) || !now.isBefore(session.expiresAt())) return new RemoteAuthorization(false, "session-expired");
        if (nonce == null || nonce.isBlank()) return new RemoteAuthorization(false, "nonce-required");
        String scopedNonce = session.sessionId() + ":" + nonce;
        if (consumedNonces.contains(scopedNonce)) return new RemoteAuthorization(false, "replay-detected");
        if (!session.capabilities().contains(capability)) return new RemoteAuthorization(false, "capability-not-granted");
        if (emergencyMode != EmergencyMode.NORMAL && !EMERGENCY_CAPABILITIES.contains(capability)) {
            return new RemoteAuthorization(false, "emergency-control-preempts-remote-action");
        }
        consumedNonces.add(scopedNonce);
        return new RemoteAuthorization(true, "owner-session-authorized");
    }
}
