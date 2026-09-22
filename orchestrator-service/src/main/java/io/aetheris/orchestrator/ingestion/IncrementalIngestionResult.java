package io.aetheris.orchestrator.ingestion;
import java.util.List;
import java.util.UUID;
public record IncrementalIngestionResult(String sourceKind,String sourceId,String namespace,int revision,boolean changed,boolean tombstoned,int chunkCount,List<UUID> activeNodeIds,String detail){}
