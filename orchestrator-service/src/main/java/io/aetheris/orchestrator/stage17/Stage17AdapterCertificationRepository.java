package io.aetheris.orchestrator.stage17;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface Stage17AdapterCertificationRepository extends JpaRepository<Stage17AdapterCertificationEntity, UUID> {
    List<Stage17AdapterCertificationEntity> findTop100ByOrderByCertifiedAtDesc();
    List<Stage17AdapterCertificationEntity> findTop20ByAdapterIdOrderByCertifiedAtDesc(String adapterId);
}
