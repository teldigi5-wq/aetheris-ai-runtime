package io.aetheris.orchestrator.stage13;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface Stage13ProviderHealthEvidenceRepository extends JpaRepository<Stage13ProviderHealthEvidenceEntity, UUID> {
    List<Stage13ProviderHealthEvidenceEntity> findTop50ByProviderIdOrderByObservedAtDesc(String providerId);
    List<Stage13ProviderHealthEvidenceEntity> findTop200ByOrderByObservedAtDesc();
}
