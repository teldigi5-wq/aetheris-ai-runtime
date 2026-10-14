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
    void cybersecurityDivisionIsOwnerGovernedAndRedTeamRestrictedToAuthorizedLabs() {
        var cyber = catalogService.byDivision("Cybersecurity");
        assertThat(cyber).hasSizeGreaterThanOrEqualTo(9);
        assertThat(cyber).extracting(io.aetheris.orchestrator.agent.AgentDefinition::id)
                .contains("chief-information-security-officer", "cryptography-officer",
                        "detection-engineer", "threat-hunter", "security-architect",
                        "blue-team-officer", "appsec-officer",
                        "authorized-redteam-officer", "incident-commander");
        assertThat(cyber).allMatch(agent -> agent.approvalRequiredForHighImpactActions()
                && (agent.riskLevel() == RiskLevel.HIGH || agent.riskLevel() == RiskLevel.CRITICAL));
        var red = catalogService.getRequired("authorized-redteam-officer");
        assertThat(red.riskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(red.allowedTools()).containsExactlyInAnyOrder(
                "security-lab", "approved-targets", "reports");
        assertThat(red.allowedTools()).doesNotContain("terminal","pc-control",
                "browser","credentials-vault","network-scanner");
        assertThat(catalogService.getRequired("cryptography-officer").allowedTools())
                .doesNotContain("credentials-vault", "terminal");
    }

    @Test
    void loadsTheInitialSyntraAetherisAgentOrganization() {
        assertThat(catalogService.listAll()).hasSizeGreaterThanOrEqualTo(20);
        assertThat(catalogService.getRequired("devops-engineer").division()).isEqualTo("DevOps and SRE");
        assertThat(catalogService.getRequired("authorized-redteam-officer").riskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(catalogService.getRequired("trading-risk-officer").approvalRequiredForHighImpactActions()).isTrue();
    }
}
