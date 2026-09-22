package io.aetheris.orchestrator.task;

import io.aetheris.orchestrator.agent.AgentCatalogService;
import io.aetheris.orchestrator.agent.AgentDefinition;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
public class TaskVerificationService {

    private static final String DECISION_KEY = "verificationDecision";
    private static final String EVIDENCE_TYPE_KEY = "verificationEvidenceType";
    private static final String INDEPENDENT_KEY = "independent";
    private static final String EXECUTOR_KEY = "executionAgentId";
    private static final String REVIEWER_KEY = "reviewAgentId";

    private static final Set<String> VERIFIER_CAPABILITIES = Set.of(
            "code-review",
            "integration-testing",
            "release-gates",
            "architecture-review",
            "secure-code-review",
            "source-verification",
            "remediation-verification",
            "evaluation");

    private final TaskRepository tasks;
    private final TaskEventStreamService events;
    private final AgentCatalogService agents;

    public TaskVerificationService(TaskRepository tasks, TaskEventStreamService events, AgentCatalogService agents) {
        this.tasks = tasks;
        this.events = events;
        this.agents = agents;
    }

    @Transactional
    public TaskEvent recordDecision(UUID taskId, String verifierId, boolean passed, String evidenceType, String summary) {
        TaskEntity task = task(taskId);
        VerificationContext context = context(task);
        AgentDefinition verifier = validateVerifier(context, verifierId);
        String normalizedEvidenceType = normalizeEvidenceType(evidenceType);
        String normalizedSummary = required(summary, "summary");

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(DECISION_KEY, passed ? "PASS" : "FAIL");
        metadata.put(EVIDENCE_TYPE_KEY, normalizedEvidenceType);
        metadata.put(INDEPENDENT_KEY, true);
        metadata.put(EXECUTOR_KEY, context.executorId());
        metadata.put(REVIEWER_KEY, context.reviewerId());

        TaskEvent event = new TaskEvent(
                taskId,
                Instant.now(),
                TaskState.VERIFYING,
                verifier.id(),
                normalizedSummary,
                Map.copyOf(metadata));
        events.publish(event);
        return event;
    }

    public void assertCompletionAllowed(UUID taskId, String verifierId) {
        TaskEntity task = task(taskId);
        VerificationContext context = context(task);
        AgentDefinition verifier = validateVerifier(context, verifierId);

        for (int index = context.history().size() - 1; index > context.reviewHandoffIndex(); index--) {
            TaskEvent event = context.history().get(index);
            if (!verifier.id().equals(event.agentId())) continue;
            if (!event.metadata().containsKey(DECISION_KEY)) continue;

            boolean passing = "PASS".equals(String.valueOf(event.metadata().get(DECISION_KEY)));
            boolean independent = Boolean.TRUE.equals(event.metadata().get(INDEPENDENT_KEY));
            boolean sameExecutor = context.executorId().equals(event.metadata().get(EXECUTOR_KEY));
            boolean sameReviewer = context.reviewerId().equals(event.metadata().get(REVIEWER_KEY));
            if (!passing || !independent || !sameExecutor || !sameReviewer) {
                throw new IllegalStateException("Task completion requires a passing independent verification decision for the current execution and review handoff");
            }
            return;
        }

        throw new IllegalStateException("Task completion requires a passing independent verification decision from the completing verifier");
    }

    private VerificationContext context(TaskEntity task) {
        if (task.getState() != TaskState.VERIFYING) {
            throw new IllegalStateException("Independent verification decisions are only valid while a task is VERIFYING");
        }

        List<TaskEvent> history = events.history(task.getId());
        String executorId = null;
        String reviewerId = null;
        int reviewHandoffIndex = -1;

        for (int index = 0; index < history.size(); index++) {
            TaskEvent event = history.get(index);
            if (event.state() == TaskState.RUNNING && isExecutionSpecialist(event.agentId())) {
                executorId = event.agentId();
            }
            if (reviewHandoffIndex < 0
                    && event.state() == TaskState.VERIFYING
                    && "RUNNING".equals(String.valueOf(event.metadata().get("previousState")))) {
                reviewerId = required(event.agentId(), "reviewAgentId");
                reviewHandoffIndex = index;
            }
        }

        if (executorId == null) {
            throw new IllegalStateException("Task completion requires a recorded specialist execution agent");
        }
        if (reviewerId == null) {
            throw new IllegalStateException("Task completion requires a recorded QA/review handoff agent");
        }

        agents.getRequired(reviewerId);
        if (executorId.equals(reviewerId)) {
            throw new IllegalStateException("Execution and QA/review handoff must be performed by different specialists");
        }

        return new VerificationContext(List.copyOf(history), executorId, reviewerId, reviewHandoffIndex);
    }

    private AgentDefinition validateVerifier(VerificationContext context, String verifierId) {
        String normalizedVerifierId = required(verifierId, "verifierId");
        AgentDefinition verifier = agents.getRequired(normalizedVerifierId);

        if (normalizedVerifierId.equals(context.executorId())) {
            throw new IllegalStateException("The execution specialist cannot verify its own work");
        }
        if (normalizedVerifierId.equals(context.reviewerId())) {
            throw new IllegalStateException("The QA/review handoff specialist cannot self-promote to final verifier");
        }
        if (verifier.capabilities().stream().noneMatch(VERIFIER_CAPABILITIES::contains)) {
            throw new IllegalStateException("Agent " + normalizedVerifierId + " does not have an independent verification capability");
        }
        return verifier;
    }

    private boolean isExecutionSpecialist(String agentId) {
        if (agentId == null || agentId.isBlank()) return false;
        try {
            AgentDefinition definition = agents.getRequired(agentId.trim());
            return !definition.division().equalsIgnoreCase("Executive Office");
        } catch (NoSuchElementException exception) {
            return false;
        }
    }

    private TaskEntity task(UUID taskId) {
        return tasks.findById(taskId)
                .orElseThrow(() -> new NoSuchElementException("Unknown task: " + taskId));
    }

    private String normalizeEvidenceType(String value) {
        String normalized = required(value, "evidenceType").toUpperCase(Locale.ROOT).replace('-', '_');
        if (!normalized.matches("[A-Z0-9][A-Z0-9_]{2,79}")) {
            throw new IllegalArgumentException("evidenceType must be a 3-80 character uppercase-safe token");
        }
        return normalized;
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private record VerificationContext(
            List<TaskEvent> history,
            String executorId,
            String reviewerId,
            int reviewHandoffIndex) {
    }
}
