package io.aetheris.orchestrator.stage17;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface Stage17MeasuredReceiptRepository extends JpaRepository<Stage17MeasuredReceiptEntity, UUID> {
    boolean existsByEnvelopeId(UUID envelopeId);
    List<Stage17MeasuredReceiptEntity> findTop100ByOrderByObservedAtDesc();
    List<Stage17MeasuredReceiptEntity> findTop50ByAdapterIdOrderByObservedAtDesc(String adapterId);
}
