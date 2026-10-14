package io.aetheris.orchestrator.execution;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SyntheticAgentClaimRepository
        extends JpaRepository<SyntheticAgentClaimEntity, UUID> {}
