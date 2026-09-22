package io.aetheris.orchestrator.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VerificationEvidenceRepository extends JpaRepository<VerificationEvidenceEntity, UUID> {
    List<VerificationEvidenceEntity> findTop100ByWorkflowIdOrderByCreatedAtAsc(UUID workflowId);
}
