package io.aetheris.orchestrator.stage32;

import java.util.concurrent.atomic.AtomicBoolean;

public final class FocusModeService {
    private final AtomicBoolean active = new AtomicBoolean(false);
    public boolean enter() { active.set(true); return true; }
    public boolean exit() { active.set(false); return false; }
    public boolean active() { return active.get(); }
    public boolean automationAllowed(boolean urgentOrEmergency) { return !active.get() || urgentOrEmergency; }
}
