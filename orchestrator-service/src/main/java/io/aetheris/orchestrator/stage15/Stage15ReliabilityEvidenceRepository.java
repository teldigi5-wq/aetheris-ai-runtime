package io.aetheris.orchestrator.stage15;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface Stage15ReliabilityEvidenceRepository extends JpaRepository<Stage15ReliabilityEvidenceEntity, UUID> {
    List<Stage15ReliabilityEvidenceEntity> findTop500ByServiceIdAndObservedAtAfterOrderByObservedAtDesc(String serviceId, Instant cutoff);
}
