package io.aetheris.orchestrator;

import io.aetheris.orchestrator.agent.AgentCatalogService;
import io.aetheris.orchestrator.agent.AgentDefinition;
import io.aetheris.orchestrator.agent.ModelClass;
import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.approval.ApprovalService;
import io.aetheris.orchestrator.execution.InvocationAuditEntity;
import io.aetheris.orchestrator.execution.InvocationAuditService;
import io.aetheris.orchestrator.host.HostCommandEnvelope;
import io.aetheris.orchestrator.host.HostCommandRequest;
import io.aetheris.orchestrator.host.HostCommandService;
import io.aetheris.orchestrator.host.HostNodeEntity;
import io.aetheris.orchestrator.host.HostRegistryService;
import io.aetheris.orchestrator.operator.BrowserOperatorService;
import io.aetheris.orchestrator.operator.BrowserTypes.BrowserAction;
import io.aetheris.orchestrator.operator.BrowserTypes.BrowserActionType;
import io.aetheris.orchestrator.operator.BrowserTypes.BrowserEffect;
import io.aetheris.orchestrator.operator.BrowserTypes.BrowserExecuteRequest;
import io.aetheris.orchestrator.operator.BrowserTypes.BrowserExecutionStatus;
import io.aetheris.orchestrator.operator.BrowserTypes.BrowserPlanRequest;
import io.aetheris.orchestrator.operator.W3cWebDriverBrowserAdapter;
import io.aetheris.orchestrator.policy.CompiledPolicyDecision;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.policy.OwnerRuleCompilerService;
import io.aetheris.orchestrator.task.DirectExecutionAuthorityService;
import io.aetheris.orchestrator.task.TaskControlService;
import io.aetheris.orchestrator.task.TaskEntity;
import io.aetheris.orchestrator.task.TaskService;
import io.aetheris.orchestrator.task.TaskState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Phase12DirectExecutionAuthorityTest {

    @Test
    void directExecutionRequiresRunningActiveSpecialistAndExactToolFamily() {
        TaskService tasks = mock(TaskService.class);
        AgentCatalogService agents = mock(AgentCatalogService.class);
        TaskEntity task = mock(TaskEntity.class);
        UUID taskId = UUID.randomUUID();

        when(tasks.getRequired(taskId)).thenReturn(task);
        when(task.getState()).thenReturn(TaskState.RUNNING);
        when(task.getActiveAgentId()).thenReturn("backend-engineer");
        when(task.getMode()).thenReturn(OperationMode.PRIVATE);
        when(agents.getRequired("backend-engineer")).thenReturn(agent("backend-engineer", List.of("github", "mcp")));
        when(agents.getRequired("frontend-engineer")).thenReturn(agent("frontend-engineer", List.of("browser")));

        DirectExecutionAuthorityService authority = new DirectExecutionAuthorityService(tasks, agents);

        assertThat(authority.requireRunningSpecialist(taskId, "backend-engineer", "mcp")).isSameAs(task);
        assertThat(authority.requireRunningSpecialist(taskId, "backend-engineer", "MCP", OperationMode.PRIVATE)).isSameAs(task);

        assertThatThrownBy(() -> authority.requireRunningSpecialist(taskId, "frontend-engineer", "browser"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not the task's active specialist");
        assertThatThrownBy(() -> authority.requireRunningSpecialist(taskId, "backend-engineer", "browser"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not authorized for direct tool family browser");
        assertThatThrownBy(() -> authority.requireRunningSpecialist(taskId, "backend-engineer", "mcp", OperationMode.BALANCED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mode mismatch");

        when(task.getState()).thenReturn(TaskState.PAUSED);
        assertThatThrownBy(() -> authority.requireRunningSpecialist(taskId, "backend-engineer", "mcp"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires a RUNNING task");
    }

    @Test
    void remoteHostIssuanceRequiresAuthorityAndExplicitOwnerApproval() {
        HostRegistryService hosts = mock(HostRegistryService.class);
        DirectExecutionAuthorityService authority = mock(DirectExecutionAuthorityService.class);
        ApprovalService approvals = mock(ApprovalService.class);
        HostNodeEntity host = mock(HostNodeEntity.class);
        TaskEntity task = mock(TaskEntity.class);
        UUID hostId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        when(hosts.getRequired(hostId)).thenReturn(host);
        when(host.getCapabilities()).thenReturn(Set.of("APP_LAUNCH"));
        when(authority.requireRunningSpecialist(taskId, "desktop-engineer", "pc-control")).thenReturn(task);

        HostCommandService remote = new HostCommandService(
                hosts, authority, approvals,
                "0123456789abcdef0123456789abcdef", false);
        HostCommandRequest request = new HostCommandRequest(
                hostId, "APP_LAUNCH", "open", Map.of("app", "code"), taskId, "desktop-engineer");

        when(approvals.hasApproved(taskId, "host:command")).thenReturn(false);
        assertThatThrownBy(() -> remote.issue(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Explicit owner approval");

        when(approvals.hasApproved(taskId, "host:command")).thenReturn(true);
        HostCommandEnvelope envelope = remote.issue(request);
        assertThat(envelope.mode()).isEqualTo("REMOTE");
        verify(authority, times(2)).requireRunningSpecialist(taskId, "desktop-engineer", "pc-control");
    }

    @Test
    void simulationHostProofDoesNotPretendToNeedLiveTaskAuthority() {
        HostRegistryService hosts = mock(HostRegistryService.class);
        DirectExecutionAuthorityService authority = mock(DirectExecutionAuthorityService.class);
        ApprovalService approvals = mock(ApprovalService.class);
        HostNodeEntity host = mock(HostNodeEntity.class);
        UUID hostId = UUID.randomUUID();

        when(hosts.getRequired(hostId)).thenReturn(host);
        when(host.getCapabilities()).thenReturn(Set.of("APP_LAUNCH"));

        HostCommandService simulation = new HostCommandService(
                hosts, authority, approvals,
                "0123456789abcdef0123456789abcdef", true);
        HostCommandEnvelope envelope = simulation.issue(new HostCommandRequest(
                hostId, "APP_LAUNCH", "open", Map.of("app", "code")));

        assertThat(envelope.mode()).isEqualTo("SIMULATION");
        verifyNoInteractions(authority, approvals);
    }

    @Test
    void browserAuthorityMustSucceedBeforeWebDriverReceivesAnyExecution() {
        UUID taskId = UUID.randomUUID();
        UUID auditId = UUID.randomUUID();
        TaskEntity task = mock(TaskEntity.class);
        AgentCatalogService agents = mock(AgentCatalogService.class);
        OwnerRuleCompilerService policy = mock(OwnerRuleCompilerService.class);
        ApprovalService approvals = mock(ApprovalService.class);
        TaskControlService control = mock(TaskControlService.class);
        TaskService tasks = mock(TaskService.class);
        DirectExecutionAuthorityService authority = mock(DirectExecutionAuthorityService.class);
        InvocationAuditService audit = mock(InvocationAuditService.class);
        InvocationAuditEntity auditEntry = mock(InvocationAuditEntity.class);
        W3cWebDriverBrowserAdapter adapter = mock(W3cWebDriverBrowserAdapter.class);

        when(tasks.getRequired(taskId)).thenReturn(task);
        when(agents.getRequired("frontend-engineer")).thenReturn(agent("frontend-engineer", List.of("browser")));
        when(policy.evaluate(any())).thenReturn(new CompiledPolicyDecision(true, false, false, false, List.of(), List.of()));
        when(authority.requireRunningSpecialist(taskId, "frontend-engineer", "browser", OperationMode.BALANCED)).thenReturn(task);
        when(audit.start(eq(taskId), eq("frontend-engineer"), any(), eq(BrowserOperatorService.RUNTIME_ID), anyMap())).thenReturn(auditEntry);
        when(auditEntry.getId()).thenReturn(auditId);
        when(adapter.execute(anyString(), anyString(), anyList(), anyMap(), anyMap(), anySet()))
                .thenReturn(new W3cWebDriverBrowserAdapter.AdapterExecution(true, "https://example.com/", List.of(), "mock browser success"));

        BrowserOperatorService browser = browserService(
                agents, policy, approvals, control, tasks, authority, audit, adapter, true, true);
        var response = browser.execute(new BrowserExecuteRequest(
                browserWorkflow(taskId, "frontend-engineer", OperationMode.BALANCED), Map.of(), Map.of()));

        assertThat(response.status()).isEqualTo(BrowserExecutionStatus.SUCCEEDED);
        var order = inOrder(authority, adapter);
        order.verify(authority).requireRunningSpecialist(taskId, "frontend-engineer", "browser", OperationMode.BALANCED);
        order.verify(adapter).execute(anyString(), anyString(), anyList(), anyMap(), anyMap(), anySet());
    }

    @Test
    void browserAuthorityMismatchBlocksBeforeWebDriver() {
        UUID taskId = UUID.randomUUID();
        UUID auditId = UUID.randomUUID();
        TaskEntity task = mock(TaskEntity.class);
        AgentCatalogService agents = mock(AgentCatalogService.class);
        OwnerRuleCompilerService policy = mock(OwnerRuleCompilerService.class);
        ApprovalService approvals = mock(ApprovalService.class);
        TaskControlService control = mock(TaskControlService.class);
        TaskService tasks = mock(TaskService.class);
        DirectExecutionAuthorityService authority = mock(DirectExecutionAuthorityService.class);
        InvocationAuditService audit = mock(InvocationAuditService.class);
        InvocationAuditEntity auditEntry = mock(InvocationAuditEntity.class);
        W3cWebDriverBrowserAdapter adapter = mock(W3cWebDriverBrowserAdapter.class);

        when(tasks.getRequired(taskId)).thenReturn(task);
        when(agents.getRequired("frontend-engineer")).thenReturn(agent("frontend-engineer", List.of("browser")));
        when(policy.evaluate(any())).thenReturn(new CompiledPolicyDecision(true, false, false, false, List.of(), List.of()));
        when(authority.requireRunningSpecialist(taskId, "frontend-engineer", "browser", OperationMode.BALANCED))
                .thenThrow(new IllegalStateException("Direct execution mode mismatch: task=PRIVATE, request=BALANCED"));
        when(audit.start(eq(taskId), eq("frontend-engineer"), any(), eq(BrowserOperatorService.RUNTIME_ID), anyMap())).thenReturn(auditEntry);
        when(auditEntry.getId()).thenReturn(auditId);

        BrowserOperatorService browser = browserService(
                agents, policy, approvals, control, tasks, authority, audit, adapter, true, true);
        var response = browser.execute(new BrowserExecuteRequest(
                browserWorkflow(taskId, "frontend-engineer", OperationMode.BALANCED), Map.of(), Map.of()));

        assertThat(response.status()).isEqualTo(BrowserExecutionStatus.BLOCKED);
        assertThat(response.detail()).contains("Direct browser execution authority denied").contains("mode mismatch");
        verifyNoInteractions(adapter);
    }

    private BrowserOperatorService browserService(
            AgentCatalogService agents,
            OwnerRuleCompilerService policy,
            ApprovalService approvals,
            TaskControlService control,
            TaskService tasks,
            DirectExecutionAuthorityService authority,
            InvocationAuditService audit,
            W3cWebDriverBrowserAdapter adapter,
            boolean runtimeEnabled,
            boolean physicalValidated) {
        return new BrowserOperatorService(
                agents, policy, approvals, control, tasks, authority, audit, adapter,
                runtimeEnabled, physicalValidated, "http://127.0.0.1:9515/", "chrome");
    }

    private BrowserPlanRequest browserWorkflow(UUID taskId, String agentId, OperationMode mode) {
        return new BrowserPlanRequest(
                taskId,
                agentId,
                mode,
                false,
                Set.of("example.com"),
                List.of(new BrowserAction(
                        "open", BrowserActionType.NAVIGATE, "https://example.com/", "", "", "",
                        BrowserEffect.OBSERVE, "open", null)));
    }

    private AgentDefinition agent(String id, List<String> tools) {
        return new AgentDefinition(id, id, "test", "test", List.of(), tools,
                RiskLevel.LOW, ModelClass.GENERAL, false);
    }
}
