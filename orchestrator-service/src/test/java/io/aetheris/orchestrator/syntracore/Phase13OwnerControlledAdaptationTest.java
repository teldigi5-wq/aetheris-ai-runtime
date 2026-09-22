package io.aetheris.orchestrator.syntracore;

import io.aetheris.orchestrator.ingestion.IncrementalKnowledgeIngestionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase13OwnerControlledAdaptationTest {

    private final AdaptationDatasetBuilder builder = new AdaptationDatasetBuilder();
    private final SyntraAdaptationPlanningService planning = new SyntraAdaptationPlanningService();

    @Test
    void datasetManifestIsDeterministicAcrossSourceOrderAndCarriesRightsProvenance() {
        AdaptationSourceDocument alpha = source(
                "alpha",
                "Explain deterministic Java records.",
                AdaptationRightsBasis.OWNER_AUTHORED,
                "owner-declaration://alpha",
                "workspace://notes/alpha",
                false,
                Set.of("training-opt-in"));
        AdaptationSourceDocument beta = source(
                "beta",
                "Use bounded local inference and preserve citations.",
                AdaptationRightsBasis.COMPATIBLE_LICENSE,
                "license://apache-2.0",
                "repository://docs/beta",
                false,
                Set.of("licensed"));

        AdaptationDatasetManifest first = builder.build(List.of(beta, alpha));
        AdaptationDatasetManifest second = builder.build(List.of(alpha, beta));

        assertEquals(AdaptationDatasetBuilder.MANIFEST_VERSION, first.manifestVersion());
        assertEquals(first.datasetHash(), second.datasetHash());
        assertEquals(List.of("alpha", "beta"), first.entries().stream()
                .map(AdaptationDatasetEntry::sourceId)
                .toList());
        assertEquals(AdaptationRightsBasis.OWNER_AUTHORED, first.entries().get(0).rightsBasis());
        assertEquals("owner-declaration://alpha", first.entries().get(0).rightsReference());
        assertEquals("workspace://notes/alpha", first.entries().get(0).provenanceReference());
    }

    @Test
    void knownSecretLikeValuesAreScrubbedAndRecordedWithoutLosingSourceHash() {
        String original = "Authorization: Bearer abcdefghijklmnopqrstuvwxyz password=hunter2";
        AdaptationDatasetManifest manifest = builder.build(List.of(source(
                "scrubbed",
                original,
                AdaptationRightsBasis.OWNER_AUTHORED,
                "owner-declaration://scrubbed",
                "workspace://notes/scrubbed",
                false,
                Set.of())));

        AdaptationDatasetEntry entry = manifest.entries().get(0);
        assertEquals(2, entry.redactionCount());
        assertEquals(2, manifest.totalRedactions());
        assertFalse(entry.sanitizedContent().contains("abcdefghijklmnopqrstuvwxyz"));
        assertFalse(entry.sanitizedContent().contains("hunter2"));
        assertTrue(entry.sanitizedContent().contains("[REDACTED_SECRET]"));
        assertNotEquals(entry.sourceContentHash(), entry.sanitizedContentHash());
    }

    @Test
    void privateKeyMaterialFailsClosedInsteadOfEnteringDataset() {
        AdaptationSourceDocument source = source(
                "private-key",
                "-----BEGIN PRIVATE KEY-----\nnot-real-key-material\n-----END PRIVATE KEY-----",
                AdaptationRightsBasis.OWNER_AUTHORED,
                "owner-declaration://private-key",
                "workspace://notes/private-key",
                false,
                Set.of());

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> builder.build(List.of(source)));

        assertTrue(failure.getMessage().contains("private key"));
    }

    @Test
    void protectedAndTombstonedSourcesAreNeverEligible() {
        AdaptationSourceDocument protectedSource = source(
                "protected",
                "private owner context",
                AdaptationRightsBasis.OWNER_AUTHORED,
                "owner-declaration://protected",
                "memory://protected",
                true,
                Set.of());
        AdaptationSourceDocument tombstonedSource = source(
                "tombstoned",
                "superseded content",
                AdaptationRightsBasis.OWNER_AUTHORED,
                "owner-declaration://tombstoned",
                "memory://tombstoned",
                false,
                Set.of(IncrementalKnowledgeIngestionService.TOMBSTONE_TAG));

        assertThrows(IllegalArgumentException.class, () -> builder.build(List.of(protectedSource)));
        assertThrows(IllegalArgumentException.class, () -> builder.build(List.of(tombstonedSource)));
    }

    @Test
    void duplicateSourceIdentityFailsClosed() {
        AdaptationSourceDocument first = source(
                "same",
                "first",
                AdaptationRightsBasis.OWNER_AUTHORED,
                "owner-declaration://same-1",
                "workspace://same-1",
                false,
                Set.of());
        AdaptationSourceDocument second = source(
                "same",
                "second",
                AdaptationRightsBasis.EXPLICIT_PERMISSION,
                "permission://same-2",
                "workspace://same-2",
                false,
                Set.of());

        assertThrows(IllegalArgumentException.class, () -> builder.build(List.of(first, second)));
    }

    @Test
    void rightsAndProvenanceMustBeExplicit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AdaptationSourceDocument(
                        "missing-rights",
                        "content",
                        AdaptationRightsBasis.OWNER_AUTHORED,
                        " ",
                        "workspace://source",
                        false,
                        Set.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AdaptationSourceDocument(
                        "missing-provenance",
                        "content",
                        AdaptationRightsBasis.PUBLIC_DOMAIN,
                        "public-domain://declaration",
                        " ",
                        false,
                        Set.of()));
    }

    @Test
    void adaptationPlanRequiresOwnerOptInAndCannotAuthorizeTraining() {
        AdaptationDatasetManifest dataset = builder.build(List.of(source(
                "plan-source",
                "Owner-controlled example data.",
                AdaptationRightsBasis.OWNER_AUTHORED,
                "owner-declaration://plan-source",
                "workspace://plan-source",
                false,
                Set.of())));

        AdaptationExperimentRequest notOptedIn = new AdaptationExperimentRequest(
                "experiment-1",
                AdaptationMethod.QLORA,
                "qwen2.5-coder:7b",
                "owner-candidate",
                false);
        assertThrows(IllegalArgumentException.class, () -> planning.plan(notOptedIn, dataset));

        AdaptationExperimentPlan plan = planning.plan(
                new AdaptationExperimentRequest(
                        "experiment-1",
                        AdaptationMethod.QLORA,
                        "qwen2.5-coder:7b",
                        "owner-candidate",
                        true),
                dataset);

        assertEquals(AdaptationMethod.QLORA, plan.method());
        assertEquals(dataset.datasetHash(), plan.datasetHash());
        assertTrue(plan.ownerOptIn());
        assertTrue(plan.localOnly());
        assertFalse(plan.baseWeightsMutable());
        assertFalse(plan.executionAuthorized());
        assertEquals("DETACH_ADAPTER", plan.rollbackStrategy());
        assertEquals(
                "aetheris-adapter://candidate/experiment-1/owner-candidate",
                plan.candidateAdapterAddress());
    }

    private AdaptationSourceDocument source(
            String sourceId,
            String content,
            AdaptationRightsBasis rights,
            String rightsReference,
            String provenance,
            boolean protectedData,
            Set<String> tags) {
        return new AdaptationSourceDocument(
                sourceId,
                content,
                rights,
                rightsReference,
                provenance,
                protectedData,
                tags);
    }
}
