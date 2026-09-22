package io.aetheris.orchestrator.planner;

import io.aetheris.orchestrator.mission.*;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.runtime.*;
import io.aetheris.orchestrator.scheduler.*;
import io.aetheris.orchestrator.task.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MissionPlannerService {
    private static final String PLANNER_AGENT_ID = "executive-planner";

    private final MissionService missions;
    private final MissionPlanNodeRepository nodes;
    private final TaskService tasks;
    private final DurableWorkQueueService queue;
    private final SchedulerService scheduler;
    private final TaskDelegationService delegations;

    public MissionPlannerService(MissionService missions,
                                 MissionPlanNodeRepository nodes,
                                 TaskService tasks,
                                 DurableWorkQueueService queue,
                                 SchedulerService scheduler,
                                 TaskDelegationService delegations) {
        this.missions = missions;
        this.nodes = nodes;
        this.tasks = tasks;
        this.queue = queue;
        this.scheduler = scheduler;
        this.delegations = delegations;
    }

    @Transactional
    public List<MissionPlanNodeEntity> plan(UUID missionId, PlanMissionRequest request) {
        missions.required(missionId);
        if (request.steps().isEmpty()) throw new IllegalArgumentException("Mission plan requires at least one step");
        OperationMode mode = request.mode() == null ? OperationMode.BALANCED : request.mode();

        Map<String, PlanStepRequest> byKey = new LinkedHashMap<>();
        for (PlanStepRequest step : request.steps()) {
            if (step.key() == null || step.key().isBlank()
                    || step.title() == null || step.title().isBlank()
                    || step.command() == null || step.command().isBlank()
                    || step.agentId() == null || step.agentId().isBlank()) {
                throw new IllegalArgumentException("Each mission step requires key, title, command and agentId");
            }
            String key = step.key().trim();
            if (byKey.putIfAbsent(key, step) != null) {
                throw new IllegalArgumentException("Duplicate mission step key: " + key);
            }
            delegations.assertPlannedAssignmentAllowed(PLANNER_AGENT_ID, step.agentId().trim());
        }

        for (Map.Entry<String, PlanStepRequest> entry : byKey.entrySet()) {
            for (String dependency : entry.getValue().dependsOn()) {
                if (!byKey.containsKey(dependency)) {
                    throw new IllegalArgumentException("Unknown dependency '" + dependency + "' for step " + entry.getKey());
                }
                if (dependency.equals(entry.getKey())) {
                    throw new IllegalArgumentException("Mission step cannot depend on itself: " + dependency);
                }
            }
        }
        assertAcyclic(byKey);

        Map<String, TaskEntity> taskByKey = new LinkedHashMap<>();
        for (Map.Entry<String, PlanStepRequest> entry : byKey.entrySet()) {
            PlanStepRequest step = entry.getValue();
            TaskEntity task = tasks.create(new CreateTaskRequest(step.title().trim(), step.command().trim(), mode));
            delegations.recordPlannedAssignment(task, PLANNER_AGENT_ID, step.agentId().trim());
            missions.attach(missionId, task.getId());
            taskByKey.put(entry.getKey(), task);
        }

        List<MissionPlanNodeEntity> created = new ArrayList<>();
        for (Map.Entry<String, PlanStepRequest> entry : byKey.entrySet()) {
            PlanStepRequest step = entry.getValue();
            List<UUID> dependencies = step.dependsOn().stream().map(key -> taskByKey.get(key).getId()).toList();
            created.add(nodes.save(new MissionPlanNodeEntity(
                    UUID.randomUUID(),
                    missionId,
                    entry.getKey(),
                    taskByKey.get(entry.getKey()).getId(),
                    dependencies,
                    step.priority(),
                    step.agentId().trim())));
        }
        return created;
    }

    public List<MissionPlanNodeEntity> nodes(UUID missionId) {
        missions.required(missionId);
        return nodes.findByMissionIdOrderByPriorityDescCreatedAtAsc(missionId);
    }

    @Transactional
    public List<MissionPlanNodeEntity> releaseReady(UUID missionId) {
        missions.required(missionId);
        List<MissionPlanNodeEntity> list = nodes.findByMissionIdOrderByPriorityDescCreatedAtAsc(missionId);
        for (MissionPlanNodeEntity node : list) {
            TaskState state = tasks.getRequired(node.getTaskId()).getState();
            if (state == TaskState.COMPLETED) {
                node.completed();
                nodes.save(node);
                continue;
            }
            if (state == TaskState.FAILED || state == TaskState.CANCELLED) {
                node.failed();
                nodes.save(node);
                continue;
            }
            if (node.getState() == MissionPlanNodeState.BLOCKED) {
                boolean ready = node.getDependencyTaskIds().stream()
                        .map(tasks::getRequired)
                        .allMatch(task -> task.getState() == TaskState.COMPLETED);
                if (ready) {
                    node.ready();
                    nodes.save(node);
                }
            }
            if (node.getState() == MissionPlanNodeState.READY) {
                TaskEntity task = tasks.getRequired(node.getTaskId());
                if (task.getState() == TaskState.QUEUED) {
                    tasks.transition(task.getId(), new TaskTransitionRequest(
                            TaskState.PLANNING,
                            PLANNER_AGENT_ID,
                            "Mission dependency graph released step " + node.getStepKey()
                                    + " for governed delegation to " + node.getAgentId()));
                }
                String payload = "{\"stepKey\":\"" + escape(node.getStepKey())
                        + "\",\"delegatorId\":\"" + PLANNER_AGENT_ID
                        + "\",\"delegateId\":\"" + escape(node.getAgentId())
                        + "\",\"authorityTransferred\":false}";
                WorkItemEntity item = queue.enqueueForSpecialist(new EnqueueWorkItemRequest(
                        node.getTaskId(),
                        "MISSION_STEP",
                        payload,
                        3), node.getAgentId());
                scheduler.schedule(new ScheduleWorkRequest(item.getId(), node.getPriority(), "mission"));
                node.enqueued();
                nodes.save(node);
            }
        }
        return nodes.findByMissionIdOrderByPriorityDescCreatedAtAsc(missionId);
    }

    private void assertAcyclic(Map<String, PlanStepRequest> steps) {
        Set<String> visiting = new HashSet<>();
        Set<String> done = new HashSet<>();
        for (String key : steps.keySet()) visit(key, steps, visiting, done);
    }

    private void visit(String key,
                       Map<String, PlanStepRequest> steps,
                       Set<String> visiting,
                       Set<String> done) {
        if (done.contains(key)) return;
        if (!visiting.add(key)) throw new IllegalArgumentException("Mission plan contains a dependency cycle at " + key);
        for (String dependency : steps.get(key).dependsOn()) visit(dependency, steps, visiting, done);
        visiting.remove(key);
        done.add(key);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
