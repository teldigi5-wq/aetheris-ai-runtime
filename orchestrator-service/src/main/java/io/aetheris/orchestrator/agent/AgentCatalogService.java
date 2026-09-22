package io.aetheris.orchestrator.agent;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AgentCatalogService {

    private final List<AgentDefinition> agents;
    private final Map<String, AgentDefinition> agentsById;

    public AgentCatalogService(AgentCatalogProperties properties) {
        this.agents = List.copyOf(properties.agents());
        Map<String, AgentDefinition> indexed = this.agents.stream()
                .collect(Collectors.toMap(
                        AgentDefinition::id,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        this.agentsById = Collections.unmodifiableMap(indexed);
    }

    public List<AgentDefinition> listAll() {
        return agents;
    }

    public AgentDefinition getRequired(String id) {
        AgentDefinition agent = agentsById.get(id);
        if (agent == null) {
            throw new NoSuchElementException("Unknown agent: " + id);
        }
        return agent;
    }

    public List<AgentDefinition> byDivision(String division) {
        return agents.stream()
                .filter(agent -> agent.division().equalsIgnoreCase(division))
                .toList();
    }

    public Map<String, Long> countByDivision() {
        return agents.stream()
                .collect(Collectors.groupingBy(
                        AgentDefinition::division,
                        LinkedHashMap::new,
                        Collectors.counting()));
    }

    public Map<RiskLevel, Long> countByRisk() {
        return agents.stream()
                .collect(Collectors.groupingBy(
                        AgentDefinition::riskLevel,
                        LinkedHashMap::new,
                        Collectors.counting()));
    }
}
