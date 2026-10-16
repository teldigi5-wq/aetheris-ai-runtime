package io.aetheris.orchestrator.organization;

import io.aetheris.orchestrator.agent.AgentCatalogProperties;
import io.aetheris.orchestrator.agent.AgentCatalogService;
import io.aetheris.orchestrator.agent.AgentDefinition;
import io.aetheris.orchestrator.agent.ModelClass;
import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.api.CompanyOrganizationController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.CacheControl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Catalog-realistic company view: no claims of actual autonomous employees.
 * CI still requires owner approval, no production deployment, no active mic.
 */
@SpringBootTest
class CompanyOrganizationDirectoryTest {
    @Autowired CompanyOrganizationDirectory directory;
    @Autowired CompanyOrganizationController controller;

    @Test
    void canonicalCatalogContainsThirtySpecialistsAcrossThirteenDivisions() {
        var organization = directory.current();
        assertThat(organization.configuredAgentCount()).isEqualTo(30);
        assertThat(organization.configuredDivisionCount()).isEqualTo(13);
        assertThat(organization.divisions().stream()
                .mapToInt(CompanyOrganizationDirectory.DivisionSummary::configuredAgents)
                .sum()).isEqualTo(30);
        assertThat(organization.evidenceClass())
                .isEqualTo("CONFIGURED_CATALOG_NOT_AN_AUTONOMOUS_COMPANY");
    }

    @Test
    void executivePlanningExistsButInventedAutonomousCeoDoesNot() {
        var leadership = directory.current().leadership();
        assertThat(leadership.executivePlannerRegistered()).isTrue();
        assertThat(leadership.chiefExecutiveOfficerRegistered()).isFalse();
        assertThat(leadership.authorityBoundary())
                .isEqualTo("OWNER_APPROVAL_AND_INDEPENDENT_VERIFICATION_REMAIN_REQUIRED");
        assertThat(directory.current().proposedRoleChecks())
                .filteredOn(role -> role.id().equals("chief-executive-officer"))
                .singleElement().extracting(CompanyOrganizationDirectory.ProposedRoleStatus::registered)
                .isEqualTo(false);
    }

    @Test
    void securityDivisionContainsNineActualRegisteredSpecialistsIncludingCiso() {
        var organization = directory.current();
        var security = organization.divisions().stream()
                .filter(division -> division.name().equals("Cybersecurity"))
                .findFirst().orElseThrow();
        assertThat(security.configuredAgents()).isEqualTo(9);
        assertThat(security.agents()).extracting(
                CompanyOrganizationDirectory.AgentSummary::id)
                .contains("chief-information-security-officer", "appsec-officer",
                        "authorized-redteam-officer", "incident-commander");
        assertThat(organization.leadership().securityChiefRegistered()).isTrue();
    }

    @Test
    void dedicatedDesignDivisionGapsAreExplicitNotInventedWorkers() {
        var organization = directory.current();
        assertThat(organization.divisions())
                .noneMatch(d -> d.name().equals("Design Studio"));
        assertThat(organization.proposedRoleChecks())
                .filteredOn(role -> role.division().equals("Design Studio"))
                .extracting(CompanyOrganizationDirectory.ProposedRoleStatus::id)
                .containsExactly("product-design-lead", "ux-researcher",
                        "visual-interface-designer");
        assertThat(organization.proposedRoleChecks())
                .allMatch(role -> !role.registered());
    }

    @Test
    void directoryConfersNoRuntimeAuthorityOrVerifiedAutonomy() {
        var safety = directory.current().safety();
        assertThat(safety.snapshotReadOnly()).isTrue();
        assertThat(safety.grantsExecutionAuthority()).isFalse();
        assertThat(safety.certifiesAutonomousEmployees()).isFalse();
        assertThat(safety.approvesProductionDeployment()).isFalse();
        assertThat(safety.approvesPhysicalOwnerPcPilot()).isFalse();
    }

    @Test
    void getEndpointReturnsNoStoreAndNoOwnerSensitiveData() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/orchestrator/company/organization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.configuredAgentCount").value(30))
                .andExpect(jsonPath("$.configuredDivisionCount").value(13))
                .andExpect(jsonPath("$.safety.grantsExecutionAuthority").value(false))
                .andExpect(jsonPath("$.leadership.chiefExecutiveOfficerRegistered").value(false));
    }

    @Test
    void noPostCommandOrDelegationEndpointCreated() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(post("/api/orchestrator/company/organization"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void duplicateIdentitiesFailClosedRatherThanInflatingCompanyRoster() {
        var repeated = new AgentDefinition("repeat", "Repeat", "Test",
                "No authority", List.of(), List.of(), RiskLevel.LOW,
                ModelClass.GENERAL, true);
        var duplicateCatalog = new AgentCatalogService(
                new AgentCatalogProperties(List.of(repeated, repeated)));
        var isolated = new CompanyOrganizationDirectory(duplicateCatalog);
        assertThatThrownBy(isolated::current)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DUPLICATE_SPECIALIST_ORGANIZATION_IDENTITY");
    }
}
