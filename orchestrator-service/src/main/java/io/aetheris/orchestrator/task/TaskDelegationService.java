package io.aetheris.orchestrator.task;

import io.aetheris.orchestrator.agent.AgentCatalogService;
import io.aetheris.orchestrator.agent.AgentDefinition;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class TaskDelegationService {

    private static final String DELEGATION_CAPABILITY = "delegation";
    private static final String DECISION_KEY = "delegationDecision";
    private static final String STAGE_KEY = "delegationStage";
    private static final String DELEGATOR_KEY = "delegatorId";
    private static final String DELEGATE_KEY = "delegateId";
    private static final String AUTHORITY_TRANSFERRED_KEY = "authorityTransferred";
    private static final String VERIFICATION_REQUIRED_KEY = "verificationRequired";

    private final AgentCatalogService agents;
    private final TaskEventStreamService events;

    public TaskDelegationService(AgentCatalogService agents, TaskEventStreamService events) {
        this.agents = agents;
        this.events = events;
    }

    public void assertPlannedAssignmentAllowed(String delegatorId, String delegateId) {
        validate(delegatorId, delegateId);
    }

    @Transactional
    public TaskEvent recordPlannedAssignment(TaskEntity task, String delegatorId, String delegateId) {
        if (task == null) throw new IllegalArgumentException("task is required");
        if (task.getState() != TaskState.QUEUED && task.getState() != TaskState.PLANNING) {
            throw new IllegalStateException("Planned delegation can only be recorded while a task is QUEUED or PLANNING");
        }
        DelegationContext context = validate(delegatorId, delegateId);
        return publish(task, context, "PLANNED", "Governed specialist delegation planned");
    }

    @Transactional
    public TaskEvent recordExecutionAssignment(TaskEntity task, String delegatorId, String delegateId) {
        if (task == null) throw new IllegalArgumentException("task is required");
        if (task.getState() != TaskState.PLANNING
                && task.getState() != TaskState.AWAITING_APPROVAL
                && task.getState() != TaskState.RUNNING) {
            throw new IllegalStateException("Execution delegation can only activate from PLANNING, AWAITING_APPROVAL or an owner-approved RUNNING handoff");
        }

        String normalizedDelegatorId = required(delegatorId, "delegatorId");
        if (!normalizedDelegatorId.equals(task.getActiveAgentId())) {
            throw new IllegalStateException("Execution delegation must be issued by the task's current active agent");
        }

        DelegationContext context = validate(normalizedDelegatorId, delegateId);
        return publish(task, context, "ACTIVATED", "Governed specialist execution assignment activated");
    }

    private TaskEvent publish(TaskEntity task, DelegationContext context, String stage, String message) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(DECISION_KEY, "ALLOW");
        metadata.put(STAGE_KEY, stage);
        metadata.put(DELEGATOR_KEY, context.delegator().id());
        metadata.put(DELEGATE_KEY, context.delegate().id());
        metadata.put("delegateDivision", context.delegate().division());
        metadata.put("delegateRiskLevel", context.delegate().riskLevel().name());
        metadata.put("taskMode", task.getMode().name());
        metadata.put(AUTHORITY_TRANSFERRED_KEY, false);
        metadata.put(VERIFICATION_REQUIRED_KEY, true);
        metadata.put("delegateAllowedTools", String.join(",", context.delegate().allowedTools()));
        metadata.put("delegateCapabilities", String.join(",", context.delegate().capabilities()));

        TaskEvent event = new TaskEvent(
                task.getId(),
                Instant.now(),
                task.getState(),
                context.delegator().id(),
                message + ": " + context.delegator().id() + " -> " + context.delegate().id(),
                Map.copyOf(metadata));
        events.publish(event);
        return event;
    }

    private DelegationContext validate(String delegatorId, String delegateId) {
        String normalizedDelegatorId = required(delegatorId, "delegatorId");
        String normalizedDelegateId = required(delegateId, "delegateId");
        if (normalizedDelegatorId.equals(normalizedDelegateId)) {
            throw new IllegalStateException("A specialist cannot delegate work to itself");
        }

        AgentDefinition delegator = agents.getRequired(normalizedDelegatorId);
        AgentDefinition delegate = agents.getRequired(normalizedDelegateId);
        if (!delegator.capabilities().contains(DELEGATION_CAPABILITY)) {
            throw new IllegalStateException("Agent " + normalizedDelegatorId + " does not have delegation capability");
        }
        return new DelegationContext(delegator, delegate);
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private record DelegationContext(AgentDefinition delegator, AgentDefinition delegate) {
    }
}
