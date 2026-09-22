package io.aetheris.orchestrator.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "aetheris.agent-catalog")
public record AgentCatalogProperties(List<AgentDefinition> agents) {
    public AgentCatalogProperties {
        agents = agents == null ? List.of() : List.copyOf(agents);
    }
}
