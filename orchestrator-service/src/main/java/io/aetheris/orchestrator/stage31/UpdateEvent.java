package io.aetheris.orchestrator.stage31;

public enum UpdateEvent {
    STAGE,
    START_CANARY,
    HEALTH_PASS,
    HEALTH_FAIL,
    PROMOTE,
    ROLLBACK
}
