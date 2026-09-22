package io.aetheris.orchestrator.stage18;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface Stage18BootstrapManifestRepository extends JpaRepository<Stage18BootstrapManifestEntity, UUID> {
    List<Stage18BootstrapManifestEntity> findTop50ByOrderByCreatedAtDesc();
    List<Stage18BootstrapManifestEntity> findTop20ByTargetIdOrderByCreatedAtDesc(String targetId);
    boolean existsByTargetIdAndManifestSha256(String targetId, String manifestSha256);
}
