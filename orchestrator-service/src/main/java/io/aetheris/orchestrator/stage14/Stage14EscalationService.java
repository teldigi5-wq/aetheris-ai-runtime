package io.aetheris.orchestrator.stage14;

import io.aetheris.orchestrator.stage12.Stage12ExternalIntegrationService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class Stage14EscalationService {
    private final Stage14OperationalIntelligenceService operations;
    private final Stage12ExternalIntegrationService integrations;

    public Stage14EscalationService(Stage14OperationalIntelligenceService operations,
                                    Stage12ExternalIntegrationService integrations) {
        this.operations = operations;
        this.integrations = integrations;
    }

    public EscalationDecision evaluate(UUID incidentId, String notificationProviderId) {
        var incident = operations.incident(incidentId);
        String level = switch (incident.getSeverity()) {
            case "CRITICAL" -> "IMMEDIATE_OWNER";
            case "WARN" -> "OWNER_ATTENTION";
            default -> "AUDIT_ONLY";
        };
        if ("AUDIT_ONLY".equals(level)) return new EscalationDecision("NO_NOTIFICATION_REQUIRED", level,
                List.of(), false, false, "Informational incident remains in the audit trail");
        List<String> blockers = new ArrayList<>();
        if (notificationProviderId == null || notificationProviderId.isBlank()) blockers.add("notification provider id is required");
        else {
            try {
                var ready = integrations.notification(notificationProviderId);
                blockers.addAll(ready.blockers());
            } catch (RuntimeException e) { blockers.add(e.getMessage()); }
        }
        return new EscalationDecision(blockers.isEmpty() ? "ESCALATION_READY_NOT_SENT" : "BLOCKED",
                level, List.copyOf(blockers), false, false,
                blockers.isEmpty() ? "Notification provider is eligible; Stage 14 still does not send the notification"
                        : "Escalation remains evidence-gated");
    }

    public record EscalationDecision(String status, String escalationLevel, List<String> blockers,
                                     boolean notificationSent, boolean externalActionAttempted, String detail) {}
}
