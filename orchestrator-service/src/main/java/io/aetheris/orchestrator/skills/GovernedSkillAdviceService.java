package io.aetheris.orchestrator.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aetheris.orchestrator.agent.AgentCatalogService;
import io.aetheris.orchestrator.agent.AgentDefinition;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.task.TaskEntity;
import io.aetheris.orchestrator.task.TaskService;
import io.aetheris.orchestrator.task.TaskState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * E3.9G-3: internal-only task-bound skill REFERENCE service.
 * Not a controller, principal authenticator, model executor or tool bridge.
 * A persistent task's active specialist is an assignment, NOT owner identity.
 * Returning a skill reference NEVER approves an action or calls an LLM.
 */
@Service
public final class GovernedSkillAdviceService {
    private static final String RESOURCE = "/enterprise/skill-advice-bindings-v1.json";
    private static final String EVIDENCE = "E39G3_TASK_BOUND_SKILL_REFERENCE_ONLY_NO_EXECUTION";
    private static final String VENDOR_COMMIT = "2d228630ba1446365f614a108cb8252f1feda202";
    private static final Pattern ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Set<TaskState> SAFE_STATES = Set.of(TaskState.RUNNING, TaskState.VERIFYING);
    private static final Map<String, String> ALLOWED_ROLE_DIVISIONS = Map.of(
            "frontend-engineer", "Software Engineering",
            "qa-engineer", "QA and Release",
            "software-architect", "Software Engineering",
            "appsec-officer", "Cybersecurity",
            "security-architect", "Cybersecurity",
            "devops-engineer", "DevOps and SRE",
            "mcp-integration-engineer", "Tools and Integrations"
    );

    private final TaskService tasks;
    private final AgentCatalogService agents;
    private final boolean enabled;
    private final Map<String, Set<String>> roleSkills;
    private final Map<String, SkillReference> references;

    public GovernedSkillAdviceService(TaskService tasks, AgentCatalogService agents,
            ObjectMapper mapper,
            @Value("${aetheris.governed-skills.reference-enabled:false}") boolean enabled) {
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.agents = Objects.requireNonNull(agents, "agents");
        this.enabled = enabled;
        Policy policy = load(Objects.requireNonNull(mapper, "mapper"));
        this.roleSkills = policy.roles();
        this.references = policy.references();
        assertCanonicalAgentRegistry(agents.listAll());
    }

    private static void require(boolean ok, String reason) {
        if (!ok) throw new IllegalStateException(reason);
    }

