package io.aetheris.orchestrator.stage32;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class EventTriggerEngine {
    private final Map<String, Instant> lastAccepted = new HashMap<>();

    public synchronized boolean accept(AutomationEvent event, Duration debounce) {
        if (debounce == null || debounce.isNegative()) throw new IllegalArgumentException("debounce must be non-negative");
        Instant previous = lastAccepted.get(event.fingerprint());
        if (previous != null && event.observedAt().isBefore(previous.plus(debounce))) return false;
        lastAccepted.put(event.fingerprint(), event.observedAt());
        return true;
    }
}
