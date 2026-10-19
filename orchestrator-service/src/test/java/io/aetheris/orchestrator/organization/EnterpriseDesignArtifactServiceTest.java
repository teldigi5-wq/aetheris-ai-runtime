package io.aetheris.orchestrator.organization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class EnterpriseDesignArtifactServiceTest {
    @Autowired EnterpriseDesignArtifactService generator;
    @Autowired EnterpriseDesignArtifactVerifier verifier;
    @Autowired ObjectMapper mapper;

    private EnterpriseAdvisoryPlanningService.DesignBrief brief() {
        return new EnterpriseAdvisoryPlanningService.DesignBrief(
            "cinematic-device", "aetheris-immersive", "scroll-experience-engineering",
            "Create an accessible cinematic laptop reveal with frame-accurate scroll",
            List.of("Scroll backwards with no frame gaps",
                    "Provide a static semantic fallback",
                    "Verify browser loading and responsive layout"));
    }

    private EnterpriseDesignArtifactService.ArtifactBundle replace(
            EnterpriseDesignArtifactService.ArtifactBundle original,
            String svg, String manifest, boolean recomputeDigest) {
        return new EnterpriseDesignArtifactService.ArtifactBundle(
                original.schemaVersion(), original.evidenceClass(), original.projectId(),
                original.companyId(), original.divisionId(), original.briefFingerprint(),
                svg, recomputeDigest ? EnterpriseDesignArtifactService.sha256(svg)
                        : original.storyboardSha256(),
                manifest, recomputeDigest ? EnterpriseDesignArtifactService.sha256(manifest)
                        : original.manifestSha256(),
                original.executionAuthorized(), original.designAgentsExecuted(),
                original.websiteDelivered(), original.productionApproved());
    }

    @Test
    void createsRealStandAloneSvgAndThreeSceneMachineReadableContract() throws Exception {
        var result = generator.create(brief());
        assertThat(result.storyboardSvg()).startsWith("<?xml version=");
        assertThat(result.storyboardSvg()).contains("<svg ", "<title>", "02 / SCROLL REVEAL",
                "03 / FALLBACK", "NO DESIGN AGENT EXECUTED");
        assertThat(result.manifestJson()).contains("STATIC_DESIGN_ARTIFACTS_NOT_EXECUTED");
        var manifest = mapper.readTree(result.manifestJson());
        assertThat(manifest.path("scenes").size()).isEqualTo(3);
        assertThat(manifest.path("scenes").get(1).path("plannedFrameEnd").asInt()).isEqualTo(119);
        assertThat(manifest.path("qualityGates").size()).isEqualTo(6);
        assertThat(manifest.path("handoffCandidates").size()).isEqualTo(7);
        assertThat(manifest.path("rendered3dAssetsPresent").asBoolean()).isFalse();
        assertThat(manifest.path("automatedTestsExecuted").asBoolean()).isFalse();
        assertThat(result.websiteDelivered()).isFalse();
        assertThat(result.productionApproved()).isFalse();
        assertThat(verifier.verify(result).verificationClass())
                .isEqualTo("VERIFIED_STATIC_ARTIFACT_FORMAT_ONLY");
    }

    @Test
    void deterministicSameBriefAndChangedInputGetsDifferentHashes() {
        var first = generator.create(brief());
        var next = generator.create(brief());
        assertThat(first.storyboardSvg()).isEqualTo(next.storyboardSvg());
        assertThat(first.manifestJson()).isEqualTo(next.manifestJson());
        assertThat(first.storyboardSha256()).isEqualTo(next.storyboardSha256());
        assertThat(first.manifestSha256()).isEqualTo(next.manifestSha256());
        var changed = new EnterpriseAdvisoryPlanningService.DesignBrief(
                brief().projectId(), brief().companyId(), brief().divisionId(),
                brief().objective() + " and mobile", brief().acceptanceCriteria());
        var other = generator.create(changed);
        assertThat(other.storyboardSha256()).isNotEqualTo(first.storyboardSha256());
        assertThat(other.manifestSha256()).isNotEqualTo(first.manifestSha256());
    }

    @Test
    void maliciousTitleIsEscapedAndSvgHasNoExecutableMarkup() {
        var injection = new EnterpriseAdvisoryPlanningService.DesignBrief(
                brief().projectId(), brief().companyId(), brief().divisionId(),
                "<script>alert('attack')</script> & \" double quote",
                brief().acceptanceCriteria());
        var bundle = generator.create(injection);
        assertThat(bundle.storyboardSvg()).doesNotContain("<script>", "</script>");
        assertThat(bundle.storyboardSvg()).contains("&lt;script&gt;", "&amp;", "&quot;");
        assertThat(verifier.verify(bundle).independentlyVerifiedVisualQuality()).isFalse();
    }

    @Test
    void independentVerifierDetectsBytesTamperedWithoutUpdatingDigest() {
        var bundle = generator.create(brief());
        var forged = replace(bundle, bundle.storyboardSvg().replace(
                "NO DESIGN AGENT EXECUTED", "DESIGN AGENT EXECUTED"),
                bundle.manifestJson(), false);
        assertThatThrownBy(() -> verifier.verify(forged))
                .hasMessage("E22_ARTIFACT_DIGEST_MISMATCH");
    }

    @Test
    void verifierRejectsInjectedScriptEvenWithRecomputedDigest() {
        var bundle = generator.create(brief());
        var unsafe = replace(bundle, bundle.storyboardSvg().replace(
                "</svg>", "<script>alert(1)</script></svg>"), bundle.manifestJson(), true);
        assertThatThrownBy(() -> verifier.verify(unsafe))
                .hasMessage("E22_SVG_ELEMENT_DISALLOWED");
    }

    @Test
    void verifierRejectsXmlExternalEntityEvenWithRecomputedDigest() {
        var bundle = generator.create(brief());
        var attack = bundle.storyboardSvg().replace(
                "<svg ", "<!DOCTYPE svg [<!ENTITY ext SYSTEM \"file:///etc/passwd\">]><svg ");
        var unsafe = replace(bundle, attack, bundle.manifestJson(), true);
        assertThatThrownBy(() -> verifier.verify(unsafe))
                .hasMessage("E22_SVG_INVALID_OR_UNSAFE");
    }

    @Test
    void verifierRejectsFakeApprovalInManifestEvenWithMatchingDigest() throws Exception {
        var bundle = generator.create(brief());
        ObjectNode content = (ObjectNode) mapper.readTree(bundle.manifestJson());
        content.put("ownerApprovalGranted", true);
        var forged = replace(bundle, bundle.storyboardSvg(), mapper.writeValueAsString(content), true);
        assertThatThrownBy(() -> verifier.verify(forged))
                .hasMessage("E22_MANIFEST_AUTHORITY_CLAIM");
    }

    @Test
    void verifierRejectsFakeCompletionByPlannedAgent() throws Exception {
        var bundle = generator.create(brief());
        ObjectNode content = (ObjectNode) mapper.readTree(bundle.manifestJson());
        ((ObjectNode) content.path("handoffCandidates").get(0)).put("executed", true);
        var forged = replace(bundle, bundle.storyboardSvg(), mapper.writeValueAsString(content), true);
        assertThatThrownBy(() -> verifier.verify(forged))
                .hasMessage("E22_MANIFEST_FALSE_EXECUTION_CLAIM");
    }

    @Test
    void verifierRejectsExternalSvgLinkEvenIfDigestIsUpdated() {
        var bundle = generator.create(brief());
        var unsafe = replace(bundle, bundle.storyboardSvg().replace(
                "<svg ", "<svg onload=\"alert(1)\" "), bundle.manifestJson(), true);
        assertThatThrownBy(() -> verifier.verify(unsafe))
                .hasMessage("E22_SVG_ATTRIBUTE_DISALLOWED");
    }

    @Test
    void companyBoundaryAndInputValidationInheritedFromE21() {
        var wrongCompany = new EnterpriseAdvisoryPlanningService.DesignBrief(
                brief().projectId(), "aetheris-finance", brief().divisionId(),
                brief().objective(), brief().acceptanceCriteria());
        assertThatThrownBy(() -> generator.create(wrongCompany))
                .hasMessage("E2_DIVISION_NOT_OWNED_BY_COMPANY");
        var missing = new EnterpriseAdvisoryPlanningService.DesignBrief(
                brief().projectId(), brief().companyId(), brief().divisionId(),
                brief().objective(), List.of());
        assertThatThrownBy(() -> generator.create(missing))
                .hasMessage("E2_ACCEPTANCE_CRITERIA_INVALID");
    }

    @Test
    void physicalArtifactBytesCanBeWrittenAndReverified(@TempDir Path temp) throws Exception {
        var result = generator.create(brief());
        Path svg = temp.resolve("storyboard.svg");
        Path json = temp.resolve("motion-contract.json");
        Files.writeString(svg, result.storyboardSvg(), StandardCharsets.UTF_8);
        Files.writeString(json, result.manifestJson(), StandardCharsets.UTF_8);
        assertThat(Files.size(svg)).isGreaterThan(1000);
        assertThat(Files.size(json)).isGreaterThan(500);
        var roundtrip = replace(result,
                Files.readString(svg, StandardCharsets.UTF_8),
                Files.readString(json, StandardCharsets.UTF_8), false);
        assertThat(verifier.verify(roundtrip).verificationClass())
                .isEqualTo("VERIFIED_STATIC_ARTIFACT_FORMAT_ONLY");
    }

    @Test
    void generateSanitizedEvidenceFilesForReview() throws Exception {
        var result = generator.create(brief());
        verifier.verify(result);
        Path dir = Path.of("target", "e22-design-evidence");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("cinematic-device-storyboard.svg"), result.storyboardSvg(),
                StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("cinematic-device-motion-contract.json"),
                result.manifestJson(), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("SHA256SUMS"),
                result.storyboardSha256() + "  cinematic-device-storyboard.svg\n"
                + result.manifestSha256() + "  cinematic-device-motion-contract.json\n",
                StandardCharsets.UTF_8);
    }
}
