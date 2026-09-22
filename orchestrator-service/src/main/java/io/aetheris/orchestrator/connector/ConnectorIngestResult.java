package io.aetheris.orchestrator.connector;

import io.aetheris.orchestrator.executive.ExecutiveDisposition;

import java.util.UUID;

public record ConnectorIngestResult(
        UUID receiptId,
        boolean duplicate,
        ConnectorProvider provider,
        String externalEventId,
        String source,
        UUID executiveRunId,
        ExecutiveDisposition disposition,
        UUID taskId,
        UUID approvalId,
        boolean effectiveTrustedLowRisk,
        String summary
) {}
