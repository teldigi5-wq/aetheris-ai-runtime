package io.aetheris.orchestrator.model;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ModelArenaMeasurementRepository extends JpaRepository<ModelArenaMeasurementEntity,UUID>{
    List<ModelArenaMeasurementEntity> findTop100ByOrderByCreatedAtDesc();
    List<ModelArenaMeasurementEntity> findTop50ByProviderIdOrderByCreatedAtDesc(String providerId);
}
