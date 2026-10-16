package io.aetheris.orchestrator.organization;

import io.aetheris.orchestrator.agent.AgentCatalogService;
import io.aetheris.orchestrator.agent.AgentDefinition;
import io.aetheris.orchestrator.agent.RiskLevel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stage 46.26: project the REAL configured specialist catalog into an
 * honest, read-only company directory.
 *
 * This is NOT an agent runner, org-command bus, autonomous CEO, personnel
 * management service, permission grant, owner authentication, or proof that
 * a specialist has an installed model/tool capable of completing a job.
 * The runtime's existing authorization and Stage45 NO_GO remain authoritative.
 */
@Service
public final class CompanyOrganizationDirectory {

    private static final List<ProposedRole> PLANNED_ROLE_CHECKS = List.of(
            new ProposedRole("chief-executive-officer", "Chief Executive Officer",
                    "Executive Office"),
            new ProposedRole("product-design-lead", "Product Design Lead",
                    "Design Studio"),
            new ProposedRole("ux-researcher", "UX Researcher", "Design Studio"),
            new ProposedRole("visual-interface-designer", "Visual Interface Designer",
                    "Design Studio")
    );

    private final AgentCatalogService catalog;

    public CompanyOrganizationDirectory(AgentCatalogService catalog) {
        this.catalog = catalog;
    }

    public OrganizationSnapshot current() {
        List<AgentDefinition> agents = catalog.listAll();
        Map<String, List<AgentSummary>> byDivision = new LinkedHashMap<>();
        Set<String> registeredIds = agents.stream()
                .map(AgentDefinition::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (registeredIds.size() != agents.size()) {
            throw new IllegalStateException("DUPLICATE_SPECIALIST_ORGANIZATION_IDENTITY");
        }

        for (AgentDefinition agent : agents) {
            if (agent.id() == null || agent.division() == null ||
                    agent.id().isBlank() || agent.division().isBlank()) {
                throw new IllegalStateException("INVALID_SPECIALIST_ORGANIZATION_IDENTITY");
            }
            byDivision.computeIfAbsent(agent.division(), ignored -> new ArrayList<>())
                    .add(new AgentSummary(agent.id(), agent.displayName(),
                            agent.riskLevel(), agent.approvalRequiredForHighImpactActions()));
        }

        List<DivisionSummary> divisions = byDivision.entrySet().stream()
                .map(entry -> new DivisionSummary(
                        entry.getKey(), entry.getValue().size(), List.copyOf(entry.getValue())))
                .sorted(Comparator.comparing(DivisionSummary::name))
                .toList();

        List<ProposedRoleStatus> proposed = PLANNED_ROLE_CHECKS.stream()
                .map(role -> new ProposedRoleStatus(role.id(), role.title(), role.division(),
                        registeredIds.contains(role.id())))
                .toList();

        return new OrganizationSnapshot(
                1,
                "CONFIGURED_CATALOG_NOT_AN_AUTONOMOUS_COMPANY",
                agents.size(),
                divisions.size(),
                divisions,
                new LeadershipSummary(
                        registeredIds.contains("executive-planner"),
                        registeredIds.contains("chief-executive-officer"),
                        registeredIds.contains("chief-information-security-officer"),
                        "OWNER_APPROVAL_AND_INDEPENDENT_VERIFICATION_REMAIN_REQUIRED"),
                proposed,
                new SafetyBoundaries(
                        true, false, false, false, false));
    }

    private record ProposedRole(String id, String title, String division) {}

    public record AgentSummary(
            String id, String displayName, RiskLevel riskLevel,
            boolean approvalRequiredForHighImpactActions) {}

    public record DivisionSummary(
            String name, int configuredAgents, List<AgentSummary> agents) {}

    public record LeadershipSummary(
            boolean executivePlannerRegistered,
            boolean chiefExecutiveOfficerRegistered,
            boolean securityChiefRegistered,
            String authorityBoundary) {}

    public record ProposedRoleStatus(
            String id, String title, String division, boolean registered) {}

    public record SafetyBoundaries(
            boolean snapshotReadOnly,
            boolean grantsExecutionAuthority,
            boolean certifiesAutonomousEmployees,
            boolean approvesProductionDeployment,
            boolean approvesPhysicalOwnerPcPilot) {}

    public record OrganizationSnapshot(
            int schemaVersion,
            String evidenceClass,
            int configuredAgentCount,
            int configuredDivisionCount,
            List<DivisionSummary> divisions,
            LeadershipSummary leadership,
            List<ProposedRoleStatus> proposedRoleChecks,
            SafetyBoundaries safety) {}
}
