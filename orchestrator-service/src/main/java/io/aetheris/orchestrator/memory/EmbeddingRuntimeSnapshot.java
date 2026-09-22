package io.aetheris.orchestrator.memory;

import java.time.Instant;

public record EmbeddingRuntimeSnapshot(
        String adapterId,
        String backend,
        String model,
        boolean neuralAttempted,
        boolean neuralUsed,
        boolean fallbackUsed,
        int dimensions,
        long latencyMs,
        long heapDeltaBytes,
        String detail,
        Instant measuredAt
) {}
