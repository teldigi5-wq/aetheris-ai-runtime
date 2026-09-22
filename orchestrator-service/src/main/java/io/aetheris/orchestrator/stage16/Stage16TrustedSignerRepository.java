package io.aetheris.orchestrator.stage16;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface Stage16TrustedSignerRepository extends JpaRepository<Stage16TrustedSignerEntity, String> {
    List<Stage16TrustedSignerEntity> findTop100ByOrderByCreatedAtDesc();
}
