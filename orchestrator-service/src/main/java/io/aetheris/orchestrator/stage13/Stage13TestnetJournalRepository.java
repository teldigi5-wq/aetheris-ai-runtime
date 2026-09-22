package io.aetheris.orchestrator.stage13;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Stage13TestnetJournalRepository extends JpaRepository<Stage13TestnetJournalEntity, UUID> {
    Optional<Stage13TestnetJournalEntity> findByClientOrderId(String clientOrderId);
    List<Stage13TestnetJournalEntity> findTop100ByOrderByUpdatedAtDesc();
}
