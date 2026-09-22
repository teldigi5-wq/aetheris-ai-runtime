package io.aetheris.orchestrator.stage32;

import java.time.Instant;

public record TimelineEntry(String id, Instant at, String subsystem, String actionClass, String actor,
                            String outcome, int risk, String evidenceReference, double costUsd, String reason) {
    public TimelineEntry {
        if (id == null || id.isBlank() || at == null || subsystem == null || actionClass == null || actor == null || outcome == null) {
            throw new IllegalArgumentException("Timeline identity fields are required");
        }
        if (risk < 0 || risk > 10 || !Double.isFinite(costUsd) || costUsd < 0) throw new IllegalArgumentException("Invalid risk or cost");
    }
}
