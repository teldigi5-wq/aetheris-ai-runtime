package io.aetheris.orchestrator.execution;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SandboxStopDispatchRepository extends JpaRepository<SandboxStopDispatchEntity, String> {
}
