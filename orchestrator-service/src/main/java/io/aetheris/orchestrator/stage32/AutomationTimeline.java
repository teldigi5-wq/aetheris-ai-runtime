package io.aetheris.orchestrator.stage32;

import java.util.ArrayList;
import java.util.List;

public final class AutomationTimeline {
    private final List<TimelineEntry> entries = new ArrayList<>();
    public synchronized void append(TimelineEntry entry) { entries.add(entry); }
    public synchronized List<TimelineEntry> snapshot() { return List.copyOf(entries); }
}
