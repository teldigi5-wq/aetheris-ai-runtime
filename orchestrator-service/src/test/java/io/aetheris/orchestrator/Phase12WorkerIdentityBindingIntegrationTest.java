package io.aetheris.orchestrator;

import io.aetheris.orchestrator.mission.CreateMissionRequest;
import io.aetheris.orchestrator.mission.MissionService;
import io.aetheris.orchestrator.mission.MissionSessionView;
import io.aetheris.orchestrator.planner.MissionPlanNodeEntity;
import io.aetheris.orchestrator.planner.MissionPlannerService;
import io.aetheris.orchestrator.planner.PlanMissionRequest;
import io.aetheris.orchestrator.planner.PlanStepRequest;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.runtime.DurableWorkQueueService;
import io.aetheris.orchestrator.runtime.EnqueueWorkItemRequest;
import io.aetheris.orchestrator.runtime.WorkItemEntity;
import io.aetheris.orchestrator.runtime.WorkItemState;
import io.aetheris.orchestrator.scheduler.ScheduleWorkRequest;
import io.aetheris.orchestrator.scheduler.SchedulerDispatchResult;
import io.aetheris.orchestrator.scheduler.SchedulerService;
import io.aetheris.orchestrator.scheduler.WorkerHeartbeatEntity;
import io.aetheris.orchestrator.scheduler.WorkerHeartbeatRequest;
import io.aetheris.orchestrator.stage9.CapabilityAwareDispatchService;
import io.aetheris.orchestrator.stage9.CapabilityDispatchResult;
import io.aetheris.orchestrator.stage9.WorkRequirementRequest;
import io.aetheris.orchestrator.stage9.WorkerCapabilityRequest;
import io.aetheris.orchestrator.task.CreateTaskRequest;
import io.aetheris.orchestrator.task.TaskEntity;
import io.aetheris.orchestrator.task.TaskService;
import io.aetheris.orchestrator.task.TaskState;
import io.aetheris.orchestrator.task.TaskTransitionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class Phase12WorkerIdentityBindingIntegrationTest {

    @Autowired TaskService tasks;
    @Autowired DurableWorkQueueService queue;
    @Autowired SchedulerService scheduler;
    @Autowired MissionService missions;
    @Autowired MissionPlannerService planner;
    @Autowired CapabilityAwareDispatchService capabilityDispatch;

    @Test
    void workerHeartbeatRejectsInventedSpecialistIdentity() {
        assertThatThrownBy(() -> scheduler.heartbeat(new WorkerHeartbeatRequest(
                "phase12-unknown-" + UUID.randomUUID(),
                "invented-specialist",
                1,
                0)))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Unknown agent");
    }

    @Test
    void workerBindingIsImmutableAcrossHeartbeats() {
        String workerId = "phase12-bound-" + UUID.randomUUID();
        WorkerHeartbeatEntity first = scheduler.heartbeat(new WorkerHeartbeatRequest(
                workerId,
                "backend-engineer",
                2,
                0));
        assertThat(first.getAgentId()).isEqualTo("backend-engineer");

        assertThatThrownBy(() -> scheduler.heartbeat(new WorkerHeartbeatRequest(
                workerId,
                "security-architect",
                2,
                0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already bound");
    }

    @Test
    void directQueueClaimCannotCrossSpecialistIdentity() {
        TaskEntity task = delegatedRunningTask("Direct worker identity", "backend-engineer");
        WorkItemEntity item = queue.enqueue(new EnqueueWorkItemRequest(
                task.getId(),
                "PHASE12_DIRECT_IDENTITY",
                "{}",
                2));
        assertThat(item.getRequiredAgentId()).isEqualTo("backend-engineer");

        String researchWorker = boundWorker("research-scientist");
        String backendWorker = boundWorker("backend-engineer");

        assertThatThrownBy(() -> queue.claim(item.getId(), researchWorker, 30))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not bound to required specialist backend-engineer");
        assertThat(queue.getRequired(item.getId()).getState()).isEqualTo(WorkItemState.QUEUED);

        WorkItemEntity claimed = queue.claim(item.getId(), backendWorker, 30);
        assertThat(claimed.getLeaseOwner()).isEqualTo(backendWorker);
        assertThat(claimed.getState()).isEqualTo(WorkItemState.RUNNING);
    }

    @Test
    void missionWorkUsesDelegateIdentityInsteadOfPlannerIdentity() {
        MissionSessionView mission = missions.create(new CreateMissionRequest(
                "Worker-bound mission",
                "Bind released mission execution to delegated specialist"));
        List<MissionPlanNodeEntity> nodes = planner.plan(mission.id(), new PlanMissionRequest(
                OperationMode.PRIVATE,
                List.of(new PlanStepRequest(
                        "research",
                        "Research evidence",
                        "Collect evidence without authority transfer",
                        "research-scientist",
                        10000,
                        Set.of()))));

        MissionPlanNodeEntity node = nodes.getFirst();
        planner.releaseReady(mission.id());
        TaskEntity task = tasks.getRequired(node.getTaskId());
        assertThat(task.getActiveAgentId()).isEqualTo("executive-planner");

        WorkItemEntity item = queue.forTask(task.getId()).getFirst();
        assertThat(item.getRequiredAgentId()).isEqualTo("research-scientist");

        String backendWorker = boundWorker("backend-engineer");
        SchedulerDispatchResult wrong = scheduler.dispatch(backendWorker, 30);
        assertThat(wrong.dispatched()).isFalse();
        assertThat(wrong.reason()).contains("specialist identity");

        String researchWorker = boundWorker("research-scientist");
        SchedulerDispatchResult correct = scheduler.dispatch(researchWorker, 30);
        assertThat(correct.dispatched()).isTrue();
        assertThat(correct.workItem().getId()).isEqualTo(item.getId());
        assertThat(correct.workItem().getRequiredAgentId()).isEqualTo("research-scientist");
    }

    @Test
    void capabilityClaimsCannotOverrideSpecialistIdentityBinding() {
        TaskEntity task = delegatedRunningTask("Capability worker identity", "backend-engineer");
        WorkItemEntity item = queue.enqueue(new EnqueueWorkItemRequest(
                task.getId(),
                "PHASE12_CAPABILITY_IDENTITY",
                "{}",
                2));
        scheduler.schedule(new ScheduleWorkRequest(item.getId(), 20000, "phase12-identity"));

        String researchWorker = boundWorker("research-scientist");
        String backendWorker = boundWorker("backend-engineer");
        WorkerCapabilityRequest coding = new WorkerCapabilityRequest(Set.of("coding"), Set.of("phase12-identity"));
        capabilityDispatch.setWorker(researchWorker, coding);
        capabilityDispatch.setWorker(backendWorker, coding);
        capabilityDispatch.setRequirement(item.getId(), new WorkRequirementRequest(Set.of("coding"), "phase12-identity"));

        CapabilityDispatchResult result = capabilityDispatch.dispatch(30);
        assertThat(result.dispatched()).isTrue();
        assertThat(result.workerId()).isEqualTo(backendWorker);
        assertThat(result.workItem().getRequiredAgentId()).isEqualTo("backend-engineer");
    }

    @Test
    void genericUnassignedWorkRemainsCompatibleWithLegacyWorkers() {
        TaskEntity task = tasks.create(new CreateTaskRequest(
                "Generic infrastructure work",
                "Preserve compatibility for work without specialist assignment",
                OperationMode.BALANCED));
        WorkItemEntity item = queue.enqueue(new EnqueueWorkItemRequest(
                task.getId(),
                "PHASE12_GENERIC",
                "{}",
                2));
        assertThat(item.getRequiredAgentId()).isNull();

        String workerId = "phase12-generic-" + UUID.randomUUID();
        scheduler.heartbeat(new WorkerHeartbeatRequest(workerId, 1, 0));
        WorkItemEntity claimed = queue.claim(item.getId(), workerId, 30);
        assertThat(claimed.getLeaseOwner()).isEqualTo(workerId);
    }

    private TaskEntity delegatedRunningTask(String title, String delegateId) {
        TaskEntity task = tasks.create(new CreateTaskRequest(title, "Specialist-bound execution", OperationMode.BALANCED));
        tasks.transition(task.getId(), new TaskTransitionRequest(
                TaskState.PLANNING,
                "executive-planner",
                "Prepare governed specialist assignment"));
        return tasks.transition(task.getId(), new TaskTransitionRequest(
                TaskState.RUNNING,
                delegateId,
                "Activate governed specialist assignment"));
    }

    private String boundWorker(String agentId) {
        String workerId = "phase12-" + agentId + "-" + UUID.randomUUID();
        scheduler.heartbeat(new WorkerHeartbeatRequest(workerId, agentId, 1, 0));
        return workerId;
    }
}
