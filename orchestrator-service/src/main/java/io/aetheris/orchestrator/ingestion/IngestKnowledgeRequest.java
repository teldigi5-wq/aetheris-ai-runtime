package io.aetheris.orchestrator.ingestion;
import io.aetheris.orchestrator.memory.MemoryScope;
import java.util.Set;
public record IngestKnowledgeRequest(String sourceKind,String sourceId,String title,String content,MemoryScope scope,String namespace,boolean protectedData,Set<String> tags){public IngestKnowledgeRequest{tags=tags==null?Set.of():Set.copyOf(tags);}}
