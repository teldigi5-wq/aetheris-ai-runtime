package io.aetheris.orchestrator.executive;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExecutiveOvernightBriefing(
        UUID runId,
        String ownerId,
        Instant generatedAt,
        Instant nextBriefingAt,
        UUID voiceSessionId,
        int totalSignals,
        int autoHandled,
        int drafts,
        int approvalsRequired,
        int signups,
        int verifiedCodeUpdates,
        String briefing,
        List<ExecutiveAction> actions
) {}