    private static Policy load(ObjectMapper mapper) {
        JsonNode root;
        try (InputStream stream = GovernedSkillAdviceService.class.getResourceAsStream(RESOURCE)) {
            require(stream != null, "E39G3_POLICY_RESOURCE_MISSING");
            root = mapper.readTree(stream);
        } catch (IOException err) {
            throw new IllegalStateException("E39G3_POLICY_UNREADABLE", err);
        }
        require(root != null && root.isObject() && root.size() == 6
                && root.path("schemaVersion").asInt(-1) == 1
                && EVIDENCE.equals(root.path("evidenceClass").asText())
                && root.path("enabledByDefault").isBoolean()
                && !root.path("enabledByDefault").asBoolean()
                && VENDOR_COMMIT.equals(root.path("originalSourceCommit").asText())
                && "MIT".equals(root.path("upstreamLicense").asText()),
                "E39G3_POLICY_CLASS_OR_METADATA_INVALID");

        JsonNode definitions = root.path("skills");
        require(definitions.isArray() && definitions.size() == 14,
                "E39G3_POLICY_SKILL_COUNT_INVALID");
        Map<String, SkillReference> refs = new LinkedHashMap<>();
        for (JsonNode node : definitions) {
            String name = node.path("name").asText("");
            String trust = node.path("trust").asText("");
            String summary = node.path("summary").asText("");
            String sourcePath = node.path("sourcePath").asText("");
            require(node.isObject() && node.size() == 4
                    && ID.matcher(name).matches() && name.length() <= 64
                    && Set.of("first-party-advisory", "third-party-pinned-reference").contains(trust)
                    && summary.length() >= 20 && summary.length() <= 180
                    && summary.chars().allMatch(c -> c >= 32 && c != 127)
                    && !sourcePath.contains("..") && !sourcePath.contains("\\")
                    && sourcePath.equals((trust.equals("first-party-advisory")
                        ? "native/" : "third-party/emilkowalski/") + name + "/SKILL.md")
                    && refs.putIfAbsent(name, new SkillReference(name, trust, summary, sourcePath)) == null,
                    "E39G3_POLICY_UNSAFE_SKILL_REFERENCE");
        }
        JsonNode mappings = root.path("roles");
        require(mappings.isObject() && mappings.size() == ALLOWED_ROLE_DIVISIONS.size()
                && ALLOWED_ROLE_DIVISIONS.keySet().containsAll(fieldNames(mappings)),
                "E39G3_POLICY_ROLE_SCOPE_CHANGED");
        Map<String, Set<String>> roles = new LinkedHashMap<>();
        mappings.fields().forEachRemaining(entry -> {
            JsonNode ids = entry.getValue();
            require(ids.isArray() && ids.size() > 0 && ids.size() <= 8,
                    "E39G3_POLICY_INVALID_ROLE_BINDING");
            LinkedHashSet<String> allowed = new LinkedHashSet<>();
            ids.forEach(id -> {
                String value = id.asText("");
                require(id.isTextual() && refs.containsKey(value) && allowed.add(value),
                        "E39G3_POLICY_DUPLICATE_OR_UNKNOWN_SKILL");
            });
            roles.put(entry.getKey(), Collections.unmodifiableSet(allowed));
        });
        return new Policy(Collections.unmodifiableMap(roles),
                Collections.unmodifiableMap(refs));
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static void assertCanonicalAgentRegistry(List<AgentDefinition> roster) {
        require(roster != null && roster.size() == 26 && roster.stream().allMatch(Objects::nonNull),
                "E39G3_AGENT_CATALOG_NOT_26");
        Set<String> ids = new LinkedHashSet<>();
        Set<String> divisions = new LinkedHashSet<>();
        Map<String, AgentDefinition> lookup = new LinkedHashMap<>();
        for (AgentDefinition agent : roster) {
            require(ids.add(agent.id()) && agent.division() != null && !agent.division().isBlank(),
                    "E39G3_INVALID_CANONICAL_IDENTITY");
            divisions.add(agent.division());
            lookup.put(agent.id(), agent);
        }
        require(divisions.size() == 13, "E39G3_CANONICAL_DIVISION_DRIFT");
        ALLOWED_ROLE_DIVISIONS.forEach((id, division) ->
                require(lookup.containsKey(id) && division.equals(lookup.get(id).division()),
                        "E39G3_ROLE_IDENTITY_DRIFT"));
    }

    public ReferenceReceipt select(ReferenceRequest request) {
        require(request != null && enabled && request.explicitReferenceRequest(),
                "E39G3_SKILL_ADVICE_DEFAULT_OFF");
        require(request.taskId() != null && request.claimedMode() != null,
                "E39G3_DURABLE_TASK_AND_MODE_REQUIRED");
        String agentId = request.agentId();
        String skillId = request.skillId();
        String taskSummary = request.taskSummary();
        require(agentId != null && ID.matcher(agentId).matches()
                && skillId != null && ID.matcher(skillId).matches(),
                "E39G3_UNSAFE_ROLE_OR_SKILL_ID");
        require(taskSummary != null && taskSummary.length() >= 10
                && taskSummary.length() <= 240
                && taskSummary.codePoints().allMatch(c -> c >= 32 && c != 127
                    && c != 0x2028 && c != 0x2029),
                "E39G3_TASK_SUMMARY_INVALID");
        Set<String> permitted = roleSkills.get(agentId);
        require(permitted != null && permitted.contains(skillId),
                "E39G3_ROLE_TO_SKILL_DENIED");
        TaskEntity task = tasks.getRequired(request.taskId());
        require(task != null && SAFE_STATES.contains(task.getState()),
                "E39G3_TASK_STATE_DENIED");
        require(agentId.equals(task.getActiveAgentId()),
                "E39G3_NOT_TASK_ACTIVE_SPECIALIST");
        require(request.claimedMode() == task.getMode(),
                "E39G3_OPERATION_MODE_MISMATCH");
        AgentDefinition agent = agents.getRequired(agentId);
        require(agent != null && ALLOWED_ROLE_DIVISIONS.get(agentId).equals(agent.division()),
                "E39G3_ROLE_DIVISION_MISMATCH");
        SkillReference skill = references.get(skillId);
        require(skill != null, "E39G3_UNREGISTERED_SKILL");
        String digest = sha256(EVIDENCE + "\n" + request.taskId() + "\n"
                + agentId + "\n" + skillId + "\n" + request.claimedMode()
                + "\n" + taskSummary + "\n" + skill.summary());
        return new ReferenceReceipt(EVIDENCE, request.taskId(), agentId,
                agent.division(), skill.name(), skill.trust(), skill.sourcePath(),
                skill.summary(), sha256(taskSummary), digest,
                "UNTRUSTED_REFERENCE_ONLY_NOT_INSTRUCTIONS",
                true, false, false, false, false, false, false, 0, 0);
    }

    public Summary status() {
        return new Summary(EVIDENCE, enabled, 26, 13, roleSkills.size(),
                references.size(), false, false, false);
    }

    private static String sha256(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("E39G3_SHA256_NOT_AVAILABLE", impossible);
        }
    }

    private record SkillReference(String name, String trust, String summary, String sourcePath) {}
    private record Policy(Map<String, Set<String>> roles, Map<String, SkillReference> references) {}

    public record ReferenceRequest(UUID taskId, String agentId, String skillId,
            String taskSummary, OperationMode claimedMode, boolean explicitReferenceRequest) {}

    public record ReferenceReceipt(String evidenceClass, UUID taskId, String agentId,
            String division, String skillId, String skillTrust, String sourcePath,
            String vettedSummaryNotInstructions, String taskSha256,
            String evidenceSha256Unsigned, String warning, boolean readOnly,
            boolean modelCalled, boolean toolCalled, boolean ownerAuthenticated,
            boolean approvalGranted, boolean deploymentAuthorized,
            boolean stage45Certified, int modelCallCount, int toolCallCount) {}

    public record Summary(String evidenceClass, boolean localReferenceFlagEnabled,
            int sourceCatalogAgentCount, int sourceDivisionCount, int eligibleAgentCount,
            int registeredReferenceCount, boolean ownerAuthGranted,
            boolean modelRuntimeIntegrated, boolean deploymentGranted) {}
}
