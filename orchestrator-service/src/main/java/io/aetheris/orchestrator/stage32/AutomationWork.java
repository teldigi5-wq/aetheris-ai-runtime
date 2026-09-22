package io.aetheris.orchestrator.stage32;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record AutomationWork(
        String id,
        boolean emergencyWork,
        int urgency,
        double estimatedCpuPercent,
        double estimatedMemoryPercent,
        Instant hardDeadline,
        Duration maxRuntime) {
    public AutomationWork {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Work id is required");
        if (urgency < 0 || urgency > 10) throw new IllegalArgumentException("Urgency must be 0..10");
        if (estimatedCpuPercent < 0 || estimatedMemoryPercent < 0) throw new IllegalArgumentException("Estimated resources cannot be negative");
        Objects.requireNonNull(maxRuntime, "maxRuntime");
        if (maxRuntime.isZero() || maxRuntime.isNegative()) throw new IllegalArgumentException("maxRuntime must be positive");
    }
}
