package io.aetheris.orchestrator.ingestion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface IngestionSourceStateRepository extends JpaRepository<IngestionSourceStateEntity,UUID>{Optional<IngestionSourceStateEntity> findBySourceKindAndSourceIdAndNamespace(String sourceKind,String sourceId,String namespace);List<IngestionSourceStateEntity> findTop200ByOrderByUpdatedAtDesc();}
