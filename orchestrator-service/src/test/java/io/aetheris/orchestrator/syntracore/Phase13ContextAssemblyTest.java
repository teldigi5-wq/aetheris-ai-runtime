package io.aetheris.orchestrator.syntracore;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Phase13ContextAssemblyTest {
    private static final Instant ASSEMBLED_AT = Instant.parse("2026-09-20T10:00:00Z");

    @Test
    void assemblyDeduplicatesRanksAndCarriesExactCitationsIntoModelInvocation() {
        SyntraRetrievalService retrieval = mock(SyntraRetrievalService.class);
        SyntraContextAssemblyService service = new SyntraContextAssemblyService(
                retrieval,
                Clock.fixed(ASSEMBLED_AT, ZoneOffset.UTC));
        RetrievalScope scope = RetrievalScope.project("aetheris-platform");
        UUID firstNode = UUID.randomUUID();
        UUID secondNode = UUID.randomUUID();

        RetrievalEvidence weakerDuplicate = evidence(
                scope,
                firstNode,
                "router#1",
                "The router evaluates capability and execution constraints.",
                0.61,
                Instant.parse("2026-09-19T08:00:00Z"));
        RetrievalEvidence strongest = evidence(
                scope,
                firstNode,
                "router#1",
                "The router evaluates capability, hardware evidence, and local fallback constraints.",
                0.94,
                Instant.parse("2026-09-20T08:00:00Z"));
        RetrievalEvidence second = evidence(
                scope,
                secondNode,
                "runtime#2",
                "Local execution remains bounded and cancelable.",
                0.82,
                Instant.parse("2026-09-20T09:00:00Z"));

        RetrievalQuery expectedQuery = new RetrievalQuery(scope, "how is local routing bounded?", 5);
        when(retrieval.retrieve(expectedQuery)).thenReturn(List.of(weakerDuplicate, second, strongest));

        ContextPack pack = service.assemble(new ContextAssemblyRequest(
                scope,
                "how is local routing bounded?",
                5,
                new ContextBudget(4_000, 2_000, 4)));

        assertEquals(2, pack.evidence().size());
        assertEquals(firstNode, pack.evidence().get(0).source().nodeId());
        assertEquals(secondNode, pack.evidence().get(1).source().nodeId());
        assertEquals(List.of(strongest.evidenceAddress(), second.evidenceAddress()), pack.citations());
        assertEquals(3, pack.quality().candidateCount());
        assertEquals(2, pack.quality().uniqueCandidateCount());
        assertEquals(1, pack.quality().duplicateCount());
        assertEquals(0, pack.quality().budgetOmittedCount());
        assertEquals(ASSEMBLED_AT, pack.assembledAt());
        assertTrue(pack.estimatedContextTokens() <= 4_000);
        verify(retrieval).retrieve(expectedQuery);

        ModelInvocation invocation = pack.toModelInvocation(
                "qwen2.5-coder:7b",
                "Explain the routing boundary.",
                512);

        assertEquals(pack.citations(), invocation.evidenceAddresses());
        assertTrue(invocation.input().contains("untrusted reference data"));
        assertTrue(invocation.input().contains(strongest.evidenceAddress()));
        assertTrue(invocation.input().contains(second.evidenceAddress()));
        assertTrue(invocation.input().contains("USER_INPUT"));

        ModelInvocation legacy = new ModelInvocation("small-local ", "hello", 32);
        assertEquals("small-local ", legacy.modelId());
        assertTrue(legacy.evidenceAddresses().isEmpty());
    }

    @Test
    void equalRelevanceUsesNewestEvidenceAsDeterministicTieBreaker() {
        SyntraRetrievalService retrieval = mock(SyntraRetrievalService.class);
        SyntraContextAssemblyService service = new SyntraContextAssemblyService(
                retrieval,
                Clock.fixed(ASSEMBLED_AT, ZoneOffset.UTC));
        RetrievalScope scope = RetrievalScope.workspace("design-lab");

        RetrievalEvidence older = evidence(
                scope,
                UUID.randomUUID(),
                "older#1",
                "Older evidence.",
                0.75,
                Instant.parse("2026-09-18T00:00:00Z"));
        RetrievalEvidence newer = evidence(
                scope,
                UUID.randomUUID(),
                "newer#1",
                "Newer evidence.",
                0.75,
                Instant.parse("2026-09-20T00:00:00Z"));

        when(retrieval.retrieve(new RetrievalQuery(scope, "same score", 2)))
                .thenReturn(List.of(older, newer));

        ContextPack pack = service.assemble(new ContextAssemblyRequest(
                scope,
                "same score",
                2,
                new ContextBudget(2_000, 1_000, 2)));

        assertEquals(newer.nodeId(), pack.evidence().getFirst().source().nodeId());
        assertEquals(older.nodeId(), pack.evidence().getLast().source().nodeId());
    }

    @Test
    void contextBudgetTruncatesEvidenceAndOmitsBeyondItemLimit() {
        SyntraRetrievalService retrieval = mock(SyntraRetrievalService.class);
        SyntraContextAssemblyService service = new SyntraContextAssemblyService(
                retrieval,
                Clock.fixed(ASSEMBLED_AT, ZoneOffset.UTC));
        RetrievalScope scope = RetrievalScope.project("budget-test");

        RetrievalEvidence first = evidence(
                scope,
                UUID.randomUUID(),
                "large#1",
                "A".repeat(2_000),
                0.9,
                Instant.parse("2026-09-20T09:00:00Z"));
        RetrievalEvidence second = evidence(
                scope,
                UUID.randomUUID(),
                "large#2",
                "B".repeat(2_000),
                0.8,
                Instant.parse("2026-09-20T09:30:00Z"));

        when(retrieval.retrieve(new RetrievalQuery(scope, "budget", 2)))
                .thenReturn(List.of(first, second));

        ContextPack pack = service.assemble(new ContextAssemblyRequest(
                scope,
                "budget",
                2,
                new ContextBudget(700, 700, 1)));

        assertEquals(1, pack.evidence().size());
        assertEquals(first.nodeId(), pack.evidence().getFirst().source().nodeId());
        assertTrue(pack.evidence().getFirst().excerpt().endsWith("…"));
        assertTrue(pack.estimatedContextTokens() <= 700);
        assertEquals(1, pack.quality().budgetOmittedCount());
        assertEquals(0.5d, pack.quality().selectionCoverage());
    }

    @Test
    void assemblyFailsClosedOnCrossScopeOrForgedEvidenceIdentity() {
        SyntraRetrievalService retrieval = mock(SyntraRetrievalService.class);
        SyntraContextAssemblyService service = new SyntraContextAssemblyService(
                retrieval,
                Clock.fixed(ASSEMBLED_AT, ZoneOffset.UTC));
        RetrievalScope requested = RetrievalScope.project("alpha");
        RetrievalScope other = RetrievalScope.project("beta");
        UUID otherId = UUID.randomUUID();

        RetrievalEvidence crossScope = evidence(
                other,
                otherId,
                "cross#1",
                "Must not cross project boundaries.",
                0.9,
                Instant.parse("2026-09-20T09:00:00Z"));
        when(retrieval.retrieve(new RetrievalQuery(requested, "cross scope", 1)))
                .thenReturn(List.of(crossScope));

        assertThrows(
                IllegalStateException.class,
                () -> service.assemble(new ContextAssemblyRequest(
                        requested,
                        "cross scope",
                        1,
                        ContextBudget.safeDefaults())));

        UUID forgedId = UUID.randomUUID();
        RetrievalEvidence forged = new RetrievalEvidence(
                "aetheris-memory://project/alpha/" + UUID.randomUUID(),
                forgedId,
                requested,
                "forged#1",
                "Forged address.",
                0.8,
                "test",
                Instant.parse("2026-09-20T09:00:00Z"));
        when(retrieval.retrieve(new RetrievalQuery(requested, "forged", 1)))
                .thenReturn(List.of(forged));

        assertThrows(
                IllegalStateException.class,
                () -> service.assemble(new ContextAssemblyRequest(
                        requested,
                        "forged",
                        1,
                        ContextBudget.safeDefaults())));
    }

    @Test
    void emptyRetrievalProducesExplicitEmptyContextWithoutInventingCitations() {
        SyntraRetrievalService retrieval = mock(SyntraRetrievalService.class);
        SyntraContextAssemblyService service = new SyntraContextAssemblyService(
                retrieval,
                Clock.fixed(ASSEMBLED_AT, ZoneOffset.UTC));
        RetrievalScope scope = RetrievalScope.workspace("empty-lab");

        when(retrieval.retrieve(new RetrievalQuery(scope, "nothing known", 3)))
                .thenReturn(List.of());

        ContextPack pack = service.assemble(ContextAssemblyRequest.safeDefaults(scope, "nothing known"));

        assertTrue(pack.evidence().isEmpty());
        assertTrue(pack.citations().isEmpty());
        assertEquals(0, pack.estimatedContextTokens());
        assertEquals(1d, pack.quality().selectionCoverage());

        ModelInvocation invocation = pack.toModelInvocation("small-local", "Answer carefully.", 64);
        assertTrue(invocation.evidenceAddresses().isEmpty());
        assertTrue(invocation.input().contains("NO_RETRIEVED_EVIDENCE"));
    }

    private static RetrievalEvidence evidence(
            RetrievalScope scope,
            UUID nodeId,
            String memoryKey,
            String excerpt,
            double score,
            Instant updatedAt) {
        return new RetrievalEvidence(
                "aetheris-memory://" + scope.kind() + "/" + scope.scopeId() + "/" + nodeId,
                nodeId,
                scope,
                memoryKey,
                excerpt,
                score,
                "phase13-test",
                updatedAt);
    }
}
