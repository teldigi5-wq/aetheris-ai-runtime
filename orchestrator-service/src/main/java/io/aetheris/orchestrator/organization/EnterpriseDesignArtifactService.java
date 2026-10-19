package io.aetheris.orchestrator.organization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * E2.2A: produces REAL deterministic SVG and JSON design CONTRACT artifacts from
 * an E2.1 project brief. No image/model synthesis, Blender, browser, network,
 * filesystem writes, agent tool invocation or owner approval is performed.
 * Artifacts are plans; no stage may mark them as executed or externally published.
 */
@Service
public final class EnterpriseDesignArtifactService {
    private static final String EVIDENCE = "STATIC_DESIGN_ARTIFACTS_NOT_EXECUTED";
    private final EnterpriseAdvisoryPlanningService planning;
    private final ObjectMapper mapper;

    public EnterpriseDesignArtifactService(EnterpriseAdvisoryPlanningService planning,
                                           ObjectMapper mapper) {
        this.planning = planning;
        this.mapper = mapper;
    }

    public ArtifactBundle create(EnterpriseAdvisoryPlanningService.DesignBrief input) {
        var packet = planning.prepare(input); // existing canonical scope validation
        require(packet.authority() != null
                && !packet.authority().grantsToolExecution()
                && !packet.authority().permitsDelegation()
                && !packet.authority().publishesExternally()
                && !packet.authority().authorizesSpending()
                && !packet.authority().certifiesCompletedWork()
                && "OWNER_APPROVAL_REQUIRED_NOT_GRANTED".equals(
                        packet.authority().ownerApprovalState()),
                "E22_UNSAFE_PLANNING_AUTHORITY");

        var brief = packet.normalizedBrief();
        var scenes = List.of(
            new MotionScene("hero", "Product introduction", 0, 0,
                    "STATIC_LAYOUT_ONLY", "NO_RENDERED_3D_ASSETS"),
            new MotionScene("scroll-reveal", "Scroll controlled product reveal", 0, 119,
                    "PLANNED_SCROLL_TO_FRAME_INDEX", "REQUIRES_120_LICENSED_RENDERED_FRAMES"),
            new MotionScene("accessible-fallback", "Reduced motion accessible content", 0, 0,
                    "NO_SCROLL_ANIMATION", "REQUIRES_SEMANTIC_STATIC_CONTENT")
        );
        var qualityGates = List.of(
                "asset-license-and-provenance",
                "scroll-forward-and-reverse",
                "rapid-scrub-and-viewport-resize",
                "reduced-motion-and-keyboard",
                "responsive-real-browser-visual-regression",
                "loading-memory-frame-time-and-fallback"
        );
        var candidateRoles = packet.stages().stream()
                .map(s -> new HandoffCandidate(s.order(), s.candidateRoleId(),
                        s.state(), s.proposedArtifact(), false)).toList();

        var manifest = new ArtifactManifest(1, EVIDENCE,
                brief.projectId(), brief.companyId(), brief.divisionId(),
                packet.sha256InputDigest(), brief.objective(),
                List.copyOf(brief.acceptanceCriteria()), scenes, qualityGates,
                candidateRoles, false, false, false, false);
        final String manifestJson;
        try {
            manifestJson = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(manifest) + "\n";
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("E22_MANIFEST_SERIALIZATION_FAILED", ex);
        }
        String storyboardSvg = renderStoryboard(manifest);
        require(storyboardSvg.length() <= 30000
                && manifestJson.length() <= 30000, "E22_ARTIFACT_BUDGET_EXCEEDED");
        return new ArtifactBundle(1, EVIDENCE, brief.projectId(), brief.companyId(),
                brief.divisionId(), packet.sha256InputDigest(),
                storyboardSvg, sha256(storyboardSvg), manifestJson,
                sha256(manifestJson), false, false, false, false);
    }

