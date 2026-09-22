package io.aetheris.orchestrator.memory;
import java.util.UUID;
public record LinkKnowledgeRequest(UUID fromNodeId,UUID toNodeId,String relation){}
