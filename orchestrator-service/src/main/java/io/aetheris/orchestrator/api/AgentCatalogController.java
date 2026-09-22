package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.agent.AgentCatalogService;
import io.aetheris.orchestrator.agent.AgentDefinition;
import io.aetheris.orchestrator.agent.RiskLevel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orchestrator")
public class AgentCatalogController {

    private final AgentCatalogService catalogService;

    public AgentCatalogController(AgentCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/agents")
    public List<AgentDefinition> listAgents() {
        return catalogService.listAll();
    }

    @GetMapping("/agents/{id}")
    public AgentDefinition getAgent(@PathVariable String id) {
        return catalogService.getRequired(id);
    }

    @GetMapping("/divisions/{division}/agents")
    public List<AgentDefinition> listDivisionAgents(@PathVariable String division) {
        return catalogService.byDivision(division);
    }

    @GetMapping("/overview")
    public OrchestratorOverview overview() {
        return new OrchestratorOverview(
                catalogService.listAll().size(),
                catalogService.countByDivision(),
                catalogService.countByRisk());
    }

    public record OrchestratorOverview(
            int totalAgents,
            Map<String, Long> agentsByDivision,
            Map<RiskLevel, Long> agentsByRisk) {
    }
}
