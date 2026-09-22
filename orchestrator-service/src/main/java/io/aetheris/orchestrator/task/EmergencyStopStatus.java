package io.aetheris.orchestrator.task;

import java.time.Instant;

public record EmergencyStopStatus(
        boolean active,
        Instant changedAt,
        String reason,
        int cancelledTasks
) {
}
