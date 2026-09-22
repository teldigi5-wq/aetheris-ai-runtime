package io.aetheris.orchestrator.executive;

import java.util.UUID;

public record ExecutiveAction(
        String source,
        ExecutiveSignalType type,
        ExecutiveDisposition disposition,
        String summary,
        UUID taskId,
        UUID approvalId
) {}
