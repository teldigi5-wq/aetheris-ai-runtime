package io.aetheris.orchestrator.stage31;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Stage31ProactiveIntelligenceEngine {

    public List<ProactiveIssue> scan(
            ProjectDigitalTwin project,
            PcDigitalTwin pc,
            OwnerWorkspaceModel workspace) {
        List<ProactiveIssue> issues = new ArrayList<>();

        if (!project.ciHealthy()) {
            issues.add(issue("PROJECT_CI_FAILED", IssueCategory.CI_FAILURE, IssueSeverity.HIGH,
                    project.projectId(), "Project CI is failing",
                    "Inspect the failed checks and verified logs before promotion or deployment.",
                    project.evidence(), true));
        }
        if (project.failedTests() > 0) {
            issues.add(issue("PROJECT_TESTS_FAILED", IssueCategory.TEST_FAILURE, IssueSeverity.HIGH,
                    project.projectId(), project.failedTests() + " project test(s) are failing",
                    "Repair the failing tests and rerun the affected verification gates.",
                    project.evidence(), true));
        }
        if (!project.docsComplete()) {
            issues.add(issue("PROJECT_DOCS_INCOMPLETE", IssueCategory.DOCUMENTATION_GAP, IssueSeverity.WARNING,
                    project.projectId(), "Project documentation is incomplete",
                    "Update architecture, runbook, or roadmap evidence before declaring the stage complete.",
                    project.evidence(), false));
        }
        if (project.dependencyDrift()) {
            issues.add(issue("PROJECT_DEPENDENCY_DRIFT", IssueCategory.DEPENDENCY_DRIFT, IssueSeverity.HIGH,
                    project.projectId(), "Dependency evidence has drifted",
                    "Regenerate dependency evidence and review every intentional version change.",
                    project.evidence(), true));
        }
        if (!project.deploymentHealthy()) {
            issues.add(issue("PROJECT_DEPLOYMENT_UNHEALTHY", IssueCategory.DEPLOYMENT_UNHEALTHY, IssueSeverity.HIGH,
                    project.projectId(), "Deployment health is not green",
                    "Hold promotion and use the existing rollback/checkpoint path until health is verified.",
                    project.evidence(), true));
        }
        if (project.quotaRemainingPercent() <= 5.0) {
            issues.add(issue("PROJECT_QUOTA_LOW", IssueCategory.QUOTA_EXHAUSTION, IssueSeverity.WARNING,
                    project.projectId(), "Provider or project quota is nearly exhausted",
                    "Prefer local or verified zero-cost routes and avoid creating a billable fallback.",
                    project.evidence(), true));
        }

        IssueSeverity pcPressureSeverity = pc.physicalEvidenceVerified()
                ? IssueSeverity.HIGH
                : IssueSeverity.WARNING;
        boolean pcRequiresOwner = pc.physicalEvidenceVerified();
        String syntheticPrefix = pc.physicalEvidenceVerified()
                ? ""
                : "Snapshot is not physically verified; treat this as diagnostic evidence only. ";

        if (pc.diskFreePercent() < 15.0) {
            issues.add(issue("PC_LOW_DISK", IssueCategory.LOW_DISK, pcPressureSeverity,
                    pc.hostId(), "PC digital twin reports low free disk space",
                    syntheticPrefix + "Plan reversible cleanup candidates; do not delete user files automatically.",
                    pc.evidence(), pcRequiresOwner));
        }
        if (pc.memoryUsedPercent() >= 95.0 || pc.temperatureC() >= 90.0) {
            issues.add(issue("PC_RESOURCE_PRESSURE", IssueCategory.RESOURCE_PRESSURE, pcPressureSeverity,
                    pc.hostId(), "PC digital twin reports resource pressure",
                    syntheticPrefix + "Reduce nonessential workload first and preserve security controls and user responsiveness.",
                    pc.evidence(), pcRequiresOwner));
        }
        if (!pc.failedServices().isEmpty()) {
            String failed = pc.failedServices().stream().sorted(Comparator.naturalOrder()).toList().toString();
            issues.add(issue("PC_SERVICE_FAILURE", IssueCategory.SERVICE_FAILURE, pcPressureSeverity,
                    pc.hostId(), "Services reported failed: " + failed,
                    syntheticPrefix + "Create a bounded restart/recovery plan and verify service health before promotion.",
                    pc.evidence(), pcRequiresOwner));
        }
        if (!pc.physicalEvidenceVerified()) {
            issues.add(issue("PC_PHYSICAL_VALIDATION_PENDING", IssueCategory.PHYSICAL_VALIDATION_PENDING,
                    IssueSeverity.INFO, pc.hostId(), "Physical-PC validation is still pending",
                    "Do not convert hosted-CI or synthetic telemetry into claims about the owner's real machine.",
                    pc.evidence(), false));
        }
        if (!workspace.activeProjectId().equals(project.projectId())) {
            issues.add(issue("WORKSPACE_PROJECT_DRIFT", IssueCategory.WORKSPACE_DRIFT, IssueSeverity.WARNING,
                    workspace.ownerId(), "Owner workspace and active project digital twin disagree",
                    "Reconcile project context before autonomous prioritization or recovery planning.",
                    TwinEvidence.OBSERVED, true));
        }

        return List.copyOf(issues);
    }

    private static ProactiveIssue issue(
            String code,
            IssueCategory category,
            IssueSeverity severity,
            String sourceId,
            String summary,
            String recommendation,
            TwinEvidence evidence,
            boolean ownerAttentionRequired) {
        return new ProactiveIssue(code, category, severity, sourceId, summary, recommendation,
                evidence, ownerAttentionRequired);
    }
}
