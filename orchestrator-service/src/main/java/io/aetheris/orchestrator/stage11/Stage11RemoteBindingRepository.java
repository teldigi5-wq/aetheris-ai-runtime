package io.aetheris.orchestrator.stage11;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface Stage11RemoteBindingRepository extends JpaRepository<Stage11RemoteBindingEntity, UUID> {}
