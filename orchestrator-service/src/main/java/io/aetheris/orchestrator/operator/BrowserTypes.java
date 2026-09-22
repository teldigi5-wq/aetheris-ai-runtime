package io.aetheris.orchestrator.operator;

import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.policy.OperationMode;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BrowserTypes {
    private BrowserTypes() {}

    public enum BrowserActionType {
        NAVIGATE,
        CLICK,
        TYPE,
        UPLOAD,
        DOWNLOAD,
        SCREENSHOT,
        EXTRACT_TEXT,
        WAIT
    }

    public enum BrowserEffect {
        OBSERVE,
        LOCAL_DRAFT,
        EXTERNAL_CHANGE,
        DESTRUCTIVE_CHANGE,
        SECURITY_CHANGE,
        FINANCIAL_CHANGE
    }

    public enum BrowserPlanStatus {
        READY,
        APPROVAL_REQUIRED,
        BLOCKED,
        PHYSICAL_VALIDATION_REQUIRED,
        RUNTIME_UNAVAILABLE
    }

    public enum BrowserExecutionStatus {
        SUCCEEDED,
        FAILED,
        BLOCKED,
        APPROVAL_REQUIRED,
        PHYSICAL_VALIDATION_REQUIRED,
        RUNTIME_UNAVAILABLE,
        CANCELLED
    }

    public record BrowserAction(
            String actionId,
            BrowserActionType type,
            String url,
            String selector,
            String valueRef,
            String fileRef,
            BrowserEffect effect,
            String description,
            Integer timeoutSeconds
    ) {
        public BrowserAction {
            actionId = actionId == null ? "" : actionId.trim();
            url = url == null ? "" : url.trim();
            selector = selector == null ? "" : selector.trim();
            valueRef = valueRef == null ? "" : valueRef.trim();
            fileRef = fileRef == null ? "" : fileRef.trim();
            description = description == null ? "" : description.trim();
        }
    }

    public record BrowserPlanRequest(
            UUID taskId,
            String agentId,
            OperationMode mode,
            boolean protectedData,
            Set<String> allowedDomains,
            List<BrowserAction> actions
    ) {
        public BrowserPlanRequest {
            mode = mode == null ? OperationMode.BALANCED : mode;
            allowedDomains = allowedDomains == null ? Set.of() : Set.copyOf(allowedDomains);
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }

    public record BrowserActionDecision(
            String actionId,
            BrowserActionType type,
            BrowserEffect effect,
            RiskLevel riskLevel,
            String targetHost,
            boolean allowed,
            boolean requiresApproval,
            List<String> policyReasons,
            List<String> evidenceRequired,
            String detail
    ) {
        public BrowserActionDecision {
            policyReasons = policyReasons == null ? List.of() : List.copyOf(policyReasons);
            evidenceRequired = evidenceRequired == null ? List.of() : List.copyOf(evidenceRequired);
            detail = detail == null ? "" : detail;
        }
    }

    public record BrowserWorkflowPlan(
            BrowserPlanStatus status,
            boolean executableNow,
            boolean requiresApproval,
            String runtimeId,
            String physicalMachineStatus,
            List<BrowserActionDecision> actions,
            List<String> blockedReasons,
            String summary
    ) {
        public BrowserWorkflowPlan {
            actions = actions == null ? List.of() : List.copyOf(actions);
            blockedReasons = blockedReasons == null ? List.of() : List.copyOf(blockedReasons);
            summary = summary == null ? "" : summary;
        }
    }

    public record BrowserExecuteRequest(
            BrowserPlanRequest workflow,
            Map<String, String> valuesByRef,
            Map<String, String> filesByRef
    ) {
        public BrowserExecuteRequest {
            valuesByRef = valuesByRef == null ? Map.of() : Map.copyOf(valuesByRef);
            filesByRef = filesByRef == null ? Map.of() : Map.copyOf(filesByRef);
        }
    }

    public record BrowserArtifactEvidence(
            String relativePath,
            long sizeBytes,
            String sha256
    ) {
        public BrowserArtifactEvidence {
            relativePath = relativePath == null ? "" : relativePath;
            sha256 = sha256 == null ? "" : sha256;
        }
    }

    public record BrowserActionResult(
            String actionId,
            BrowserActionType type,
            boolean success,
            String detail,
            String evidenceRef,
            BrowserArtifactEvidence artifactEvidence
    ) {
        public BrowserActionResult {
            detail = detail == null ? "" : detail;
            evidenceRef = evidenceRef == null ? "" : evidenceRef;
        }

        public BrowserActionResult(
                String actionId,
                BrowserActionType type,
                boolean success,
                String detail,
                String evidenceRef) {
            this(actionId, type, success, detail, evidenceRef, null);
        }
    }

    public record BrowserExecutionResponse(
            BrowserExecutionStatus status,
            String detail,
            String finalUrl,
            List<BrowserActionResult> actions,
            UUID auditId
    ) {
        public BrowserExecutionResponse {
            detail = detail == null ? "" : detail;
            finalUrl = finalUrl == null ? "" : finalUrl;
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }

    public record BrowserRuntimeStatus(
            String runtimeId,
            boolean repositoryAdapterImplemented,
            boolean runtimeEnabled,
            boolean physicalValidated,
            String webdriverEndpoint,
            String physicalMachineStatus,
            String detail
    ) {}
}
