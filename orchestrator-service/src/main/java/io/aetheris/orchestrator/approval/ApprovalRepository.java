package io.aetheris.orchestrator.approval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApprovalRepository extends JpaRepository<ApprovalEntity, UUID> {
    List<ApprovalEntity> findTop100ByStatusOrderByCreatedAtDesc(ApprovalStatus status);
    List<ApprovalEntity> findTop100ByTaskIdOrderByCreatedAtDesc(UUID taskId);
    long countByTaskIdAndStatus(UUID taskId, ApprovalStatus status);
    boolean existsByTaskIdAndActionTypeAndStatus(UUID taskId, String actionType, ApprovalStatus status);
}
