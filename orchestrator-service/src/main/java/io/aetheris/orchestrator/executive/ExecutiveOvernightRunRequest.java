package io.aetheris.orchestrator.executive;

import java.time.Instant;
import java.util.List;

public record ExecutiveOvernightRunRequest(
        String ownerId,
        Instant nextBriefingAt,
        List<OvernightSignal> signals
) {}
