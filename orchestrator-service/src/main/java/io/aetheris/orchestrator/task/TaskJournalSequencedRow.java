package io.aetheris.orchestrator.task;

import java.time.Instant;
import java.util.UUID;

/** Read-only projection; excludes message, command and metadata. */
public record TaskJournalSequencedRow(UUID eventId, UUID taskId,
                                      Long sequence, Instant eventAt,
                                      TaskState state, String agentId) {}
