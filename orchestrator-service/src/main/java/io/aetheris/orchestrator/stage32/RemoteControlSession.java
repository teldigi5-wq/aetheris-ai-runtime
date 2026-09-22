package io.aetheris.orchestrator.stage32;

import java.time.Instant;
import java.util.Set;

public record RemoteControlSession(String sessionId, boolean ownerAuthenticated, Instant issuedAt, Instant expiresAt,
                                   Set<String> capabilities) {
    public RemoteControlSession {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        if (sessionId == null || sessionId.isBlank() || issuedAt == null || expiresAt == null || !expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("Invalid remote session");
        }
    }
}
