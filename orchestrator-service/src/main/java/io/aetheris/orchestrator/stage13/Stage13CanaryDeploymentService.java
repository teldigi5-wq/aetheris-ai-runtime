package io.aetheris.orchestrator.stage13;

import io.aetheris.orchestrator.approval.ApprovalService;
import io.aetheris.orchestrator.approval.ApprovalStatus;
import io.aetheris.orchestrator.stage12.Stage12ReleaseTrustService;
import io.aetheris.orchestrator.stage12.Stage12TargetAttestationService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class Stage13CanaryDeploymentService {
    public static final String APPROVAL_ACTION = "STAGE13_CANARY_DEPLOY";

    private final Stage12ReleaseTrustService releaseTrust;
    private final ApprovalService approvals;
    private final Stage12TargetAttestationService targetAttestations;

    public Stage13CanaryDeploymentService(Stage12ReleaseTrustService releaseTrust,
                                          ApprovalService approvals,
                                          Stage12TargetAttestationService targetAttestations) {
        this.releaseTrust = releaseTrust;
        this.approvals = approvals;
        this.targetAttestations = targetAttestations;
    }

    public CanaryDecision evaluate(CanaryRequest request) {
        if (request == null || request.taskId() == null || request.approvalId() == null) {
            throw new IllegalArgumentException("taskId and approvalId are required");
        }
        List<String> blockers = new ArrayList<>();
        var approval = approvals.getRequired(request.approvalId());
        if (approval.getStatus() != ApprovalStatus.APPROVED) blockers.add("owner approval is not approved");
        if (!APPROVAL_ACTION.equals(approval.getActionType())) blockers.add("approval action type must be " + APPROVAL_ACTION);
        if (!request.taskId().equals(approval.getTaskId())) blockers.add("approval task does not match canary deployment task");

        var release = releaseTrust.verify(request.releaseManifest());
        if (!"CRYPTOGRAPHICALLY_VERIFIED".equals(release.status())) blockers.addAll(release.blockers());

        Stage12TargetAttestationService.TargetAttestation target;
        try {
            target = targetAttestations.assess(request.targetKind(), request.targetAttestationSha256());
            if (!target.targetMeasured()) blockers.add("exact target attestation is not available");
        } catch (RuntimeException e) {
            target = null;
            blockers.add(e.getMessage());
        }

        String rollback = normalizeSha(request.rollbackArtifactSha256());
        if (rollback == null) blockers.add("rollback artifact SHA-256 is required");
        if (request.releaseManifest() != null && rollback != null
                && rollback.equalsIgnoreCase(request.releaseManifest().artifactSha256())) {
            blockers.add("rollback artifact must be distinct from the candidate artifact");
        }
        return new CanaryDecision(blockers.isEmpty() ? "CANARY_ELIGIBLE_NOT_EXECUTED" : "BLOCKED",
                List.copyOf(blockers), release.status(), target == null ? "EVIDENCE_REQUIRED" : target.status(),
                rollback != null, false, true, false,
                blockers.isEmpty() ? "Signed candidate, exact target proof, owner approval and rollback artifact are present; deployment has not been executed"
                        : "Canary deployment must not execute");
    }

    private String normalizeSha(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.matches("[a-f0-9]{64}") ? v : null;
    }

    public record CanaryRequest(UUID taskId, UUID approvalId, Stage12ReleaseTrustService.ReleaseManifest releaseManifest,
                                String targetKind, String targetAttestationSha256, String rollbackArtifactSha256) {}
    public record CanaryDecision(String status, List<String> blockers, String releaseStatus, String targetStatus,
                                 boolean rollbackArtifactPresent, boolean externalActionAttempted,
                                 boolean rollbackRequired, boolean productionPromoted, String detail) {}
}
