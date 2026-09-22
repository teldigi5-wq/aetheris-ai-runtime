package io.aetheris.orchestrator.memory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface KnowledgeEdgeRepository extends JpaRepository<KnowledgeEdgeEntity,UUID>{List<KnowledgeEdgeEntity> findTop500ByOrderByCreatedAtDesc();}
