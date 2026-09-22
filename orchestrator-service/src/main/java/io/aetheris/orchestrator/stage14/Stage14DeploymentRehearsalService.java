package io.aetheris.orchestrator.stage14;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class Stage14DeploymentRehearsalService {
    private final Stage14ChangeImpactService impact;

    public Stage14DeploymentRehearsalService(Stage14ChangeImpactService impact) { this.impact = impact; }

    public RehearsalResult rehearse(RehearsalRequest request) {
        if (request == null) throw new IllegalArgumentException("Deployment rehearsal request is required");
        Stage14ChangeImpactService.ChangeImpact change = impact.assess(request.changeImpact());
        List<String> blockers = new ArrayList<>(change.blockers());
        if (!request.rollbackAvailable()) blockers.add("rollback material is required for rehearsal readiness");
        if (!validSha(request.evidenceSha256())) blockers.add("rehearsal evidence SHA-256 is required");
        if (!request.dependencyEvidenceComplete()) blockers.add("dependency evidence is incomplete");
        if ("CRITICAL".equals(change.riskLevel())) blockers.add("critical change-impact score requires redesign or explicit later-stage exception review");
        String status = blockers.isEmpty() ? "REHEARSAL_PASS" : "REHEARSAL_BLOCKED";
        return new RehearsalResult(status, change, List.copyOf(blockers), true,
                false, false, true,
                blockers.isEmpty() ? "Digital-twin rehearsal passed; no production action was executed"
                        : "Deployment must not proceed from this rehearsal");
    }

    private boolean validSha(String value) {
        return value != null && value.trim().toLowerCase(Locale.ROOT).matches("[a-f0-9]{64}");
    }

    public record RehearsalRequest(Stage14ChangeImpactService.ChangeImpactRequest changeImpact,
                                   boolean rollbackAvailable, boolean dependencyEvidenceComplete,
                                   String evidenceSha256) {}
    public record RehearsalResult(String status, Stage14ChangeImpactService.ChangeImpact changeImpact,
                                  List<String> blockers, boolean simulationOnly,
                                  boolean externalActionAttempted, boolean productionPromoted,
                                  boolean rollbackRequired, String detail) {}
}
