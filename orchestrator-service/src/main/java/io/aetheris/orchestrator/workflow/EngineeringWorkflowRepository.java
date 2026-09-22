package io.aetheris.orchestrator.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EngineeringWorkflowRepository extends JpaRepository<EngineeringWorkflowEntity, UUID> {
    List<EngineeringWorkflowEntity> findTop50ByOrderByUpdatedAtDesc();
}
