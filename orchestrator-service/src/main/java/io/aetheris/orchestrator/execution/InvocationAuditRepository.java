package io.aetheris.orchestrator.execution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InvocationAuditRepository extends JpaRepository<InvocationAuditEntity, UUID> {
    List<InvocationAuditEntity> findTop100ByOrderByStartedAtDesc();
    List<InvocationAuditEntity> findTop100ByTaskIdOrderByStartedAtDesc(UUID taskId);
}
