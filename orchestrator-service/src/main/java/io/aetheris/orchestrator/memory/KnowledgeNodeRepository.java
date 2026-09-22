package io.aetheris.orchestrator.memory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface KnowledgeNodeRepository extends JpaRepository<KnowledgeNodeEntity,UUID>{
    Optional<KnowledgeNodeEntity> findByScopeAndNamespaceAndMemoryKey(MemoryScope scope,String namespace,String memoryKey);
    List<KnowledgeNodeEntity> findTop500ByOrderByUpdatedAtDesc();
}
