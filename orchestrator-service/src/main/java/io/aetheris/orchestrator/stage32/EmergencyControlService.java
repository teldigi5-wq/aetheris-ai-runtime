package io.aetheris.orchestrator.stage32;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class EmergencyControlService {
    private final AtomicReference<EmergencyMode> mode = new AtomicReference<>(EmergencyMode.NORMAL);

    public EmergencyMode current() { return mode.get(); }

    public EmergencyMode request(EmergencyMode requested) {
        Objects.requireNonNull(requested, "requested");
        if (requested == EmergencyMode.NORMAL) return mode.get();
        return mode.updateAndGet(existing -> requested.priority() > existing.priority() ? requested : existing);
    }

    public EmergencyMode release(boolean ownerConfirmed) {
        if (!ownerConfirmed) throw new SecurityException("Owner confirmation is required to release emergency control");
        mode.set(EmergencyMode.NORMAL);
        return EmergencyMode.NORMAL;
    }
}
