package io.aetheris.orchestrator.host;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface HostPairingChallengeRepository extends JpaRepository<HostPairingChallengeEntity, UUID> {}
