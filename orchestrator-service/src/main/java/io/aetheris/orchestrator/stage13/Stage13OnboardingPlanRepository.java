package io.aetheris.orchestrator.stage13;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface Stage13OnboardingPlanRepository extends JpaRepository<Stage13OnboardingPlanEntity, UUID> {
    List<Stage13OnboardingPlanEntity> findTop100ByOrderByUpdatedAtDesc();
}
