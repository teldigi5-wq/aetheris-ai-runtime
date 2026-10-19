package io.aetheris.orchestrator.organization;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aetheris.orchestrator.agent.AgentCatalogService;
import io.aetheris.orchestrator.agent.AgentDefinition;
import io.aetheris.orchestrator.agent.ModelClass;
import io.aetheris.orchestrator.agent.RiskLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class EnterpriseOrganizationRegistryTest {
    @Autowired EnterpriseOrganizationRegistry registry;
    @Autowired AgentCatalogService catalog;
    @Autowired ObjectMapper mapper;

    private EnterpriseOrganizationRegistry.Blueprint source() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/enterprise/organization-blueprint-v1.json")) {
            assertThat(input).isNotNull();
            return mapper.readValue(input, EnterpriseOrganizationRegistry.Blueprint.class);
        }
    }

    private EnterpriseOrganizationRegistry.Blueprint rewrite(
            EnterpriseOrganizationRegistry.Blueprint original,
            List<EnterpriseOrganizationRegistry.Company> companies,
            Map<String, String> mapping) {
        return new EnterpriseOrganizationRegistry.Blueprint(
                original.schemaVersion(), original.evidenceClass(),
                original.targetCompanyCount(), original.targetDivisionCount(),
                companies, mapping);
    }

    @Test
    void declaresThirteenProposedCompaniesAndOneHundredUniqueDivisions() {
        var snapshot = registry.snapshot();
        assertThat(snapshot.proposedCompanyCount()).isEqualTo(13);
        assertThat(snapshot.proposedDivisionCount()).isEqualTo(100);
        assertThat(snapshot.existingLegacyDivisionCount()).isEqualTo(13);
        assertThat(snapshot.existingConfiguredAgentCount()).isEqualTo(26);
        assertThat(snapshot.headquartersConfiguredAgentCount()).isEqualTo(4);
        assertThat(snapshot.evidenceClass()).isEqualTo("PROPOSED_TAXONOMY_NON_EXECUTABLE");
        assertThat(snapshot.companies()).allMatch(c -> c.status().equals("PROPOSED"));
        assertThat(snapshot.companies().stream().flatMap(c -> c.divisions().stream())
                .map(EnterpriseOrganizationRegistry.DivisionSummary::id)
                .distinct().count()).isEqualTo(100L);
        assertThat(snapshot.companies().stream().flatMap(c -> c.divisions().stream())
                .allMatch(d -> d.status().equals("PROPOSED") && !d.workerVerified())).isTrue();
    }

    @Test
    void immersiveContainsTwelveRealPlanningDisciplinesButNoWorkers() {
        var immersive = registry.snapshot().companies().stream()
                .filter(c -> c.id().equals("aetheris-immersive")).findFirst().orElseThrow();
        assertThat(immersive.divisions()).hasSize(12);
        assertThat(immersive.existingConfiguredAgentCount()).isZero();
        assertThat(immersive.divisions()).extracting(
                EnterpriseOrganizationRegistry.DivisionSummary::id)
                .contains("scroll-experience-engineering", "frame-sequence-animation",
                        "realtime-3d-web-engineering", "photorealistic-rendering",
                        "visual-qa-accessibility", "3d-modeling-asset-creation");
    }

    @Test
    void mapsAllExistingAgentsWithoutInventingExecutableSpecialists() {
        var snapshot = registry.snapshot();
        assertThat(snapshot.legacyDivisionCompanyAssignments().keySet())
                .isEqualTo(catalog.listAll().stream()
                        .map(AgentDefinition::division).collect(Collectors.toSet()));
        int assigned = snapshot.companies().stream()
                .mapToInt(EnterpriseOrganizationRegistry.CompanySummary::existingConfiguredAgentCount)
                .sum() + snapshot.headquartersConfiguredAgentCount();
        assertThat(assigned).isEqualTo(catalog.listAll().size());
        assertThat(snapshot.safety().readOnly()).isTrue();
        assertThat(snapshot.safety().executionAuthorityGranted()).isFalse();
        assertThat(snapshot.safety().autonomousWorkersVerified()).isFalse();
        assertThat(snapshot.safety().crossTenantAccessGranted()).isFalse();
        assertThat(snapshot.safety().productionDeploymentApproved()).isFalse();
        assertThat(snapshot.safety().financialActionsAuthorized()).isFalse();
    }

    @Test
    void rejectsDuplicateDivisionIdsAcrossDifferentCompanies() throws Exception {
        var original = source();
        var companies = new ArrayList<>(original.companies());
        var first = companies.get(0);
        var second = companies.get(1);
        var invalid = new ArrayList<>(second.divisions());
        invalid.set(0, new EnterpriseOrganizationRegistry.Division(
                first.divisions().get(0).id(), "Duplicate"));
        companies.set(1, new EnterpriseOrganizationRegistry.Company(
                second.id(), second.name(), invalid));
        assertThatThrownBy(() -> EnterpriseOrganizationRegistry.evaluate(
                rewrite(original, companies, original.legacyDivisionCompanyAssignments()),
                catalog.listAll())).isInstanceOf(IllegalStateException.class)
                .hasMessage("ENTERPRISE_DIVISION_DUPLICATE");
    }

    @Test
    void rejectsUnknownLegacyTenantAndMissingLegacyDivision() throws Exception {
        var original = source();
        var invalid = new LinkedHashMap<>(original.legacyDivisionCompanyAssignments());
        invalid.put("Software Engineering", "untrusted-company");
        assertThatThrownBy(() -> EnterpriseOrganizationRegistry.evaluate(
                rewrite(original, original.companies(), invalid), catalog.listAll()))
                .hasMessage("ENTERPRISE_LEGACY_TARGET_INVALID");
        invalid.remove("Software Engineering");
        assertThatThrownBy(() -> EnterpriseOrganizationRegistry.evaluate(
                rewrite(original, original.companies(), invalid), catalog.listAll()))
                .hasMessage("ENTERPRISE_LEGACY_MAPPING_NOT_EXACT");
    }

    @Test
    void rejectsAdditionalIdentityWithNoMappedDivision() throws Exception {
        var original = source();
        List<AgentDefinition> inflated = new ArrayList<>(catalog.listAll());
        inflated.add(new AgentDefinition("invented-employee", "Unapproved employee",
                "Invented Division", "No approved action", List.of(), List.of(),
                RiskLevel.LOW, ModelClass.GENERAL, true));
        assertThatThrownBy(() -> EnterpriseOrganizationRegistry.evaluate(
                original, inflated)).hasMessage("ENTERPRISE_LEGACY_MAPPING_NOT_EXACT");
    }

    @Test
    void willAcceptExplicitlyAddedRolesWithinKnownDivisionWithoutPromotingToWorker() throws Exception {
        var original = source();
        List<AgentDefinition> augmented = new ArrayList<>(catalog.listAll());
        augmented.add(new AgentDefinition("synthetic-catalog-addition", "Synthetic Catalog Identity",
                "Cybersecurity", "No deployment authority", List.of(), List.of(),
                RiskLevel.HIGH, ModelClass.SECURITY, true));
        var snapshot = EnterpriseOrganizationRegistry.evaluate(original, augmented);
        assertThat(snapshot.existingConfiguredAgentCount()).isEqualTo(27);
        assertThat(snapshot.safety().autonomousWorkersVerified()).isFalse();
    }

    @Test
    void returnedListsAndAssignmentsCannotBeMutated() {
        var snapshot = registry.snapshot();
        assertThatThrownBy(() -> snapshot.companies().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.legacyDivisionCompanyAssignments()
                .put("Software Engineering", "aetheris-finance"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.companies().get(0).divisions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
