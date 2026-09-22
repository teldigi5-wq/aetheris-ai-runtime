package io.aetheris.orchestrator.stage15;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface Stage15ServiceCatalogRepository extends JpaRepository<Stage15ServiceCatalogEntity, String> {
    List<Stage15ServiceCatalogEntity> findTop200ByOrderByUpdatedAtDesc();
}
