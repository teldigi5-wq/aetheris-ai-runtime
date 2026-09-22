package io.aetheris.orchestrator.syntracore;

import io.aetheris.orchestrator.ingestion.IncrementalIngestionResult;
import io.aetheris.orchestrator.ingestion.IncrementalKnowledgeIngestionService;
import io.aetheris.orchestrator.ingestion.IngestKnowledgeRequest;
import io.aetheris.orchestrator.memory.KnowledgeNodeEntity;
import io.aetheris.orchestrator.memory.Stage9AdaptiveVectorIndexService;
import io.aetheris.orchestrator.memory.VectorMemorySearchResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class SyntraRetrievalService {
    private final IncrementalKnowledgeIngestionService ingestion;
    private final Stage9AdaptiveVectorIndexService adaptiveIndex;
    private final RetrievalBounds bounds;

    public SyntraRetrievalService(
            IncrementalKnowledgeIngestionService ingestion,
            Stage9AdaptiveVectorIndexService adaptiveIndex) {
        this.ingestion = Objects.requireNonNull(ingestion, "ingestion");
        this.adaptiveIndex = Objects.requireNonNull(adaptiveIndex, "adaptiveIndex");
        this.bounds = RetrievalBounds.safeDefaults();
    }

    public RetrievalIngestionReceipt ingest(RetrievalScope scope, RetrievalDocument document) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(document, "document");
        bounds.validateDocument(document);

        Set<String> tags = new LinkedHashSet<>(document.tags());
        tags.add("syntra-rag");
        tags.add("syntra-" + scope.kind());

        IncrementalIngestionResult result = ingestion.sync(new IngestKnowledgeRequest(
                document.sourceKind(),
                document.sourceId(),
                document.title(),
                document.content(),
                scope.memoryScope(),
                scope.namespace(),
                document.protectedData(),
                tags));

        validateIngestionResult(scope, document.sourceKind(), document.sourceId(), result);
        if (result.tombstoned()) {
            throw new IllegalStateException("fresh retrieval ingestion returned tombstoned source state");
        }
        return receipt(scope, result);
    }

    public RetrievalIngestionReceipt tombstone(
            RetrievalScope scope,
            String sourceKind,
            String sourceId) {
        Objects.requireNonNull(scope, "scope");
        String canonicalKind = RetrievalDocument.canonicalSourceKind(sourceKind);
        bounds.validateSourceId(sourceId);
        String canonicalSourceId = sourceId.trim();

        IncrementalIngestionResult result = ingestion.tombstone(
                canonicalKind,
                canonicalSourceId,
                scope.namespace());

        validateIngestionResult(scope, canonicalKind, canonicalSourceId, result);
        if (!result.tombstoned()) {
            throw new IllegalStateException("retrieval tombstone did not produce tombstoned source state");
        }
        return receipt(scope, result);
    }

    public List<RetrievalEvidence> retrieve(RetrievalQuery query) {
        Objects.requireNonNull(query, "query");
        bounds.validateQuery(query);

        List<VectorMemorySearchResult> results = adaptiveIndex.search(
                query.text(),
                query.scope().memoryScope(),
                query.scope().namespace(),
                false,
                query.limit());

        List<RetrievalEvidence> evidence = new ArrayList<>();
        for (VectorMemorySearchResult result : results) {
            evidence.add(toEvidence(query.scope(), result));
        }
        return List.copyOf(evidence);
    }

    private RetrievalEvidence toEvidence(RetrievalScope requestedScope, VectorMemorySearchResult result) {
        if (result == null || result.node() == null) {
            throw new IllegalStateException("retrieval index returned missing evidence node");
        }
        KnowledgeNodeEntity node = result.node();
        if (node.getScope() != requestedScope.memoryScope()
                || !node.getNamespace().equalsIgnoreCase(requestedScope.namespace())) {
            throw new IllegalStateException("retrieval index returned evidence outside requested scope");
        }
        if (node.isProtectedData()) {
            throw new IllegalStateException("retrieval index returned protected evidence to default-deny RAG path");
        }
        if (!Double.isFinite(result.score()) || result.score() < 0d) {
            throw new IllegalStateException("retrieval index returned invalid score");
        }
        if (result.method() == null || result.method().isBlank()) {
            throw new IllegalStateException("retrieval index returned missing retrieval method");
        }

        return new RetrievalEvidence(
                evidenceAddress(requestedScope, node),
                node.getId(),
                requestedScope,
                node.getMemoryKey(),
                boundedExcerpt(node.getContent()),
                result.score(),
                result.method(),
                node.getUpdatedAt());
    }

    private void validateIngestionResult(
            RetrievalScope scope,
            String sourceKind,
            String sourceId,
            IncrementalIngestionResult result) {
        if (result == null) {
            throw new IllegalStateException("retrieval ingestion returned no source state");
        }
        if (!sourceKind.equals(result.sourceKind())
                || !sourceId.equals(result.sourceId())
                || !scope.namespace().equals(result.namespace())) {
            throw new IllegalStateException("retrieval ingestion returned source state outside requested scope");
        }
    }

    private RetrievalIngestionReceipt receipt(RetrievalScope scope, IncrementalIngestionResult result) {
        return new RetrievalIngestionReceipt(
                result.sourceKind(),
                result.sourceId(),
                scope,
                result.revision(),
                result.changed(),
                result.tombstoned(),
                result.chunkCount(),
                result.activeNodeIds(),
                result.detail());
    }

    private String evidenceAddress(RetrievalScope scope, KnowledgeNodeEntity node) {
        return "aetheris-memory://" + scope.kind() + "/" + scope.scopeId() + "/" + node.getId();
    }

    private String boundedExcerpt(String content) {
        if (content.length() <= bounds.maxEvidenceCharacters()) {
            return content;
        }
        if (bounds.maxEvidenceCharacters() == 1) {
            return "…";
        }
        return content.substring(0, bounds.maxEvidenceCharacters() - 1) + "…";
    }
}
