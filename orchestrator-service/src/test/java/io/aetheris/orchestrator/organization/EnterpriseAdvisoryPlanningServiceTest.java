package io.aetheris.orchestrator.organization;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aetheris.orchestrator.agent.AgentCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class EnterpriseAdvisoryPlanningServiceTest {
    @Autowired EnterpriseAdvisoryPlanningService service;
    @Autowired EnterpriseOrganizationRegistry organization;
    @Autowired AgentCatalogService catalog;
    @Autowired ObjectMapper mapper;

    private EnterpriseAdvisoryPlanningService.DesignBrief brief() {
        return new EnterpriseAdvisoryPlanningService.DesignBrief(
                "rog-laptop-study", "aetheris-immersive", "scroll-experience-engineering",
                "Build a cinematic product site with a scroll-bound 3D animation",
                List.of("Playable reverse scroll", "Accessible reduced-motion alternative",
                        "Independent browser quality tests"));
    }

    @Test
    void realSevenStepAdvisoryHandoffForImmersiveProject() {
        var result = service.prepare(brief());
        assertThat(result.evidenceClass()).isEqualTo("ADVISORY_PACKET_NOT_AUTHORIZED_FOR_EXECUTION");
        assertThat(result.sha256InputDigest()).matches("[0-9a-f]{64}");
        assertThat(result.stages()).hasSize(7);
        assertThat(result.stages()).extracting(
                EnterpriseAdvisoryPlanningService.PlannedHandoff::candidateRoleId)
                .containsExactly("executive-planner", "product-design-lead",
                        "ux-researcher", "visual-interface-designer",
                        "frontend-engineer", "qa-engineer", "project-manager");
        assertThat(result.stages().get(6).requiresOwnerApproval()).isTrue();
        assertThat(result.reviewChecks()).anyMatch(s -> s.contains("scroll direction"));
        assertThat(result.authority().ownerApprovalState())
                .isEqualTo("OWNER_APPROVAL_REQUIRED_NOT_GRANTED");
        assertThat(result.authority().grantsToolExecution()).isFalse();
        assertThat(result.authority().permitsDelegation()).isFalse();
        assertThat(result.authority().publishesExternally()).isFalse();
        assertThat(result.authority().authorizesSpending()).isFalse();
        assertThat(result.authority().certifiesCompletedWork()).isFalse();
    }

    @Test
    void proposedRolesRemainUnregisteredAndCannotProvideToolsOrApproval() {
        assertThat(service.proposedRoles()).hasSize(4);
        Set<String> real = catalog.listAll().stream().map(a -> a.id())
                .collect(Collectors.toSet());
        assertThat(catalog.listAll()).hasSize(26);
        for (var role : service.proposedRoles()) {
            assertThat(real).doesNotContain(role.id());
            assertThat(role.status()).isEqualTo("PROPOSED");
            assertThat(role.allowedTools()).isEmpty();
            assertThat(role.executionEnabled()).isFalse();
            assertThat(role.authorityGranted()).isFalse();
            assertThat(role.approvalCanBeIssued()).isFalse();
        }
        assertThat(service.proposedRoles()).extracting(
                EnterpriseAdvisoryPlanningService.AdvisoryRole::id)
                .containsExactlyInAnyOrder("chief-executive-officer",
                        "product-design-lead", "ux-researcher", "visual-interface-designer");
    }

    @Test
    void identicalInputProducesIdenticalFingerprintAndChangedInputDoesNot() {
        var first = service.prepare(brief());
        var second = service.prepare(brief());
        assertThat(first.sha256InputDigest()).isEqualTo(second.sha256InputDigest());
        var changed = new EnterpriseAdvisoryPlanningService.DesignBrief(
                brief().projectId(), brief().companyId(), brief().divisionId(),
                brief().objective(), List.of("Different acceptance target"));
        assertThat(service.prepare(changed).sha256InputDigest())
                .isNotEqualTo(first.sha256InputDigest());
    }

    @Test
    void rejectsCompanyDivisionScopeMismatch() {
        var invalid = new EnterpriseAdvisoryPlanningService.DesignBrief(
                "rog-laptop-study", "aetheris-finance", "scroll-experience-engineering",
                brief().objective(), brief().acceptanceCriteria());
        assertThatThrownBy(() -> service.prepare(invalid))
                .hasMessage("E2_DIVISION_NOT_OWNED_BY_COMPANY");
    }

    @Test
    void rejectsNonexistentCompaniesOrInvalidProjectIdentifiers() {
        var unknown = new EnterpriseAdvisoryPlanningService.DesignBrief(
                "rog-laptop-study", "company-not-in-registry",
                "scroll-experience-engineering", brief().objective(), brief().acceptanceCriteria());
        assertThatThrownBy(() -> service.prepare(unknown))
                .hasMessage("E2_UNKNOWN_COMPANY");
        var invalid = new EnterpriseAdvisoryPlanningService.DesignBrief(
                "../private-credentials", brief().companyId(),
                brief().divisionId(), brief().objective(), brief().acceptanceCriteria());
        assertThatThrownBy(() -> service.prepare(invalid))
                .hasMessage("E2_PROJECT_ID_INVALID");
    }

    @Test
    void rejectsOversizedOrEmptyBriefAndCriteria() {
        var empty = new EnterpriseAdvisoryPlanningService.DesignBrief(
                brief().projectId(), brief().companyId(), brief().divisionId(),
                brief().objective(), List.of());
        assertThatThrownBy(() -> service.prepare(empty))
                .hasMessage("E2_ACCEPTANCE_CRITERIA_INVALID");
        var oversized = new EnterpriseAdvisoryPlanningService.DesignBrief(
                brief().projectId(), brief().companyId(), brief().divisionId(),
                "x".repeat(1801), brief().acceptanceCriteria());
        assertThatThrownBy(() -> service.prepare(oversized))
                .hasMessage("E2_OBJECTIVE_INVALID");
        var injection = new EnterpriseAdvisoryPlanningService.DesignBrief(
                brief().projectId(), brief().companyId(), brief().divisionId(),
                brief().objective(), List.of((String) null));
        assertThatThrownBy(() -> service.prepare(injection))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesNewToolsOrAuthorityForProposedCeo() {
        var modified = new ArrayList<>(service.proposedRoles());
        var ceo = modified.get(0);
        modified.set(0, new EnterpriseAdvisoryPlanningService.AdvisoryRole(
                ceo.id(), ceo.title(), ceo.organization(), ceo.status(),
                false, false, List.of("terminal"), false));
        var injected = new EnterpriseAdvisoryPlanningService.AdvisoryRoles(
                1, "PROPOSED_ADVISORY_ROLES_NOT_AGENT_IDENTITIES", modified);
        assertThatThrownBy(() -> new EnterpriseAdvisoryPlanningService(
                organization, catalog, mapper, injected))
                .hasMessage("E2_ROLE_AUTHORITY_OR_IDENTITY_CONFLICT");
    }

    @Test
    void refusesCeoSelfApprovalOrFalseActivation() {
        var roles = new ArrayList<>(service.proposedRoles());
        var original = roles.get(0);
        roles.set(0, new EnterpriseAdvisoryPlanningService.AdvisoryRole(
                original.id(), original.title(), original.organization(), original.status(),
                true, false, List.of(), true));
        var injected = new EnterpriseAdvisoryPlanningService.AdvisoryRoles(
                1, "PROPOSED_ADVISORY_ROLES_NOT_AGENT_IDENTITIES", roles);
        assertThatThrownBy(() -> new EnterpriseAdvisoryPlanningService(
                organization, catalog, mapper, injected))
                .hasMessage("E2_ROLE_AUTHORITY_OR_IDENTITY_CONFLICT");
    }

    @Test
    void proposedRoleIdsMustNotShadowRealCanonicalAgents() {
        var roles = new ArrayList<>(service.proposedRoles());
        var original = roles.get(0);
        roles.set(0, new EnterpriseAdvisoryPlanningService.AdvisoryRole(
                "executive-planner", original.title(), original.organization(), original.status(),
                false, false, List.of(), false));
        var injected = new EnterpriseAdvisoryPlanningService.AdvisoryRoles(
                1, "PROPOSED_ADVISORY_ROLES_NOT_AGENT_IDENTITIES", roles);
        assertThatThrownBy(() -> new EnterpriseAdvisoryPlanningService(
                organization, catalog, mapper, injected))
                .hasMessage("E2_ROLE_AUTHORITY_OR_IDENTITY_CONFLICT");
    }

    @Test
    void resultListsCannotBeReorderedToImpersonateVerifiedHandoffs() {
        var packet = service.prepare(brief());
        assertThatThrownBy(() -> packet.stages().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> packet.reviewChecks().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> packet.proposedRoles().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
