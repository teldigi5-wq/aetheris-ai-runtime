package io.aetheris.orchestrator.stage17;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface Stage17AdapterManifestRepository extends JpaRepository<Stage17AdapterManifestEntity, UUID> {
    boolean existsByAdapterIdAndManifestSha256(String adapterId, String manifestSha256);
    List<Stage17AdapterManifestEntity> findTop100ByOrderByCreatedAtDesc();
    List<Stage17AdapterManifestEntity> findTop20ByAdapterIdOrderByCreatedAtDesc(String adapterId);
}
