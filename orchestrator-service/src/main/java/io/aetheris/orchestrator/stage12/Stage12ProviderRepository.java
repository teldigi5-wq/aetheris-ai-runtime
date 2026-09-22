package io.aetheris.orchestrator.stage12;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface Stage12ProviderRepository extends JpaRepository<Stage12ProviderEntity, UUID> {
    Optional<Stage12ProviderEntity> findByProviderId(String providerId);
}
