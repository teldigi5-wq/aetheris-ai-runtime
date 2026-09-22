package io.aetheris.orchestrator;

import io.aetheris.orchestrator.approval.ApprovalService;
import io.aetheris.orchestrator.connector.ConnectorConnectionRepository;
import io.aetheris.orchestrator.connector.ConnectorProvider;
import io.aetheris.orchestrator.connector.action.ConnectorActionBlockedException;
import io.aetheris.orchestrator.connector.action.ConnectorActionEntity;
import io.aetheris.orchestrator.connector.action.ConnectorActionExecutionMode;
import io.aetheris.orchestrator.connector.action.ConnectorActionKind;
import io.aetheris.orchestrator.connector.action.ConnectorActionRepository;
import io.aetheris.orchestrator.connector.action.ConnectorActionService;
import io.aetheris.orchestrator.connector.action.ConnectorActionStatus;
import io.aetheris.orchestrator.connector.action.LiveConnectorWriteExecutor;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.task.DirectExecutionAuthorityService;
import io.aetheris.orchestrator.task.TaskEntity;
import io.aetheris.orchestrator.task.TaskService;
import io.aetheris.orchestrator.task.TaskState;
import io.aetheris.orchestrator.task.TaskVerificationService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class Phase12ConnectorLiveAuthorityTest {

    @Test
    void liveConnectorAuthorityDenialNeverReachesProviderExecutor() {
        Fixture fixture = fixture();
        when(fixture.authority.requireRunningSpecialist(
                fixture.taskId, "automation-engineer", "automation", OperationMode.PRIVATE))
                .thenThrow(new IllegalStateException("Direct execution agent automation-engineer is not the task's active specialist career-director"));

        assertThatThrownBy(() -> fixture.service.execute(fixture.actionId))
                .isInstanceOf(ConnectorActionBlockedException.class)
                .hasMessageContaining("Live connector write authority denied")
                .hasMessageContaining("not the task's active specialist");

        verifyNoInteractions(fixture.liveExecutor);
        verify(fixture.actions, never()).save(any());
    }

    @Test
    void liveConnectorExecutesOnlyAfterExactAutomationAuthorityPasses() {
        Fixture fixture = fixture();
        when(fixture.authority.requireRunningSpecialist(
                fixture.taskId, "automation-engineer", "automation", OperationMode.PRIVATE))
                .thenReturn(fixture.task);
        when(fixture.liveExecutor.execute(fixture.action)).thenReturn("https://github.com/example/repo/issues/42");
        when(fixture.actions.save(fixture.action)).thenReturn(fixture.action);

        var result = fixture.service.execute(fixture.actionId);

        assertThat(result.status()).isEqualTo(ConnectorActionStatus.EXECUTED);
        assertThat(result.externalReference()).isEqualTo("https://github.com/example/repo/issues/42");
        verify(fixture.authority).requireRunningSpecialist(
                fixture.taskId, "automation-engineer", "automation", OperationMode.PRIVATE);
        verify(fixture.liveExecutor).execute(fixture.action);
        verify(fixture.verification).recordDecision(
                fixture.taskId,
                "mcp-integration-engineer",
                true,
                "CONNECTOR_RECEIPT",
                "Independent connector receipt verification passed");
    }

    private Fixture fixture() {
        ConnectorActionRepository actions = mock(ConnectorActionRepository.class);
        ConnectorConnectionRepository connections = mock(ConnectorConnectionRepository.class);
        TaskService tasks = mock(TaskService.class);
        ApprovalService approvals = mock(ApprovalService.class);
        LiveConnectorWriteExecutor liveExecutor = mock(LiveConnectorWriteExecutor.class);
        TaskVerificationService verification = mock(TaskVerificationService.class);
        DirectExecutionAuthorityService authority = mock(DirectExecutionAuthorityService.class);
        TaskEntity task = mock(TaskEntity.class);

        UUID actionId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        ConnectorActionEntity action = new ConnectorActionEntity(
                actionId,
                connectionId,
                ConnectorProvider.GITHUB,
                ConnectorActionKind.GITHUB_CREATE_ISSUE,
                ConnectorActionExecutionMode.LIVE,
                "phase12-live-authority",
                "example/repo",
                "Create a governed test issue",
                taskId,
                approvalId,
                "CONNECTOR_WRITE_GITHUB_CREATE_ISSUE");

        when(actions.findById(actionId)).thenReturn(Optional.of(action));
        when(approvals.hasApproved(taskId, "CONNECTOR_WRITE_GITHUB_CREATE_ISSUE")).thenReturn(true);
        when(tasks.getRequired(taskId)).thenReturn(task);
        when(task.getId()).thenReturn(taskId);
        when(task.getState()).thenReturn(TaskState.RUNNING);

        ConnectorActionService service = new ConnectorActionService(
                actions,
                connections,
                tasks,
                approvals,
                liveExecutor,
                verification,
                authority,
                true);

        return new Fixture(service, actions, liveExecutor, verification, authority, task, action, taskId, actionId);
    }

    private record Fixture(
            ConnectorActionService service,
            ConnectorActionRepository actions,
            LiveConnectorWriteExecutor liveExecutor,
            TaskVerificationService verification,
            DirectExecutionAuthorityService authority,
            TaskEntity task,
            ConnectorActionEntity action,
            UUID taskId,
            UUID actionId) {
    }
}
