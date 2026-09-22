package io.aetheris.orchestrator.operator;

import io.aetheris.orchestrator.policy.OperationMode;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.aetheris.orchestrator.operator.BrowserTypes.*;

public final class SiteSkillTypes {
    private SiteSkillTypes() {}

    public enum SiteSkillKind {
        OBSERVE,
        MUTATE
    }

    public enum SiteSkillStatus {
        REPOSITORY_TEMPLATE,
        PHYSICAL_VALIDATION_REQUIRED,
        VALIDATED
    }

    public enum WebTaskStatus {
        READY,
        APPROVAL_REQUIRED,
        BLOCKED,
        SKILL_VALIDATION_REQUIRED,
        PHYSICAL_VALIDATION_REQUIRED,
        RUNTIME_UNAVAILABLE,
        SUCCEEDED,
        FAILED,
        CANCELLED
    }

    public record SiteSkillDescriptor(
            String id,
            String displayName,
            String site,
            SiteSkillKind kind,
            Set<String> allowedDomains,
            boolean authenticationExpected,
            boolean physicallyValidated,
            Set<String> requiredParameters,
            Set<String> requiredValueRefs,
            List<String> evidenceRequirements,
            String truthNote
    ) {
        public SiteSkillDescriptor {
            allowedDomains = allowedDomains == null ? Set.of() : Set.copyOf(allowedDomains);
            requiredParameters = requiredParameters == null ? Set.of() : Set.copyOf(requiredParameters);
            requiredValueRefs = requiredValueRefs == null ? Set.of() : Set.copyOf(requiredValueRefs);
            evidenceRequirements = evidenceRequirements == null ? List.of() : List.copyOf(evidenceRequirements);
            truthNote = truthNote == null ? "" : truthNote;
        }

        public SiteSkillStatus status() {
            return physicallyValidated ? SiteSkillStatus.VALIDATED : SiteSkillStatus.PHYSICAL_VALIDATION_REQUIRED;
        }
    }

    public record WebTaskRequest(
            UUID taskId,
            String agentId,
            OperationMode mode,
            boolean protectedData,
            String skillId,
            Map<String, String> parameters
    ) {
        public WebTaskRequest {
            mode = mode == null ? OperationMode.BALANCED : mode;
            skillId = skillId == null ? "" : skillId.trim();
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    public record WebTaskCompilation(
            WebTaskStatus status,
            SiteSkillDescriptor skill,
            BrowserPlanRequest browserWorkflow,
            BrowserWorkflowPlan browserPlan,
            int maxAutomaticAttempts,
            boolean blindRetryAllowed,
            List<String> warnings,
            String summary
    ) {
        public WebTaskCompilation {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            summary = summary == null ? "" : summary;
        }
    }

    public record WebTaskExecuteRequest(
            WebTaskRequest task,
            Map<String, String> valuesByRef,
            Map<String, String> filesByRef
    ) {
        public WebTaskExecuteRequest {
            valuesByRef = valuesByRef == null ? Map.of() : Map.copyOf(valuesByRef);
            filesByRef = filesByRef == null ? Map.of() : Map.copyOf(filesByRef);
        }
    }

    public record WebTaskExecutionResponse(
            WebTaskStatus status,
            String skillId,
            int attempts,
            boolean retried,
            BrowserExecutionResponse browserExecution,
            List<String> evidenceRequirements,
            String detail
    ) {
        public WebTaskExecutionResponse {
            skillId = skillId == null ? "" : skillId;
            evidenceRequirements = evidenceRequirements == null ? List.of() : List.copyOf(evidenceRequirements);
            detail = detail == null ? "" : detail;
        }
    }
}
