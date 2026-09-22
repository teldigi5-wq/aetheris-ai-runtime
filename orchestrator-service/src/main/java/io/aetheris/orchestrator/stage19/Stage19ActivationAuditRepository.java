package io.aetheris.orchestrator.stage19;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface Stage19ActivationAuditRepository extends JpaRepository<Stage19ActivationAuditEntity, UUID> {
    List<Stage19ActivationAuditEntity> findTop200ByOrderByObservedAtDesc();
    List<Stage19ActivationAuditEntity> findTop100ByProposalIdOrderByObservedAtDesc(UUID proposalId);
}
