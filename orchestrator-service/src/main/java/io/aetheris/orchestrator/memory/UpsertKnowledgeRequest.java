package io.aetheris.orchestrator.memory;
import java.util.Set;
public record UpsertKnowledgeRequest(MemoryScope scope,String namespace,String key,String content,Set<String> tags,boolean protectedData){public UpsertKnowledgeRequest{tags=tags==null?Set.of():Set.copyOf(tags);}}
