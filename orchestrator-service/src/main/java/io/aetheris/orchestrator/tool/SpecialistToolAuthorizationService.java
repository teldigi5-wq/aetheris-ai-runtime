package io.aetheris.orchestrator.tool;

import io.aetheris.orchestrator.agent.AgentCatalogService;
import io.aetheris.orchestrator.agent.AgentDefinition;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

@Service
public class SpecialistToolAuthorizationService {

    private static final Map<String, String> TOOL_FAMILY_BY_ID = Map.of(
            "github.read", "github",
            "github.propose-change", "github",
            "files.read-workspace", "filesystem",
            "files.write-workspace", "filesystem",
            "terminal.inspect", "terminal",
            "terminal.execute-workspace", "terminal"
    );

    private final AgentCatalogService agents;

    public SpecialistToolAuthorizationService(AgentCatalogService agents) {
        this.agents = agents;
    }

    public Decision evaluate(String agentId, ToolDescriptor tool) {
        AgentDefinition agent = agents.getRequired(agentId);
        String family = toolFamilyFor(tool.id());
        if (family == null) {
            return Decision.deny(null,
                    "No specialist tool-family mapping exists for registered tool " + tool.id());
        }

        boolean allowed = agent.allowedTools().stream()
                .map(this::normalize)
                .anyMatch(family::equals);
        if (!allowed) {
            return Decision.deny(family,
                    "Agent " + agent.id() + " is not allowed to use specialist tool family " + family);
        }

        return Decision.allow(family,
                "Agent " + agent.id() + " is allowed to use specialist tool family " + family);
    }

    public String toolFamilyFor(String toolId) {
        if (toolId == null || toolId.isBlank()) return null;
        return TOOL_FAMILY_BY_ID.get(normalize(toolId));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record Decision(boolean allowed, String toolFamily, String reason) {
        private static Decision allow(String toolFamily, String reason) {
            return new Decision(true, toolFamily, reason);
        }

        private static Decision deny(String toolFamily, String reason) {
            return new Decision(false, toolFamily, reason);
        }
    }
}
