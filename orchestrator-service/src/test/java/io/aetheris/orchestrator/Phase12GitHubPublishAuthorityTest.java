package io.aetheris.orchestrator;

import io.aetheris.orchestrator.approval.ApprovalService;
import io.aetheris.orchestrator.execution.InvocationAuditEntity;
import io.aetheris.orchestrator.execution.InvocationAuditService;
import io.aetheris.orchestrator.github.GitHubAdapterService;
import io.aetheris.orchestrator.github.GitHubChangeProposalEntity;
import io.aetheris.orchestrator.github.GitHubChangeProposalRepository;
import io.aetheris.orchestrator.github.GitHubProposalPublishService;
import io.aetheris.orchestrator.task.DirectExecutionAuthorityService;
import io.aetheris.orchestrator.task.TaskControlService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class Phase12GitHubPublishAuthorityTest {

    @Test
    void approvedPublishStillRequiresCurrentRunningGithubSpecialistBeforeNetworkEffect() {
        Fixture fixture = fixture();
        when(fixture.approvals.hasApproved(fixture.taskId, fixture.actionType())).thenReturn(true);
        when(fixture.github.publishExistingFile(fixture.proposal)).thenReturn("abc123");

        var result = fixture.publisher.publish(fixture.proposalId);

        assertThat(result.published()).isTrue();
        assertThat(result.commitSha()).isEqualTo("abc123");
        InOrder order = inOrder(fixture.authority, fixture.approvals, fixture.github);
        order.verify(fixture.authority).requireRunningSpecialist(fixture.taskId, "backend-engineer", "github");
        order.verify(fixture.approvals).hasApproved(fixture.taskId, fixture.actionType());
        order.verify(fixture.github).publishExistingFile(fixture.proposal);
        verify(fixture.repository).save(fixture.proposal);
    }

    @Test
    void staleOwnerApprovalCannotOverrideReassignedOrPausedTaskAuthority() {
        Fixture fixture = fixture();
        when(fixture.approvals.hasApproved(fixture.taskId, fixture.actionType())).thenReturn(true);
        doThrow(new IllegalStateException("Direct execution agent backend-engineer is not the task's active specialist frontend-engineer"))
                .when(fixture.authority)
                .requireRunningSpecialist(fixture.taskId, "backend-engineer", "github");

        var result = fixture.publisher.publish(fixture.proposalId);

        assertThat(result.published()).isFalse();
        assertThat(result.detail()).contains("GitHub publish authority denied");
        assertThat(result.detail()).contains("not the task's active specialist");
        verify(fixture.approvals, never()).hasApproved(any(), any());
        verifyNoInteractions(fixture.github);
        verify(fixture.audit).finish(eq(fixture.auditId), any(), eq(result.detail()), anyMap());
    }

    @Test
    void authorityDoesNotReplaceExactProposalOwnerApproval() {
        Fixture fixture = fixture();
        when(fixture.approvals.hasApproved(fixture.taskId, fixture.actionType())).thenReturn(false);

        var result = fixture.publisher.publish(fixture.proposalId);

        assertThat(result.published()).isFalse();
        assertThat(result.detail()).contains("Explicit owner approval");
        verify(fixture.authority).requireRunningSpecialist(fixture.taskId, "backend-engineer", "github");
        verify(fixture.approvals).hasApproved(fixture.taskId, fixture.actionType());
        verifyNoInteractions(fixture.github);
    }

    private Fixture fixture() {
        UUID taskId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        UUID auditId = UUID.randomUUID();
        GitHubChangeProposalEntity proposal = new GitHubChangeProposalEntity(
                proposalId,
                taskId,
                "backend-engineer",
                "teldigi5-wq/aetheris-platform",
                "README.md",
                "main",
                "proposed content",
                "Phase 12 authority proof");

        GitHubChangeProposalRepository repository = mock(GitHubChangeProposalRepository.class);
        GitHubAdapterService github = mock(GitHubAdapterService.class);
        ApprovalService approvals = mock(ApprovalService.class);
        InvocationAuditService audit = mock(InvocationAuditService.class);
        TaskControlService control = mock(TaskControlService.class);
        DirectExecutionAuthorityService authority = mock(DirectExecutionAuthorityService.class);
        InvocationAuditEntity auditEntry = mock(InvocationAuditEntity.class);

        when(repository.findById(proposalId)).thenReturn(Optional.of(proposal));
        when(control.isEmergencyStopActive()).thenReturn(false);
        when(auditEntry.getId()).thenReturn(auditId);
        when(audit.start(eq(taskId), eq("backend-engineer"), any(), eq("github.publish"), anyMap()))
                .thenReturn(auditEntry);

        GitHubProposalPublishService publisher = new GitHubProposalPublishService(
                repository, github, approvals, audit, control, authority);
        return new Fixture(taskId, proposalId, auditId, proposal, repository, github, approvals, audit, authority, publisher);
    }

    private record Fixture(
            UUID taskId,
            UUID proposalId,
            UUID auditId,
            GitHubChangeProposalEntity proposal,
            GitHubChangeProposalRepository repository,
            GitHubAdapterService github,
            ApprovalService approvals,
            InvocationAuditService audit,
            DirectExecutionAuthorityService authority,
            GitHubProposalPublishService publisher) {
        String actionType() {
            return "github.publish:" + proposalId;
        }
    }
}
