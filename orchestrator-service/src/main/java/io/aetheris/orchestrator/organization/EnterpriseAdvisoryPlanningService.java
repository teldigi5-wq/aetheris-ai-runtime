package io.aetheris.orchestrator.organization;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aetheris.orchestrator.agent.AgentCatalogService;
import io.aetheris.orchestrator.agent.AgentDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * E2: real deterministic, non-executing project-design handoff preparation.
 *
 * This is deliberately NOT an autonomous CEO, agent worker, tool executor,
 * authenticated owner-approval service, durable mission scheduler, HTTP API,
 * or cryptographic audit ledger. No external side effects are possible here.
 * A SHA-256 content fingerprint detects changed packet input; it DOES NOT
 * authenticate the owner or authorize any action.
 */
@Service
public final class EnterpriseAdvisoryPlanningService {
    private static final String SOURCE = "/enterprise/executive-design-advisory-v1.json";
    private static final String EVIDENCE = "ADVISORY_PACKET_NOT_AUTHORIZED_FOR_EXECUTION";
    private static final String ROLE_EVIDENCE = "PROPOSED_ADVISORY_ROLES_NOT_AGENT_IDENTITIES";
    private static final Pattern ID = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private final EnterpriseOrganizationRegistry organization;
    private final AgentCatalogService catalog;
    private final ObjectMapper mapper;
    private final AdvisoryRoles advisoryRoles;

    @Autowired
    public EnterpriseAdvisoryPlanningService(EnterpriseOrganizationRegistry organization,
                                              AgentCatalogService catalog, ObjectMapper mapper) {
        this(organization, catalog, mapper, load(mapper));
    }

    EnterpriseAdvisoryPlanningService(EnterpriseOrganizationRegistry organization,
                                       AgentCatalogService catalog, ObjectMapper mapper,
                                       AdvisoryRoles advisoryRoles) {
        this.organization = organization;
        this.catalog = catalog;
        this.mapper = mapper;
        this.advisoryRoles = validateRoles(advisoryRoles, catalog.listAll());
    }

    private static AdvisoryRoles load(ObjectMapper mapper) {
        try (InputStream in = EnterpriseAdvisoryPlanningService.class.getResourceAsStream(SOURCE)) {
            if (in == null) throw new IllegalStateException("E2_ROLE_BLUEPRINT_MISSING");
            return mapper.readValue(in, AdvisoryRoles.class);
        } catch (IOException ex) {
            throw new IllegalStateException("E2_ROLE_BLUEPRINT_UNREADABLE", ex);
        }
    }

    private static AdvisoryRoles validateRoles(AdvisoryRoles source, List<AgentDefinition> existing) {
        require(source != null && source.schemaVersion() == 1
                && ROLE_EVIDENCE.equals(source.evidenceClass())
                && source.roles() != null && source.roles().size() == 4, "E2_ROLE_SCHEMA_INVALID");
        Set<String> current = new HashSet<>();
        for (AgentDefinition agent : existing) current.add(agent.id());
        Set<String> expected = Set.of("chief-executive-officer", "product-design-lead",
                "ux-researcher", "visual-interface-designer");
        Set<String> seen = new HashSet<>();
        for (AdvisoryRole role : source.roles()) {
            require(role != null && expected.contains(role.id()) && seen.add(role.id())
                    && !current.contains(role.id()) && !blank(role.title())
                    && "PROPOSED".equals(role.status())
                    && (role.id().equals("chief-executive-officer")
                       ? "group-headquarters".equals(role.organization())
                       : "aetheris-creative".equals(role.organization()))
                    && !role.executionEnabled() && !role.authorityGranted()
                    && !role.approvalCanBeIssued()
                    && role.allowedTools() != null && role.allowedTools().isEmpty(),
                    "E2_ROLE_AUTHORITY_OR_IDENTITY_CONFLICT");
        }
        require(seen.equals(expected), "E2_REQUIRED_ROLES_MISSING");
        List<AdvisoryRole> immutable = source.roles().stream()
                .map(r -> new AdvisoryRole(r.id(), r.title(), r.organization(), r.status(),
                        false, false, List.of(), false))
                .toList();
        return new AdvisoryRoles(source.schemaVersion(), source.evidenceClass(), immutable);
    }

    public List<AdvisoryRole> proposedRoles() {
        return advisoryRoles.roles();
    }

