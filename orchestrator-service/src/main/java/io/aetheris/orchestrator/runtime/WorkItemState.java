package io.aetheris.orchestrator.runtime;

public enum WorkItemState {
    QUEUED,
    RUNNING,
    RETRY_WAIT,
    PAUSED,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
