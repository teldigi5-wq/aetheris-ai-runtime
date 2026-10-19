package io.aetheris.orchestrator.organization;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aetheris.orchestrator.agent.AgentCatalogService;
import io.aetheris.orchestrator.agent.AgentDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Stage E1: READ-ONLY organizational metadata, never a worker or permission registry.
 * An organization/role label confers no authority. All existing catalog tool, risk,
 * approval and identity definitions remain owned by AgentCatalogService.
 *
 * Deliberately NO HTTP controller: the main branch does not yet provide a verified
 * authenticated owner-scoped gate for this new endpoint. Future access is gated on
 * an independently verified owner principal; do not expose this by an open GET.
 */
@Service
public final class EnterpriseOrganizationRegistry {
    private static final String SOURCE = "/enterprise/organization-blueprint-v1.json";
    private static final String HEADQUARTERS = "group-headquarters";
    private static final String EVIDENCE = "PROPOSED_TAXONOMY_NON_EXECUTABLE";
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private final AgentCatalogService catalog;
    private final Blueprint blueprint;

    @Autowired
    public EnterpriseOrganizationRegistry(AgentCatalogService catalog, ObjectMapper mapper) {
        this(catalog, load(mapper));
    }

    EnterpriseOrganizationRegistry(AgentCatalogService catalog, Blueprint blueprint) {
        this.catalog = catalog;
        this.blueprint = blueprint;
        snapshot(); // fail closed on invalid committed source during bean construction
    }

    private static Blueprint load(ObjectMapper mapper) {
        try (InputStream in = EnterpriseOrganizationRegistry.class.getResourceAsStream(SOURCE)) {
            if (in == null) throw new IllegalStateException("ENTERPRISE_BLUEPRINT_MISSING");
            return mapper.readValue(in, Blueprint.class);
        } catch (IOException e) {
            throw new IllegalStateException("ENTERPRISE_BLUEPRINT_UNREADABLE", e);
        }
    }

    public Snapshot snapshot() {
        return evaluate(blueprint, catalog.listAll());
    }

    static Snapshot evaluate(Blueprint data, List<AgentDefinition> agents) {
        require(data != null && agents != null, "ENTERPRISE_SOURCE_MISSING");
        require(data.schemaVersion() == 1 && EVIDENCE.equals(data.evidenceClass()),
                "ENTERPRISE_SCHEMA_UNSUPPORTED");
        require(data.targetCompanyCount() == 13 && data.targetDivisionCount() == 100,
                "ENTERPRISE_TARGET_CONTRACT_UNEXPECTED");
        require(data.companies() != null && data.companies().size() == 13,
                "ENTERPRISE_COMPANY_COUNT_INVALID");
        require(data.legacyDivisionCompanyAssignments() != null,
                "ENTERPRISE_LEGACY_MAPPING_MISSING");

        Set<String> companyIds = new HashSet<>();
        Set<String> globalIds = new HashSet<>();
        Map<String, Integer> companyDivisionCounts = new LinkedHashMap<>();
        List<CompanySummary> companySummaries = new ArrayList<>();
        int totalDivisions = 0;
        for (Company company : data.companies()) {
            require(company != null && slug(company.id()) && !blank(company.name()),
                    "ENTERPRISE_COMPANY_INVALID");
            require(companyIds.add(company.id()) && globalIds.add(company.id()),
                    "ENTERPRISE_COMPANY_DUPLICATE");
            require(company.divisions() != null && !company.divisions().isEmpty(),
                    "ENTERPRISE_DIVISIONS_MISSING");
            List<DivisionSummary> divisions = new ArrayList<>();
            for (Division division : company.divisions()) {
                require(division != null && slug(division.id()) && !blank(division.name()),
                        "ENTERPRISE_DIVISION_INVALID");
                require(globalIds.add(division.id()), "ENTERPRISE_DIVISION_DUPLICATE");
                divisions.add(new DivisionSummary(division.id(), division.name(), "PROPOSED", false));
                totalDivisions++;
            }
            companyDivisionCounts.put(company.id(), divisions.size());
            companySummaries.add(new CompanySummary(company.id(), company.name(),
                    "PROPOSED", List.copyOf(divisions), 0));
        }
        require(totalDivisions == 100, "ENTERPRISE_DIVISION_COUNT_INVALID");
        require(agents.stream().allMatch(a -> a != null && !blank(a.id()) && !blank(a.division())),
                "ENTERPRISE_AGENT_IDENTITY_INVALID");
        Set<String> agentIds = new HashSet<>();
        for (AgentDefinition agent : agents) {
            require(agentIds.add(agent.id()), "ENTERPRISE_CATALOG_IDENTITY_DUPLICATE");
        }

        Map<String, Integer> legacyCounts = new HashMap<>();
        for (AgentDefinition agent : agents) {
            legacyCounts.merge(agent.division(), 1, Integer::sum);
        }
        Map<String, String> assignments = data.legacyDivisionCompanyAssignments();
        require(assignments.keySet().equals(legacyCounts.keySet()),
                "ENTERPRISE_LEGACY_MAPPING_NOT_EXACT");
        Map<String, Integer> assignedByCompany = new HashMap<>();
        for (Map.Entry<String, String> entry : assignments.entrySet()) {
            require(companyIds.contains(entry.getValue()) || HEADQUARTERS.equals(entry.getValue()),
                    "ENTERPRISE_LEGACY_TARGET_INVALID");
            assignedByCompany.merge(entry.getValue(), legacyCounts.get(entry.getKey()), Integer::sum);
        }
        int headquartersCount = assignedByCompany.getOrDefault(HEADQUARTERS, 0);
        List<CompanySummary> calculated = companySummaries.stream()
                .map(company -> new CompanySummary(company.id(), company.name(), company.status(),
                        company.divisions(), assignedByCompany.getOrDefault(company.id(), 0)))
                .toList();
        require(assignedByCompany.values().stream().mapToInt(Integer::intValue).sum() == agents.size(),
                "ENTERPRISE_CATALOG_IDENTITY_COVERAGE_FAILED");

        return new Snapshot(1, EVIDENCE, calculated.size(), totalDivisions, agents.size(),
                legacyCounts.size(), headquartersCount, List.copyOf(calculated),
                Map.copyOf(assignments),
                new Safety(true, false, false, false, false, false));
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static boolean slug(String value) { return value != null && SLUG.matcher(value).matches(); }
    private static void require(boolean condition, String code) {
        if (!condition) throw new IllegalStateException(code);
    }

    public record Blueprint(int schemaVersion, String evidenceClass,
            int targetCompanyCount, int targetDivisionCount, List<Company> companies,
            Map<String, String> legacyDivisionCompanyAssignments) {}
    public record Company(String id, String name, List<Division> divisions) {}
    public record Division(String id, String name) {}
    public record CompanySummary(String id, String name, String status,
            List<DivisionSummary> divisions, int existingConfiguredAgentCount) {}
    public record DivisionSummary(String id, String name, String status, boolean workerVerified) {}
    public record Safety(boolean readOnly, boolean executionAuthorityGranted,
            boolean autonomousWorkersVerified, boolean crossTenantAccessGranted,
            boolean productionDeploymentApproved, boolean financialActionsAuthorized) {}
    public record Snapshot(int schemaVersion, String evidenceClass, int proposedCompanyCount,
            int proposedDivisionCount, int existingConfiguredAgentCount,
            int existingLegacyDivisionCount, int headquartersConfiguredAgentCount,
            List<CompanySummary> companies, Map<String, String> legacyDivisionCompanyAssignments,
            Safety safety) {}
}
