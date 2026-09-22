package io.aetheris.orchestrator.connector.sync;

import io.aetheris.orchestrator.connector.ConnectorIngestResult;
import io.aetheris.orchestrator.connector.ConnectorProvider;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProviderSyncResult(
        UUID connectionId,
        ConnectorProvider provider,
        int fetched,
        int ingested,
        int duplicates,
        int failed,
        Instant syncedAt,
        List<ConnectorIngestResult> results,
        List<String> errors
) {}
