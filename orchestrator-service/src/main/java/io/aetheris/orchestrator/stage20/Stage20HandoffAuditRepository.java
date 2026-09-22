package io.aetheris.orchestrator.stage20;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface Stage20HandoffAuditRepository extends JpaRepository<Stage20HandoffAuditEntity, UUID> {
    List<Stage20HandoffAuditEntity> findTop200ByOrderByObservedAtDesc();
    List<Stage20HandoffAuditEntity> findTop100ByAuthorizationIdOrderByObservedAtDesc(UUID authorizationId);
}
