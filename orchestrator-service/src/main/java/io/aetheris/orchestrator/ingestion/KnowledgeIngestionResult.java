package io.aetheris.orchestrator.ingestion;
import java.util.List;
import java.util.UUID;
public record KnowledgeIngestionResult(String sourceKind,String sourceId,int chunks,List<UUID> nodeIds,String embeddingAdapter){}
