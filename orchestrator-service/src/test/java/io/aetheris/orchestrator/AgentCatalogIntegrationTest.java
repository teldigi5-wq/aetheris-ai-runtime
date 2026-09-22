package io.aetheris.orchestrator;

import io.aetheris.orchestrator.agent.AgentCatalogService;
import io.aetheris.orchestrator.agent.RiskLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AgentCatalogIntegrationTest {

    @Autowired
    private AgentCatalogService catalogService;

    @Test
    void loadsTheInitialSyntraAetherisAgentOrganization() {
        assertThat(catalogService.listAll()).hasSizeGreaterThanOrEqualTo(20);
        assertThat(catalogService.getRequired("devops-engineer").division()).isEqualTo("DevOps and SRE");
        assertThat(catalogService.getRequired("authorized-redteam-officer").riskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(catalogService.getRequired("trading-risk-officer").approvalRequiredForHighImpactActions()).isTrue();
    }
}
