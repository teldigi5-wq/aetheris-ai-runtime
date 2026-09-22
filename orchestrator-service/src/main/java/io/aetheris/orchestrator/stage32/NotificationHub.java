package io.aetheris.orchestrator.stage32;

import java.time.LocalTime;

public final class NotificationHub {
    public NotificationRoute route(NotificationUrgency urgency, LocalTime now, LocalTime quietStart, LocalTime quietEnd,
                                   boolean externalConfigured, boolean focusMode) {
        boolean critical = urgency == NotificationUrgency.CRITICAL;
        boolean quiet = inWindow(now, quietStart, quietEnd);
        if (!critical && (quiet || focusMode)) {
            return new NotificationRoute(false, false, false, focusMode ? "focus-mode" : "quiet-hours");
        }
        boolean external = externalConfigured && (critical || urgency == NotificationUrgency.HIGH);
        return new NotificationRoute(true, external, critical, critical ? "critical-bypass" : "normal-routing");
    }

    private boolean inWindow(LocalTime now, LocalTime start, LocalTime end) {
        if (now == null || start == null || end == null || start.equals(end)) return false;
        if (start.isBefore(end)) return !now.isBefore(start) && now.isBefore(end);
        return !now.isBefore(start) || now.isBefore(end);
    }
}
