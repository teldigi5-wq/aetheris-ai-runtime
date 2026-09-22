package io.aetheris.orchestrator.stage20;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface Stage20TargetReceiptRepository extends JpaRepository<Stage20TargetReceiptEntity, UUID> {
    List<Stage20TargetReceiptEntity> findTop200ByOrderByObservedAtDesc();
    List<Stage20TargetReceiptEntity> findTop100ByAuthorizationIdOrderByObservedAtDesc(UUID authorizationId);
    List<Stage20TargetReceiptEntity> findTop100ByLeaseIdOrderByObservedAtDesc(UUID leaseId);
}
