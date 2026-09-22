package io.aetheris.orchestrator.stage31;

public enum UpdateState {
    PROPOSED,
    STAGED,
    CANARY_RUNNING,
    HEALTHY,
    PROMOTED,
    ROLLBACK_REQUIRED,
    ROLLED_BACK
}
