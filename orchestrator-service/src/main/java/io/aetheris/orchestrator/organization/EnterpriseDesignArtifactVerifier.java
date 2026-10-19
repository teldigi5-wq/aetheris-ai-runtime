package io.aetheris.orchestrator.organization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

import org.xml.sax.InputSource;

/**
 * An independently coded verifier for the STATIC E2.2A artifact format.
 * Validates exact SHA256 byte content, manifest scope/status, secure SVG parsing
 * and a deliberately narrow SVG element/attribute allowlist.
 * A matching digest is NOT authentication, a signature, or proof of design quality.
 */
@Service
public final class EnterpriseDesignArtifactVerifier {
    private static final Set<String> ELEMENTS = Set.of(
            "svg", "title", "desc", "rect", "text", "path");
    private static final Set<String> ATTRIBUTES = Set.of(
            "xmlns", "width", "height", "viewBox", "role", "aria-label",
            "x", "y", "rx", "fill", "font-size", "font-weight", "d");
    private static final String EVIDENCE = "STATIC_DESIGN_ARTIFACTS_NOT_EXECUTED";
    private final ObjectMapper mapper;

    public EnterpriseDesignArtifactVerifier(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public VerificationResult verify(EnterpriseDesignArtifactService.ArtifactBundle artifact) {
        check(artifact != null, "E22_ARTIFACT_MISSING");
        check(artifact.schemaVersion() == 1 && EVIDENCE.equals(artifact.evidenceClass()),
                "E22_ARTIFACT_EVIDENCE_INVALID");
        check(!artifact.executionAuthorized() && !artifact.designAgentsExecuted()
                && !artifact.websiteDelivered() && !artifact.productionApproved(),
                "E22_ARTIFACT_UNAUTHORIZED_CLAIM");
        check(artifact.storyboardSvg() != null && !artifact.storyboardSvg().isBlank()
                && artifact.storyboardSvg().length() <= 30000
                && artifact.manifestJson() != null && !artifact.manifestJson().isBlank()
                && artifact.manifestJson().length() <= 30000, "E22_ARTIFACT_SIZE_INVALID");
        check(digest(artifact.storyboardSvg()).equals(artifact.storyboardSha256())
                && digest(artifact.manifestJson()).equals(artifact.manifestSha256()),
                "E22_ARTIFACT_DIGEST_MISMATCH");

        final JsonNode manifest;
        try {
            manifest = mapper.readTree(artifact.manifestJson());
        } catch (Exception ex) {
            throw new IllegalArgumentException("E22_MANIFEST_JSON_INVALID", ex);
        }
        check(manifest != null && manifest.path("schemaVersion").asInt(-1) == 1
                && EVIDENCE.equals(manifest.path("evidenceClass").asText())
                && artifact.projectId().equals(manifest.path("projectId").asText())
                && artifact.companyId().equals(manifest.path("companyId").asText())
                && artifact.divisionId().equals(manifest.path("divisionId").asText())
                && artifact.briefFingerprint().equals(manifest.path("briefFingerprint").asText()),
                "E22_MANIFEST_SCOPE_MISMATCH");
        check(!manifest.path("rendered3dAssetsPresent").asBoolean(true)
                && !manifest.path("websiteBuilt").asBoolean(true)
                && !manifest.path("automatedTestsExecuted").asBoolean(true)
                && !manifest.path("ownerApprovalGranted").asBoolean(true),
                "E22_MANIFEST_AUTHORITY_CLAIM");
        var scenes = manifest.path("scenes");
        check(scenes.isArray() && scenes.size() == 3
                && "hero".equals(scenes.get(0).path("id").asText())
                && "scroll-reveal".equals(scenes.get(1).path("id").asText())
                && scenes.get(1).path("plannedFrameStart").asInt(-1) == 0
                && scenes.get(1).path("plannedFrameEnd").asInt(-1) == 119
                && "accessible-fallback".equals(scenes.get(2).path("id").asText()),
                "E22_MANIFEST_SCENE_CONTRACT_INVALID");
        var gates = manifest.path("qualityGates");
        check(gates.isArray() && gates.size() >= 5,
                "E22_MANIFEST_QA_GATES_MISSING");
        var handoffs = manifest.path("handoffCandidates");
        check(handoffs.isArray() && handoffs.size() == 7,
                "E22_MANIFEST_HANDOFFS_MISSING");
        for (JsonNode handoff : handoffs) {
            check(!handoff.path("executed").asBoolean(true)
                    && handoff.path("roleId").isTextual(),
                    "E22_MANIFEST_FALSE_EXECUTION_CLAIM");
        }

        int elements = parseSvgAndVerify(artifact.storyboardSvg());
        return new VerificationResult("VERIFIED_STATIC_ARTIFACT_FORMAT_ONLY",
                artifact.projectId(), artifact.storyboardSha256(),
                artifact.manifestSha256(), elements, false, false);
    }

    private int parseSvgAndVerify(String svg) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void fatalError(org.xml.sax.SAXParseException exception)
                        throws org.xml.sax.SAXException { throw exception; }
            });
            var xml = builder.parse(new InputSource(new StringReader(svg)));
            Node root = xml.getDocumentElement();
            check("svg".equals(root.getLocalName())
                    && "http://www.w3.org/2000/svg".equals(root.getNamespaceURI()),
                    "E22_SVG_ROOT_INVALID");
            NodeList items = xml.getElementsByTagName("*");
            int panels = 0;
            for (int i = 0; i < items.getLength(); i++) {
                Node element = items.item(i);
                String local = element.getLocalName();
                check(ELEMENTS.contains(local) && "http://www.w3.org/2000/svg".equals(
                        element.getNamespaceURI()), "E22_SVG_ELEMENT_DISALLOWED");
                if ("rect".equals(local)) panels++;
                NamedNodeMap attrs = element.getAttributes();
                for (int j = 0; j < attrs.getLength(); j++) {
                    Node attr = attrs.item(j);
                    check(ATTRIBUTES.contains(attr.getNodeName()),
                            "E22_SVG_ATTRIBUTE_DISALLOWED");
                }
            }
            check(panels >= 6 && items.getLength() >= 16, "E22_SVG_STORYBOARD_INCOMPLETE");
            return items.getLength();
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("E22_SVG_INVALID_OR_UNSAFE", ex);
        }
    }

    private static String digest(String text) {
        try {
            byte[] sum = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sum);
        } catch (Exception ex) {
            throw new IllegalStateException("E22_DIGEST_UNAVAILABLE", ex);
        }
    }

    private static void check(boolean condition, String code) {
        if (!condition) throw new IllegalArgumentException(code);
    }

    public record VerificationResult(String verificationClass, String projectId,
            String storyboardSha256, String manifestSha256, int svgElementCount,
            boolean authenticatedOwnerApproval, boolean independentlyVerifiedVisualQuality) {}
}
