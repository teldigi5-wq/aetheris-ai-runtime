package io.aetheris.orchestrator.stage32;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

public final class WatcherAbstraction {
    public AutomationEvent normalize(EventSource source, String sourceKey, String eventKey, Instant observedAt, String payloadReference) {
        if (source == null || sourceKey == null || sourceKey.isBlank() || eventKey == null || eventKey.isBlank() || observedAt == null) {
            throw new IllegalArgumentException("Watcher event fields are required");
        }
        String material = source.name() + "|" + sourceKey + "|" + eventKey;
        return new AutomationEvent(eventKey, source, sourceKey, sha256(material), observedAt, payloadReference);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