    public AdvisoryPacket prepare(DesignBrief input) {
        require(input != null && id(input.projectId()), "E2_PROJECT_ID_INVALID");
        require(id(input.companyId()) && id(input.divisionId()), "E2_ORG_SCOPE_INVALID");
        require(!blank(input.objective()) && input.objective().length() <= 1800,
                "E2_OBJECTIVE_INVALID");
        require(input.acceptanceCriteria() != null && !input.acceptanceCriteria().isEmpty()
                && input.acceptanceCriteria().size() <= 12, "E2_ACCEPTANCE_CRITERIA_INVALID");
        require(input.acceptanceCriteria().stream().allMatch(s ->
                !blank(s) && s.length() <= 300), "E2_ACCEPTANCE_CRITERION_INVALID");

        var snapshot = organization.snapshot();
        var company = snapshot.companies().stream()
                .filter(c -> c.id().equals(input.companyId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("E2_UNKNOWN_COMPANY"));
        require(company.divisions().stream().anyMatch(d -> d.id().equals(input.divisionId())),
                "E2_DIVISION_NOT_OWNED_BY_COMPANY");

        // Real existing catalog roles are referenced as handoff candidates only.
        for (String role : List.of("executive-planner", "project-manager",
                "frontend-engineer", "qa-engineer")) {
            require(catalog.listAll().stream().anyMatch(a -> a.id().equals(role)),
                    "E2_CANONICAL_HANDOFF_ROLE_MISSING");
        }

        var criteria = input.acceptanceCriteria().stream().map(String::trim).toList();
        var normalized = new DesignBrief(input.projectId(), input.companyId(),
                input.divisionId(), input.objective().trim(), List.copyOf(criteria));

        List<PlannedHandoff> stages = List.of(
                new PlannedHandoff(1, "executive-planner", "CONFIGURED_NOT_EXECUTED",
                        "REQUIREMENTS_AND_RISK_PLAN", false),
                new PlannedHandoff(2, "product-design-lead", "PROPOSED_NOT_REGISTERED",
                        "VISUAL_DIRECTION_STORYBOARD", false),
                new PlannedHandoff(3, "ux-researcher", "PROPOSED_NOT_REGISTERED",
                        "USER_FLOW_AND_ACCESSIBILITY_REVIEW", false),
                new PlannedHandoff(4, "visual-interface-designer", "PROPOSED_NOT_REGISTERED",
                        "COMPONENT_AND_MOTION_SPECIFICATION", false),
                new PlannedHandoff(5, "frontend-engineer", "CONFIGURED_NOT_EXECUTED",
                        "IMPLEMENTATION_HANDOFF_PROPOSAL", false),
                new PlannedHandoff(6, "qa-engineer", "CONFIGURED_NOT_EXECUTED",
                        "INDEPENDENT_TEST_PLAN", false),
                new PlannedHandoff(7, "project-manager", "CONFIGURED_NOT_EXECUTED",
                        "OWNER_APPROVAL_REQUEST", true)
        );
        List<String> reviewChecks = List.of(
                "Verify source and usage rights of every 3D, video, font and photographic asset",
                "Independently test scroll direction, rapid scrubbing, viewport resize and frame loading",
                "Verify reduced-motion preference, keyboard navigation and semantic content",
                "Measure browser loading, memory, WebGL fallback and responsive device behavior",
                "Confirm project acceptance criteria and security findings using independent evidence"
        );
        String digest;
        try {
            byte[] material = mapper.writeValueAsBytes(normalized);
            digest = java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(material));
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("E2_FINGERPRINT_FAILED", ex);
        }
        return new AdvisoryPacket(1, EVIDENCE, digest, normalized,
                stages, List.copyOf(reviewChecks), List.copyOf(advisoryRoles.roles()),
                new AuthorityBoundary("OWNER_APPROVAL_REQUIRED_NOT_GRANTED",
                        false, false, false, false, false));
    }

    private static boolean id(String value) {
        return value != null && ID.matcher(value).matches() && value.length() <= 80;
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static void require(boolean valid, String code) {
        if (!valid) throw new IllegalArgumentException(code);
    }

    public record AdvisoryRoles(int schemaVersion, String evidenceClass,
                                List<AdvisoryRole> roles) {}
    public record AdvisoryRole(String id, String title, String organization,
                               String status, boolean executionEnabled,
                               boolean authorityGranted, List<String> allowedTools,
                               boolean approvalCanBeIssued) {}
    public record DesignBrief(String projectId, String companyId, String divisionId,
                              String objective, List<String> acceptanceCriteria) {}
    public record PlannedHandoff(int order, String candidateRoleId, String state,
                                 String proposedArtifact, boolean requiresOwnerApproval) {}
    public record AuthorityBoundary(String ownerApprovalState,
                                    boolean grantsToolExecution, boolean permitsDelegation,
                                    boolean publishesExternally, boolean authorizesSpending,
                                    boolean certifiesCompletedWork) {}
    public record AdvisoryPacket(int schemaVersion, String evidenceClass, String sha256InputDigest,
                                 DesignBrief normalizedBrief, List<PlannedHandoff> stages,
                                 List<String> reviewChecks, List<AdvisoryRole> proposedRoles,
                                 AuthorityBoundary authority) {}
}
