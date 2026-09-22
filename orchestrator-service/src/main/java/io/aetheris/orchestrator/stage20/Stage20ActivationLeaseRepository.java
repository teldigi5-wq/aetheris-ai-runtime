package io.aetheris.orchestrator.stage20;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface Stage20ActivationLeaseRepository extends JpaRepository<Stage20ActivationLeaseEntity, UUID> {
    List<Stage20ActivationLeaseEntity> findTop100ByOrderByStartedAtDesc();
    List<Stage20ActivationLeaseEntity> findTop50ByAuthorizationIdOrderByStartedAtDesc(UUID authorizationId);
}
