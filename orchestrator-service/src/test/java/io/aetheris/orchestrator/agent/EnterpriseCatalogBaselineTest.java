package io.aetheris.orchestrator.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage E0: lock the REAL protected-main catalog rather than a proposed organization.
 * A deliberate catalog change must update the fixture through human-reviewed PR.
 * No worker activation, permissions, or production authority are granted here.
 */
@SpringBootTest
class EnterpriseCatalogBaselineTest {
    private static final String BASELINE = "/enterprise/canonical-main-specialists.json";

    @Autowired
    AgentCatalogService catalog;

    @Test
    void canonicalMainIdentityAndAuthoritySnapshotHasNotDrifted() throws Exception {
        List<AgentDefinition> agents = catalog.listAll();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode locked;
        try (InputStream input = getClass().getResourceAsStream(BASELINE)) {
            assertThat(input).as("committed E0 baseline fixture").isNotNull();
            locked = mapper.readTree(input);
        }

        assertThat(locked.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(locked.path("sourceBranch").asText()).isEqualTo("main");
        assertThat(agents).hasSize(locked.path("roleCount").asInt());
        assertThat(agents.stream().map(AgentDefinition::id).distinct().count())
                .as("duplicate identities are forbidden").isEqualTo(agents.size());
        assertThat(agents.stream().map(AgentDefinition::division).collect(Collectors.toSet()))
                .hasSize(locked.path("divisionCount").asInt());

        List<Map<String, Object>> actual = new ArrayList<>();
        for (AgentDefinition agent : agents.stream()
                .sorted(Comparator.comparing(AgentDefinition::id)).toList()) {
            Map<String, Object> identity = new LinkedHashMap<>();
            identity.put("id", agent.id());
            identity.put("division", agent.division());
            identity.put("riskLevel", agent.riskLevel().name());
            identity.put("preferredModelClass", agent.preferredModelClass().name());
            identity.put("approvalRequiredForHighImpactActions",
                    agent.approvalRequiredForHighImpactActions());
            identity.put("allowedTools", agent.allowedTools());
            actual.add(identity);
        }
        JsonNode observed = mapper.valueToTree(actual);
        assertThat(observed)
                .as("no identity loss, added roles, division drift, model/risk change or tool grant drift")
                .isEqualTo(locked.path("agents"));
    }

    @Test
    void highRiskRolesCannotLoseHumanApproval() {
        for (AgentDefinition agent : catalog.listAll()) {
            if (Set.of(RiskLevel.HIGH, RiskLevel.CRITICAL).contains(agent.riskLevel())) {
                assertThat(agent.approvalRequiredForHighImpactActions())
                        .as("human approval required for high-risk specialist " + agent.id())
                        .isTrue();
            }
        }
    }

    @Test
    void mainMustNotClaimDraftOnlyCisoOrDedicatedDesignAndCeoAgents() {
        Set<String> configured = catalog.listAll().stream()
                .map(AgentDefinition::id).collect(Collectors.toSet());
        assertThat(configured).doesNotContain(
                "chief-information-security-officer", "cryptography-officer",
                "detection-engineer", "threat-hunter",
                "chief-executive-officer", "product-design-lead",
                "ux-researcher", "visual-interface-designer");
        assertThat(catalog.byDivision("Cybersecurity")).hasSize(5);
    }
}