    private static String renderStoryboard(ArtifactManifest m) {
        String scope = xml(shortText(m.companyId() + " / " + m.divisionId(), 70));
        String heading = xml(shortText(m.objective(), 76));
        String project = xml(shortText(m.projectId(), 60));
        // A genuine, accessible, valid standalone SVG storyboard.
        // All variable strings are XML escaped, with invalid XML code points removed.
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <svg xmlns="http://www.w3.org/2000/svg" width="1280" height="720" viewBox="0 0 1280 720" role="img" aria-label="Unexecuted Aetheris motion storyboard">
              <title>Aetheris Immersive — static planning storyboard</title>
              <desc>Concept-only motion and accessibility panels, not 3D assets or a finished website.</desc>
              <rect x="0" y="0" width="1280" height="720" fill="#0a0e18"/>
              <text x="56" y="70" fill="#dbeafe" font-size="18" font-weight="700">AETHERIS / IMMERSIVE DESIGN LAB</text>
              <text x="56" y="108" fill="#94a3b8" font-size="16">PROJECT: %s</text>
              <text x="56" y="151" fill="#f8fafc" font-size="23" font-weight="700">%s</text>
              <text x="56" y="185" fill="#94a3b8" font-size="13">%s</text>
              <rect x="56" y="220" width="370" height="355" rx="20" fill="#182235"/>
              <rect x="455" y="220" width="370" height="355" rx="20" fill="#182235"/>
              <rect x="854" y="220" width="370" height="355" rx="20" fill="#182235"/>
              <rect x="95" y="288" width="290" height="159" rx="12" fill="#273b60"/>
              <rect x="495" y="291" width="288" height="157" rx="12" fill="#294760"/>
              <rect x="894" y="291" width="290" height="157" rx="12" fill="#2c3949"/>
              <text x="95" y="259" fill="#c4d8fa" font-size="19" font-weight="700">01 / HERO</text>
              <text x="495" y="259" fill="#c4d8fa" font-size="19" font-weight="700">02 / SCROLL REVEAL</text>
              <text x="894" y="259" fill="#c4d8fa" font-size="19" font-weight="700">03 / FALLBACK</text>
              <path d="M150 385 L220 320 L300 385 L220 418 Z" fill="#89a2cc"/>
              <path d="M560 385 L625 315 L700 385 L625 427 Z" fill="#a5b4fc"/>
              <rect x="930" y="332" width="211" height="20" rx="5" fill="#cbd5e1"/>
              <rect x="930" y="367" width="170" height="15" rx="5" fill="#94a3b8"/>
              <rect x="930" y="395" width="190" height="15" rx="5" fill="#94a3b8"/>
              <text x="95" y="489" fill="#e2e8f0" font-size="16">Introduction layout</text>
              <text x="95" y="518" fill="#94a3b8" font-size="13">3D render assets not yet created</text>
              <text x="495" y="489" fill="#e2e8f0" font-size="16">Frame planning: 0–119</text>
              <text x="495" y="518" fill="#94a3b8" font-size="13">Sequence and GSAP not yet built</text>
              <text x="894" y="489" fill="#e2e8f0" font-size="16">Static, semantic alternative</text>
              <text x="894" y="518" fill="#94a3b8" font-size="13">Reduced motion required</text>
              <text x="56" y="633" fill="#94a3b8" font-size="14">PROVENANCE: DETERMINISTIC TEMPLATE · NO DESIGN AGENT EXECUTED</text>
              <text x="56" y="666" fill="#f9a8d4" font-size="14">NOT AUTHORIZED FOR PRODUCTION OR PUBLICATION</text>
            </svg>
            """.formatted(project, heading, scope);
    }

    private static String shortText(String text, int limit) {
        var out = new StringBuilder();
        text.codePoints()
                .filter(c -> c == 9 || c == 10 || c == 13
                        || (c >= 32 && c <= 0xD7FF)
                        || (c >= 0xE000 && c <= 0xFFFD)
                        || (c >= 0x10000 && c <= 0x10FFFF))
                .limit(limit)
                .forEach(c -> out.appendCodePoint(c == 10 || c == 13 || c == 9 ? 32 : c));
        return out.toString();
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("E22_SHA256_UNAVAILABLE", ex);
        }
    }

    private static void require(boolean valid, String code) {
        if (!valid) throw new IllegalStateException(code);
    }

    public record MotionScene(String id, String name, int plannedFrameStart,
                              int plannedFrameEnd, String state, String missingPrerequisite) {}
    public record HandoffCandidate(int order, String roleId, String status,
                                   String proposedArtifact, boolean executed) {}
    public record ArtifactManifest(int schemaVersion, String evidenceClass, String projectId,
                                   String companyId, String divisionId, String briefFingerprint,
                                   String objective, List<String> acceptanceCriteria,
                                   List<MotionScene> scenes, List<String> qualityGates,
                                   List<HandoffCandidate> handoffCandidates,
                                   boolean rendered3dAssetsPresent, boolean websiteBuilt,
                                   boolean automatedTestsExecuted, boolean ownerApprovalGranted) {}
    public record ArtifactBundle(int schemaVersion, String evidenceClass, String projectId,
                                 String companyId, String divisionId, String briefFingerprint,
                                 String storyboardSvg, String storyboardSha256,
                                 String manifestJson, String manifestSha256,
                                 boolean executionAuthorized, boolean designAgentsExecuted,
                                 boolean websiteDelivered, boolean productionApproved) {}
}
