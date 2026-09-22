package io.aetheris.orchestrator.task;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TaskEvent(
        UUID taskId,
        Instant timestamp,
        TaskState state,
        String agentId,
        String message,
        Map<String, Object> metadata
) {
    public TaskEvent {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
