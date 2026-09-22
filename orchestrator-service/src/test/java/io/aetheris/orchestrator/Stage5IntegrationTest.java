package io.aetheris.orchestrator;

import io.aetheris.orchestrator.github.GitHubAdapterService;
import io.aetheris.orchestrator.github.GitHubProposalPublishService;
import io.aetheris.orchestrator.host.HostRegistrationRequest;
import io.aetheris.orchestrator.host.HostRegistryService;
import io.aetheris.orchestrator.host.HostStatus;
import io.aetheris.orchestrator.mcp.McpRegistryService;
import io.aetheris.orchestrator.mcp.McpServerRegistrationRequest;
import io.aetheris.orchestrator.mcp.McpToolInvocationRequest;
import io.aetheris.orchestrator.mcp.McpToolInvocationService;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.runtime.DurableWorkQueueService;
import io.aetheris.orchestrator.runtime.EnqueueWorkItemRequest;
import io.aetheris.orchestrator.runtime.WorkItemState;
import io.aetheris.orchestrator.scheduler.SchedulerService;
import io.aetheris.orchestrator.scheduler.WorkerHeartbeatRequest;
import io.aetheris.orchestrator.task.CreateTaskRequest;
import io.aetheris.orchestrator.task.TaskControlService;
import io.aetheris.orchestrator.task.TaskService;
import io.aetheris.orchestrator.task.TaskState;
import io.aetheris.orchestrator.task.TaskTransitionRequest;
import io.aetheris.orchestrator.vault.CredentialVault;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class Stage5IntegrationTest {

    @Autowired TaskService tasks;
    @Autowired TaskControlService control;
    @Autowired DurableWorkQueueService queue;
    @Autowired SchedulerService scheduler;
    @Autowired HostRegistryService hosts;
    @Autowired CredentialVault vault;
    @Autowired McpRegistryService mcpRegistry;
    @Autowired McpToolInvocationService mcpTools;
    @Autowired GitHubAdapterService github;
    @Autowired GitHubProposalPublishService publisher;

    @BeforeEach
    void resetControl(){control.release("Stage 5 test setup");}

    @Test
    void credentialVaultExposesMetadataWithoutSecretValue(){
        var descriptor=vault.describe("definitely-missing-stage5-secret");
        assertThat(descriptor.alias()).isEqualTo("definitely-missing-stage5-secret");
        assertThat(descriptor.available()).isFalse();
        assertThat(descriptor.toString()).doesNotContain("Bearer");
    }

    @Test
    void durableQueueRetriesAndOwnerPauseResumePropagate(){
        var task=runningTask("Stage5 durable queue");
        var item=queue.enqueue(new EnqueueWorkItemRequest(task.getId(),"engineering","{}",2));
        String workerId="stage5-backend-"+UUID.randomUUID();
        scheduler.heartbeat(new WorkerHeartbeatRequest(workerId,"backend-engineer",1,0));
        assertThat(queue.claim(item.getId(),workerId,60).getState()).isEqualTo(WorkItemState.RUNNING);
        assertThat(queue.fail(item.getId(),"transient").getState()).isEqualTo(WorkItemState.RETRY_WAIT);

        assertThat(control.pauseTask(task.getId(),"inspect").getState()).isEqualTo(TaskState.PAUSED);
        assertThat(queue.getRequired(item.getId()).getState()).isEqualTo(WorkItemState.PAUSED);
        assertThat(control.resumeTask(task.getId(),"continue").getState()).isEqualTo(TaskState.RUNNING);
        assertThat(queue.getRequired(item.getId()).getState()).isEqualTo(WorkItemState.QUEUED);
    }

    @Test
    void futureHostRegistersUnpairedAndCannotExecute(){
        var host=hosts.register(new HostRegistrationRequest("stage5-host-"+UUID.randomUUID(),"Future Windows PC","WINDOWS",
                Set.of("PROCESS_READ","APP_LAUNCH","PC_TELEMETRY","OLLAMA"),"AA:BB:CC:DD:EE:FF:11:22"));
        assertThat(host.getStatus()).isEqualTo(HostStatus.UNPAIRED);
        assertThatThrownBy(()->hosts.requireExecutable(host.getId())).isInstanceOf(IllegalStateException.class).hasMessageContaining("not paired");
    }

    @Test
    void mcpToolInvocationFailsClosedWithoutAgentGrant(){
        var server=mcpRegistry.register(new McpServerRegistrationRequest("stage5-mcp-"+UUID.randomUUID(),"Stage5 MCP","http://127.0.0.1:65534/mcp",true,true,
                Set.of("tools.call"),Set.of("public")));
        mcpRegistry.updateHealth(server.getId(),new io.aetheris.orchestrator.mcp.McpHealthUpdateRequest(true,"test healthy"));
        var result=mcpTools.invoke(new McpToolInvocationRequest(server.getId(),null,"backend-engineer","tools.call","public","read_file",Map.of("path","README.md")));
        assertThat(result.success()).isFalse();
        assertThat(result.detail()).contains("no active MCP capability grant");
    }

    @Test
    void githubPublishIsBlockedUntilExactProposalApproval(){
        var task=runningTask("Stage5 GitHub approval");
        var proposal=github.propose(task.getId(),"backend-engineer","teldigi5-wq/aetheris-platform","README.md","main","proposed","Stage5 test proposal");
        var result=publisher.publish(proposal.getId());
        assertThat(result.published()).isFalse();
        assertThat(result.detail()).contains("Explicit owner approval");
    }

    private io.aetheris.orchestrator.task.TaskEntity runningTask(String title){
        var task=tasks.create(new CreateTaskRequest(title,"stage5 test",OperationMode.BALANCED));
        tasks.transition(task.getId(),new TaskTransitionRequest(TaskState.PLANNING,"executive-planner","Planning"));
        return tasks.transition(task.getId(),new TaskTransitionRequest(TaskState.RUNNING,"backend-engineer","Running"));
    }
}
