package io.aetheris.orchestrator.skills;

import org.springframework.data.jpa.repository.JpaRepository;

/** Internal-only durable replay evidence lookup; never public owner authority. */
interface SkillProofNonceRepository extends JpaRepository<SkillProofNonceEntity, String> {}
