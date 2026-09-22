package io.aetheris.orchestrator.syntracore;

import io.aetheris.orchestrator.ingestion.IncrementalIngestionResult;
import io.aetheris.orchestrator.ingestion.IncrementalKnowledgeIngestionService;
import io.aetheris.orchestrator.ingestion.IngestKnowledgeRequest;
import io.aetheris.orchestrator.memory.KnowledgeNodeEntity;
import io.aetheris.orchestrator.memory.MemoryScope;
import io.aetheris.orchestrator.memory.Stage9AdaptiveVectorIndexService;
import io.aetheris.orchestrator.memory.VectorMemorySearchResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class Phase13RetrievalFoundationTest {

    @Test
    void retrievalScopesCanonicalizeAndSeparateProjectsFromWorkspaces() {
        RetrievalScope project = RetrievalScope.project("Aetheris-Platform");
        RetrievalScope workspace = RetrievalScope.workspace("Aetheris-Platform");

        assertEquals(MemoryScope.PROJECT, project.memoryScope());
        assertEquals("aetheris-platform", project.scopeId());
        assertEquals("project:aetheris-platform", project.namespace());
        assertEquals(MemoryScope.WORKSPACE, workspace.memoryScope());
        assertEquals("workspace:aetheris-platform", workspace.namespace());
        assertFalse(project.namespace().equals(workspace.namespace()));

        assertThrows(
                IllegalArgumentException.class,
                () -> new RetrievalScope(MemoryScope.PERSONAL, "owner"));
        assertThrows(
                IllegalArgumentException.class,
                () -> RetrievalScope.project("../escape"));
    }

    @Test
    void ingestionReusesIncrementalPipelineWithExactScopeNamespace() {
        IncrementalKnowledgeIngestionService ingestion = mock(IncrementalKnowledgeIngestionService.class);
        Stage9AdaptiveVectorIndexService index = mock(Stage9AdaptiveVectorIndexService.class);
        SyntraRetrievalService service = new SyntraRetrievalService(ingestion, index);
        RetrievalScope scope = RetrievalScope.project("aetheris-platform");
        UUID nodeId = UUID.randomUUID();

        when(ingestion.sync(any())).thenReturn(new IncrementalIngestionResult(
                "DOCUMENT",
                "architecture-spec",
                scope.namespace(),
                4,
                true,
                false,
                1,
                List.of(nodeId),
                "indexed"));

        RetrievalIngestionReceipt receipt = service.ingest(
                scope,
                new RetrievalDocument(
                        "document",
                        "architecture-spec",
                        "Architecture",
                        "Aetheris uses scoped retrieval evidence.",
                        false,
                        Set.of("architecture")));

        ArgumentCaptor<IngestKnowledgeRequest> request = ArgumentCaptor.forClass(IngestKnowledgeRequest.class);
        verify(ingestion).sync(request.capture());
        assertEquals(MemoryScope.PROJECT, request.getValue().scope());
        assertEquals(scope.namespace(), request.getValue().namespace());
        assertTrue(request.getValue().tags().contains("syntra-rag"));
        assertTrue(request.getValue().tags().contains("syntra-project"));
        assertEquals(4, receipt.revision());
        assertEquals(List.of(nodeId), receipt.activeNodeIds());
        verifyNoInteractions(index);
    }

    @Test
    void retrievalReturnsEvidenceAddressesAndNeverRequestsProtectedData() {
        IncrementalKnowledgeIngestionService ingestion = mock(IncrementalKnowledgeIngestionService.class);
        Stage9AdaptiveVectorIndexService index = mock(Stage9AdaptiveVectorIndexService.class);
        SyntraRetrievalService service = new SyntraRetrievalService(ingestion, index);
        RetrievalScope scope = RetrievalScope.workspace("interview-lab");
        UUID nodeId = UUID.randomUUID();
        KnowledgeNodeEntity node = new KnowledgeNodeEntity(
                nodeId,
                MemoryScope.WORKSPACE,
                scope.namespace(),
                "system-design#1",
                "Bounded retrieval keeps evidence addressable and locally scoped.",
                Set.of("rag"),
                false);

        when(index.search(
                "how is retrieval scoped?",
                MemoryScope.WORKSPACE,
                scope.namespace(),
                false,
                3)).thenReturn(List.of(new VectorMemorySearchResult(node, 0.91, "stage9 adaptive local vector cosine")));

        List<RetrievalEvidence> evidence = service.retrieve(new RetrievalQuery(
                scope,
                "how is retrieval scoped?",
                3));

        assertEquals(1, evidence.size());
        assertEquals(nodeId, evidence.getFirst().nodeId());
        assertEquals(
                "aetheris-memory://workspace/interview-lab/" + nodeId,
                evidence.getFirst().evidenceAddress());
        assertEquals("system-design#1", evidence.getFirst().memoryKey());
        assertEquals(0.91, evidence.getFirst().score());
        verify(index).search(
                "how is retrieval scoped?",
                MemoryScope.WORKSPACE,
                scope.namespace(),
                false,
                3);
        verifyNoInteractions(ingestion);
    }

    @Test
    void retrievalFailsClosedIfIndexLeaksCrossScopeOrProtectedEvidence() {
        IncrementalKnowledgeIngestionService ingestion = mock(IncrementalKnowledgeIngestionService.class);
        Stage9AdaptiveVectorIndexService index = mock(Stage9AdaptiveVectorIndexService.class);
        SyntraRetrievalService service = new SyntraRetrievalService(ingestion, index);
        RetrievalScope scope = RetrievalScope.project("alpha");

        KnowledgeNodeEntity wrongScope = new KnowledgeNodeEntity(
                UUID.randomUUID(),
                MemoryScope.PROJECT,
                "project:beta",
                "leak#1",
                "must never cross the project boundary",
                Set.of(),
                false);
        when(index.search("query", MemoryScope.PROJECT, scope.namespace(), false, 2))
                .thenReturn(List.of(new VectorMemorySearchResult(wrongScope, 0.8, "test")));

        assertThrows(
                IllegalStateException.class,
                () -> service.retrieve(new RetrievalQuery(scope, "query", 2)));

        KnowledgeNodeEntity protectedNode = new KnowledgeNodeEntity(
                UUID.randomUUID(),
                MemoryScope.PROJECT,
                scope.namespace(),
                "secret#1",
                "protected evidence",
                Set.of(),
                true);
        when(index.search("protected", MemoryScope.PROJECT, scope.namespace(), false, 1))
                .thenReturn(List.of(new VectorMemorySearchResult(protectedNode, 0.7, "test")));

        assertThrows(
                IllegalStateException.class,
                () -> service.retrieve(new RetrievalQuery(scope, "protected", 1)));
    }

    @Test
    void documentAndQueryBoundsFailBeforeStorageOrRetrievalExecution() {
        IncrementalKnowledgeIngestionService ingestion = mock(IncrementalKnowledgeIngestionService.class);
        Stage9AdaptiveVectorIndexService index = mock(Stage9AdaptiveVectorIndexService.class);
        SyntraRetrievalService service = new SyntraRetrievalService(ingestion, index);
        RetrievalScope scope = RetrievalScope.project("bounds");

        RetrievalDocument oversized = new RetrievalDocument(
                "TEXT",
                "oversized",
                "Oversized",
                "x".repeat(120_001),
                false,
                Set.of());
        assertThrows(IllegalArgumentException.class, () -> service.ingest(scope, oversized));
        verify(ingestion, never()).sync(any());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.retrieve(new RetrievalQuery(scope, "q".repeat(4_001), 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.retrieve(new RetrievalQuery(scope, "query", 21)));
        verifyNoInteractions(index);
    }

    @Test
    void tombstoneRemainsBoundToTheExactScopeNamespace() {
        IncrementalKnowledgeIngestionService ingestion = mock(IncrementalKnowledgeIngestionService.class);
        Stage9AdaptiveVectorIndexService index = mock(Stage9AdaptiveVectorIndexService.class);
        SyntraRetrievalService service = new SyntraRetrievalService(ingestion, index);
        RetrievalScope scope = RetrievalScope.workspace("docs");

        when(ingestion.tombstone("TEXT", "owner-notes", scope.namespace()))
                .thenReturn(new IncrementalIngestionResult(
                        "TEXT",
                        "owner-notes",
                        scope.namespace(),
                        3,
                        true,
                        true,
                        0,
                        List.of(),
                        "tombstoned"));

        RetrievalIngestionReceipt receipt = service.tombstone(scope, "text", "owner-notes");

        assertTrue(receipt.tombstoned());
        verify(ingestion).tombstone("TEXT", "owner-notes", scope.namespace());
        verifyNoInteractions(index);
    }
}
