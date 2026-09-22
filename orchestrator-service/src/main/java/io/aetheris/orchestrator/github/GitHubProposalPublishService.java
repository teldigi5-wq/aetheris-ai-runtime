package io.aetheris.orchestrator.github;

import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.approval.ApprovalEntity;
import io.aetheris.orchestrator.approval.ApprovalService;
import io.aetheris.orchestrator.approval.CreateApprovalRequest;
import io.aetheris.orchestrator.execution.InvocationAuditEntity;
import io.aetheris.orchestrator.execution.InvocationAuditService;
import io.aetheris.orchestrator.execution.InvocationKind;
import io.aetheris.orchestrator.execution.InvocationStatus;
import io.aetheris.orchestrator.task.DirectExecutionAuthorityService;
import io.aetheris.orchestrator.task.TaskControlService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class GitHubProposalPublishService {

    private static final String DIRECT_TOOL_FAMILY = "github";

    private final GitHubChangeProposalRepository proposals;
    private final GitHubAdapterService github;
    private final ApprovalService approvals;
    private final InvocationAuditService audit;
    private final TaskControlService control;
    private final DirectExecutionAuthorityService directAuthority;

    public GitHubProposalPublishService(GitHubChangeProposalRepository proposals, GitHubAdapterService github,
                                        ApprovalService approvals, InvocationAuditService audit, TaskControlService control,
                                        DirectExecutionAuthorityService directAuthority) {
        this.proposals = proposals;
        this.github = github;
        this.approvals = approvals;
        this.audit = audit;
        this.control = control;
        this.directAuthority = directAuthority;
    }

    public ApprovalEntity requestApproval(UUID proposalId) {
        GitHubChangeProposalEntity proposal = getRequired(proposalId);
        if (proposal.getTaskId() == null) throw new IllegalStateException("GitHub publish approval requires a task-bound proposal");
        if (proposal.getStatus() != GitHubProposalStatus.PROPOSED) throw new IllegalStateException("Only proposed changes can request publish approval");
        return approvals.request(new CreateApprovalRequest(
                proposal.getTaskId(), actionType(proposalId),
                "Publish approved GitHub proposal " + proposalId + " to " + proposal.getRepository() + "/" + proposal.getPath(),
                RiskLevel.HIGH));
    }

    public GitHubPublishResult publish(UUID proposalId) {
        GitHubChangeProposalEntity proposal = getRequired(proposalId);
        InvocationAuditEntity entry = audit.start(proposal.getTaskId(), proposal.getAgentId(), InvocationKind.TOOL,
                "github.publish", Map.of("proposalId", proposalId.toString(), "repository", proposal.getRepository(), "path", proposal.getPath()));
        if (control.isEmergencyStopActive()) {
            audit.finish(entry.getId(), InvocationStatus.CANCELLED, "Emergency stop is active", Map.of());
            return new GitHubPublishResult(proposalId, false, null, "Emergency stop is active");
        }
        if (proposal.getTaskId() == null) {
            audit.finish(entry.getId(), InvocationStatus.BLOCKED, "GitHub publish requires a task-bound proposal", Map.of());
            return new GitHubPublishResult(proposalId, false, null, "GitHub publish requires a task-bound proposal");
        }
        try {
            directAuthority.requireRunningSpecialist(proposal.getTaskId(), proposal.getAgentId(), DIRECT_TOOL_FAMILY);
        } catch (RuntimeException exception) {
            String reason = safeMessage(exception);
            String detail = "GitHub publish authority denied: " + reason;
            audit.finish(entry.getId(), InvocationStatus.BLOCKED, detail, Map.of("authorityDenied", true));
            return new GitHubPublishResult(proposalId, false, null, detail);
        }
        if (!approvals.hasApproved(proposal.getTaskId(), actionType(proposalId))) {
            audit.finish(entry.getId(), InvocationStatus.BLOCKED, "Explicit owner approval is required for this exact proposal", Map.of());
            return new GitHubPublishResult(proposalId, false, null, "Explicit owner approval is required for this exact proposal");
        }
        try {
            String commitSha = github.publishExistingFile(proposal);
            proposal.markPublished(commitSha);
            proposals.save(proposal);
            audit.finish(entry.getId(), InvocationStatus.SUCCEEDED, "GitHub proposal published", Map.of("commitSha", commitSha));
            return new GitHubPublishResult(proposalId, true, commitSha, "GitHub proposal published");
        } catch (RuntimeException exception) {
            String detail = safeMessage(exception);
            proposal.markFailed(detail);
            proposals.save(proposal);
            audit.finish(entry.getId(), InvocationStatus.FAILED, detail, Map.of());
            return new GitHubPublishResult(proposalId, false, null, detail);
        }
    }

    private GitHubChangeProposalEntity getRequired(UUID id) {
        return proposals.findById(id).orElseThrow(() -> new NoSuchElementException("Unknown GitHub proposal: " + id));
    }

    private String actionType(UUID proposalId) {
        return "github.publish:" + proposalId;
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
