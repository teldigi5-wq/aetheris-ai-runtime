package io.aetheris.orchestrator.host;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface HostPairingCredentialRepository extends JpaRepository<HostPairingCredentialEntity, UUID> { Optional<HostPairingCredentialEntity> findByHostId(UUID hostId); }
