package io.aetheris.orchestrator.stage19;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface Stage19ActivationProposalRepository extends JpaRepository<Stage19ActivationProposalEntity, UUID> {
    List<Stage19ActivationProposalEntity> findTop100ByOrderByCreatedAtDesc();
    List<Stage19ActivationProposalEntity> findTop50ByTargetIdOrderByCreatedAtDesc(String targetId);
    boolean existsByProposalSha256(String proposalSha256);
}
