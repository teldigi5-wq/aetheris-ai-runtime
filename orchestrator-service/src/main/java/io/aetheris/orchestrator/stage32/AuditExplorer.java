package io.aetheris.orchestrator.stage32;

import java.util.List;

public final class AuditExplorer {
    public List<TimelineEntry> search(List<TimelineEntry> entries, AuditQuery q) {
        return entries.stream().filter(e -> q.from() == null || !e.at().isBefore(q.from()))
                .filter(e -> q.to() == null || !e.at().isAfter(q.to()))
                .filter(e -> blank(q.subsystem()) || e.subsystem().equals(q.subsystem()))
                .filter(e -> blank(q.actionClass()) || e.actionClass().equals(q.actionClass()))
                .filter(e -> blank(q.actor()) || e.actor().equals(q.actor()))
                .filter(e -> blank(q.outcome()) || e.outcome().equals(q.outcome()))
                .filter(e -> q.minimumRisk() == null || e.risk() >= q.minimumRisk())
                .filter(e -> blank(q.evidenceContains()) || (e.evidenceReference() != null && e.evidenceReference().contains(q.evidenceContains())))
                .toList();
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
