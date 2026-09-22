package io.aetheris.orchestrator;

import io.aetheris.orchestrator.execution.CommandSandboxService;
import io.aetheris.orchestrator.execution.InvocationAuditService;
import io.aetheris.orchestrator.execution.ToolExecutionRequest;
import io.aetheris.orchestrator.execution.ToolExecutionResponse;
import io.aetheris.orchestrator.execution.ToolExecutionService;
import io.aetheris.orchestrator.execution.ToolExecutionStatus;
import io.aetheris.orchestrator.execution.WorkspaceSandboxService;
import io.aetheris.orchestrator.mcp.McpClientConnectionService;
import io.aetheris.orchestrator.mcp.McpDiscoveryResult;
import io.aetheris.orchestrator.mcp.McpRegistryService;
import io.aetheris.orchestrator.mcp.McpServerEntity;
import io.aetheris.orchestrator.mcp.McpServerRegistrationRequest;
import io.aetheris.orchestrator.model.ModelProviderRegistry;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.task.CreateTaskRequest;
import io.aetheris.orchestrator.task.TaskControlService;
import io.aetheris.orchestrator.task.TaskEntity;
import io.aetheris.orchestrator.task.TaskService;
import io.aetheris.orchestrator.task.TaskState;
import io.aetheris.orchestrator.task.TaskTransitionRequest;
import io.aetheris.orchestrator.workflow.EngineeringWorkflowEntity;
import io.aetheris.orchestrator.workflow.EngineeringWorkflowPhase;
import io.aetheris.orchestrator.workflow.EngineeringWorkflowRequest;
import io.aetheris.orchestrator.workflow.EngineeringWorkflowService;
import io.aetheris.orchestrator.workflow.WorkflowExecutionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class Stage4IntegrationTest {

    @Autowired WorkspaceSandboxService workspace;
    @Autowired CommandSandboxService commands;
    @Autowired ToolExecutionService toolExecution;
    @Autowired InvocationAuditService audit;
    @Autowired TaskService tasks;
    @Autowired TaskControlService control;
    @Autowired ModelProviderRegistry providers;
    @Autowired McpRegistryService mcpRegistry;
    @Autowired McpClientConnectionService mcpConnections;
    @Autowired EngineeringWorkflowService workflows;

    @BeforeEach
    void prepareWorkspace() throws Exception {
        control.release("Stage 4 test setup");
        Files.createDirectories(workspace.root());
        Files.writeString(workspace.root().resolve("stage4.txt"), "Stage 4 sandbox evidence\n");
    }

    @Test
    void workspaceSandboxRejectsTraversalAndReadsAllowlistedRoot() {
        assertThat(workspace.read("stage4.txt").content()).contains("sandbox evidence");
        assertThatThrownBy(() -> workspace.read("../outside.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes");
    }

    @Test
    void commandSandboxRejectsNonAllowlistedShell() {
        assertThatThrownBy(() -> commands.execute(List.of("sh", "-c", "echo unsafe"), "", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowlisted");
    }

    @Test
    void toolExecutionIsAuditedAndEmergencyStopBlocksNewInvocations() {
        TaskEntity task = tasks.create(new CreateTaskRequest("Stage4 tool test", "Read sandbox evidence", OperationMode.BALANCED));
        tasks.transition(task.getId(), new TaskTransitionRequest(TaskState.PLANNING, "executive-planner", "Planning"));
        tasks.transition(task.getId(), new TaskTransitionRequest(TaskState.RUNNING, "backend-engineer", "Running"));

        ToolExecutionResponse read = toolExecution.execute(new ToolExecutionRequest(
                task.getId(), "backend-engineer", "files.read-workspace", OperationMode.BALANCED,
                Map.of("path", "stage4.txt")));
        assertThat(read.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
        assertThat(audit.forTask(task.getId())).isNotEmpty();

        control.engage("Stage4 emergency-stop test");
        ToolExecutionResponse blocked = toolExecution.execute(new ToolExecutionRequest(
                null, "backend-engineer", "files.read-workspace", OperationMode.BALANCED,
                Map.of("path", "stage4.txt")));
        assertThat(blocked.status()).isEqualTo(ToolExecutionStatus.CANCELLED);
        control.release("Stage4 test complete");
    }

    @Test
    void providerSpiExposesConfiguredLocalProviderWithoutInventingHealth() {
        assertThat(providers.snapshots()).hasSize(1);
        assertThat(providers.snapshots().getFirst().providerId()).isEqualTo("ollama");
        assertThat(providers.snapshots().getFirst().available()).isFalse();
    }

    @Test
    void mcpDiscoveryRejectsUnsafeRemoteHttpEndpointBeforeNetworkCall() {
        McpServerEntity server = mcpRegistry.register(new McpServerRegistrationRequest(
                "unsafe-remote-stage4",
                "Unsafe Remote Stage4",
                "http://example.com/mcp",
                false,
                true,
                Set.of("tools.list"),
                Set.of("public")));

        McpDiscoveryResult result = mcpConnections.discover(server.getId());
        assertThat(result.healthy()).isFalse();
        assertThat(result.detail()).contains("HTTPS");
    }

    @Test
    void engineeringWorkflowCanExecuteRealSandboxAndQaEvidence() {
        EngineeringWorkflowEntity workflow = workflows.start(new EngineeringWorkflowRequest(
                "Stage4 adapter workflow",
                "Read a sandbox file, run a safe QA command, and verify evidence",
                OperationMode.BALANCED));
        assertThat(workflow.getPhase()).isEqualTo(EngineeringWorkflowPhase.ENGINEERING);

        var first = workflows.executeNext(workflow.getId(), new WorkflowExecutionRequest(
                "stage4.txt", List.of("java", "-version"), "", List.of()));
        assertThat(first.workflow().getPhase()).isEqualTo(EngineeringWorkflowPhase.QA);

        var second = workflows.executeNext(workflow.getId(), new WorkflowExecutionRequest(
                "stage4.txt", List.of("java", "-version"), "", List.of()));
        assertThat(second.workflow().getPhase()).isEqualTo(EngineeringWorkflowPhase.VERIFICATION);

        var third = workflows.executeNext(workflow.getId(), new WorkflowExecutionRequest(
                "stage4.txt", List.of("java", "-version"), "", List.of("source-readable", "qa-command-passed")));
        assertThat(third.workflow().getPhase()).isEqualTo(EngineeringWorkflowPhase.COMPLETED);
        assertThat(tasks.getRequired(workflow.getTaskId()).getState()).isEqualTo(TaskState.COMPLETED);
        assertThat(third.evidence()).hasSizeGreaterThanOrEqualTo(3);
    }
}
