package io.aetheris.orchestrator.memory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface MemoryVectorRepository extends JpaRepository<MemoryVectorEntity,UUID>{Optional<MemoryVectorEntity> findByKnowledgeNodeIdAndAdapterId(UUID knowledgeNodeId,String adapterId);List<MemoryVectorEntity> findTop1000ByAdapterIdOrderByUpdatedAtDesc(String adapterId);}
