package io.aetheris.orchestrator.stage15;

import io.aetheris.orchestrator.stage14.Stage14OperationalIntelligenceService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class Stage15IncidentReplayService {
    private final Stage14OperationalIntelligenceService operations;
    private final Stage15RecoveryPlanRepository recoveries;

    public Stage15IncidentReplayService(Stage14OperationalIntelligenceService operations, Stage15RecoveryPlanRepository recoveries) {
        this.operations = operations; this.recoveries = recoveries;
    }

    public IncidentReplay replay(UUID incidentId) {
        var incident = operations.incident(incidentId);
        List<ReplayEvent> events = new ArrayList<>();
        operations.signalsForIncident(incidentId).forEach(signal -> events.add(new ReplayEvent(signal.getObservedAt(),
                "SIGNAL", signal.getSeverity(), signal.getSignalType() + " from " + signal.getSourceType() + ":" + signal.getSourceId(),
                signal.getAttestationSha256())));
        recoveries.findTop100ByIncidentIdOrderByUpdatedAtDesc(incidentId).forEach(plan -> {
            events.add(new ReplayEvent(plan.getCreatedAt(), "RECOVERY_PLAN", "INFO", plan.getAction() + " for " + plan.getServiceId(), plan.getPlanSha256()));
            events.add(new ReplayEvent(plan.getUpdatedAt(), "RECOVERY_STATE", plan.isRollbackRequired() ? "WARN" : "INFO", plan.getStatus(),
                    plan.getVerificationAttestationSha256() != null ? plan.getVerificationAttestationSha256() : plan.getExecutionAttestationSha256()));
        });
        events.sort(Comparator.comparing(ReplayEvent::at));
        return new IncidentReplay("REPLAY_READY", incidentId, incident.getStatus(), incident.getSeverity(), List.copyOf(events), true, false);
    }

    public record ReplayEvent(Instant at, String type, String severity, String summary, String evidenceSha256) {}
    public record IncidentReplay(String status, UUID incidentId, String incidentStatus, String incidentSeverity,
                                 List<ReplayEvent> timeline, boolean readOnly, boolean externalActionAttempted) {}
}
