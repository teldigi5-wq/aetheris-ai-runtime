package io.aetheris.orchestrator.organization;

import org.springframework.data.jpa.repository.JpaRepository;

/** Internal only; no endpoint or executor consumes this repository. */
interface EnterpriseDesignReviewIntentRepository extends JpaRepository<EnterpriseDesignReviewIntent, String> {}
