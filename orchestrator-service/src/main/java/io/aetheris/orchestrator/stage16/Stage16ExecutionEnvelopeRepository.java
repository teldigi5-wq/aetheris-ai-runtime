package io.aetheris.orchestrator.stage16;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface Stage16ExecutionEnvelopeRepository extends JpaRepository<Stage16ExecutionEnvelopeEntity, UUID> {
    boolean existsByNonce(String nonce);
    List<Stage16ExecutionEnvelopeEntity> findTop100ByOrderByUpdatedAtDesc();
    List<Stage16ExecutionEnvelopeEntity> findTop100ByPlanIdOrderByUpdatedAtDesc(UUID planId);
}
