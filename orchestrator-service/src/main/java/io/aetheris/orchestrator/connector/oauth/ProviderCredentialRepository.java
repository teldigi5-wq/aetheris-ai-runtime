package io.aetheris.orchestrator.connector.oauth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProviderCredentialRepository extends JpaRepository<ProviderCredentialEntity, UUID> {
    Optional<ProviderCredentialEntity> findByConnectionId(UUID connectionId);
}
