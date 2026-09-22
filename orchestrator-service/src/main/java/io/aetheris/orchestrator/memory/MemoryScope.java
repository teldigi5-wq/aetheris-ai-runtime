package io.aetheris.orchestrator.memory;

/**
 * Memory boundaries used by both the legacy knowledge layer and the Phase 9
 * durable memory service. Existing values remain for backwards compatibility;
 * the new values model the explicit session/owner/workspace boundaries from
 * the product blueprint.
 */
public enum MemoryScope {
    PERSONAL,
    PROJECT,
    CAREER,
    SYSTEM,
    SESSION,
    OWNER,
    WORKSPACE
}
