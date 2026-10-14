package io.aetheris.orchestrator.memory;

import java.util.UUID;

/**
 * Bounded DB projection for an offline migration-readiness inventory.
 * Only the encrypted payload's first 96 characters are selected. No owner ID,
 * project, tags, non-encrypted payload, content or token material is projected.
 */
public record MemoryEnvelopeInventoryRow(
        UUID id,
        boolean encrypted,
        boolean sensitive,
        String encryptedHeader,
        Integer encryptedCharacters) {
}
