package io.aetheris.orchestrator.github;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GitHubChangeProposalRepository extends JpaRepository<GitHubChangeProposalEntity, UUID> {
    List<GitHubChangeProposalEntity> findTop100ByOrderByCreatedAtDesc();
}
