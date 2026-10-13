package io.aetheris.orchestrator.task;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only database projection: SELECT deliberately excludes original
 * journal message, serialized metadata, commands and credentials.
 */
public record TaskJournalCursorRow(UUID eventId, UUID taskId,
                                   Instant eventAt, TaskState state, String agentId) {}
