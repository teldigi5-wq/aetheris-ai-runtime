package io.aetheris.orchestrator.stage14;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class Stage14PostIncidentReportService {
    private final Stage14OperationalIntelligenceService operations;
    private final Stage14SelfHealingProposalService proposals;

    public Stage14PostIncidentReportService(Stage14OperationalIntelligenceService operations,
                                            Stage14SelfHealingProposalService proposals) {
        this.operations = operations;
        this.proposals = proposals;
    }

    public PostIncidentReport build(UUID incidentId) {
        var incident = operations.incident(incidentId);
        var signals = operations.signalsForIncident(incidentId);
        var healing = proposals.forIncident(incidentId);
        List<TimelineItem> timeline = new ArrayList<>();
        for (var signal : signals) timeline.add(new TimelineItem(signal.getObservedAt(), "SIGNAL",
                signal.getSeverity() + " " + signal.getSignalType() + ": " + signal.getSummary(), signal.getAttestationSha256()));
        for (var p : healing) timeline.add(new TimelineItem(p.getCreatedAt(), "REMEDIATION_PROPOSAL",
                p.getAction() + " -> " + p.getTarget() + " (" + p.getStatus() + ")", null));
        timeline.sort(Comparator.comparing(TimelineItem::at));
        String rootCause = "UNDETERMINED_FROM_AVAILABLE_EVIDENCE";
        String status = "RESOLVED".equals(incident.getStatus()) ? "FINAL_EVIDENCE_REPORT" : "DRAFT_INCIDENT_REPORT";
        return new PostIncidentReport(status, incident.getId(), incident.getTitle(), incident.getSeverity(),
                incident.getStatus(), incident.getSignalCount(), incident.getFirstSeenAt(), incident.getLastSeenAt(),
                rootCause, incident.getResolutionNote(), List.copyOf(timeline), healing.stream().map(Stage14SelfHealingProposalEntity::getStatus).toList(),
                false, "Root cause is not inferred without explicit evidence; unresolved incidents remain draft reports");
    }

    public record TimelineItem(Instant at, String type, String detail, String attestationSha256) {}
    public record PostIncidentReport(String reportStatus, UUID incidentId, String title, String severity,
                                     String incidentStatus, int signalCount, Instant firstSeenAt, Instant lastSeenAt,
                                     String rootCause, String resolutionNote, List<TimelineItem> timeline,
                                     List<String> remediationStates, boolean externalActionAttempted, String evidenceNote) {}
}
