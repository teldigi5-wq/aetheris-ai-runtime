package io.aetheris.orchestrator.stage32;

import java.time.Instant;

public record AutomationEvent(String eventId, EventSource source, String sourceKey, String fingerprint,
                              Instant observedAt, String payloadReference) {
    public AutomationEvent {
        if (eventId == null || eventId.isBlank() || source == null || sourceKey == null || sourceKey.isBlank()
                || fingerprint == null || fingerprint.isBlank() || observedAt == null) {
            throw new IllegalArgumentException("Complete normalized event identity is required");
        }
    }
}
