package io.aetheris.orchestrator.memory;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
@Entity
@Table(name="aetheris_knowledge_edges")
public class KnowledgeEdgeEntity {
 @Id private UUID id; @Column(nullable=false) private UUID fromNodeId; @Column(nullable=false) private UUID toNodeId; @Column(nullable=false,length=120) private String relation; @Column(nullable=false) private Instant createdAt;
 protected KnowledgeEdgeEntity(){} public KnowledgeEdgeEntity(UUID id,UUID from,UUID to,String relation){if(from.equals(to))throw new IllegalArgumentException("Knowledge edge cannot self-link");this.id=id;this.fromNodeId=from;this.toNodeId=to;this.relation=relation==null||relation.isBlank()?"RELATED_TO":relation.trim().toUpperCase(Locale.ROOT);this.createdAt=Instant.now();}
 public UUID getId(){return id;} public UUID getFromNodeId(){return fromNodeId;} public UUID getToNodeId(){return toNodeId;} public String getRelation(){return relation;} public Instant getCreatedAt(){return createdAt;}
}
