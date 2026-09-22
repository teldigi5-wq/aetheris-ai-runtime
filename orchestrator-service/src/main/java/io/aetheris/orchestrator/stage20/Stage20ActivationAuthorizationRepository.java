package io.aetheris.orchestrator.stage20;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface Stage20ActivationAuthorizationRepository extends JpaRepository<Stage20ActivationAuthorizationEntity, UUID> {
    boolean existsByAuthorizationSha256(String authorizationSha256);
    List<Stage20ActivationAuthorizationEntity> findTop100ByOrderByCreatedAtDesc();
}
