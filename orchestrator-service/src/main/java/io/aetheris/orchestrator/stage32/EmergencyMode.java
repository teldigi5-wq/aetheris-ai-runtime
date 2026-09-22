package io.aetheris.orchestrator.stage32;

public enum EmergencyMode {
    NORMAL(0), PAUSE(1), TAKE_CONTROL(2), STOP(3);

    private final int priority;
    EmergencyMode(int priority) { this.priority = priority; }
    public int priority() { return priority; }
}
