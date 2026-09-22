package io.aetheris.orchestrator.scheduler;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record WorkerHeartbeatRequest(
        @NotBlank String workerId,
        String agentId,
        @Min(1) @Max(64) int maxConcurrency,
        @Min(0) int activeLeases) {

    public WorkerHeartbeatRequest(String workerId, int maxConcurrency, int activeLeases) {
        this(workerId, null, maxConcurrency, activeLeases);
    }
}
