package io.aetheris.orchestrator.task;

public enum TaskState {
    QUEUED,
    PLANNING,
    AWAITING_APPROVAL,
    RUNNING,
    VERIFYING,
    PAUSED,
    ROLLING_BACK,
    COMPLETED,
    FAILED,
    CANCELLED
}
