package io.aetheris.orchestrator.connector.oauth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OAuthAuthorizationSessionRepository extends JpaRepository<OAuthAuthorizationSessionEntity, UUID> {}
