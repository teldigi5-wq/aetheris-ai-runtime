package io.aetheris.orchestrator.stage20;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface Stage20DeviceChallengeRepository extends JpaRepository<Stage20DeviceChallengeEntity, UUID> {
    List<Stage20DeviceChallengeEntity> findTop100ByOrderByIssuedAtDesc();
    List<Stage20DeviceChallengeEntity> findTop50ByAuthorizationIdOrderByIssuedAtDesc(UUID authorizationId);
}
