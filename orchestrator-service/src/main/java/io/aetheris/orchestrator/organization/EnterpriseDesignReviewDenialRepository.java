package io.aetheris.orchestrator.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/** Persist denial evidence, not a substitute for an immutable security log. */
interface EnterpriseDesignReviewDenialRepository extends JpaRepository<EnterpriseDesignReviewDenial, Long> {
    List<EnterpriseDesignReviewDenial> findByIntentIdOrderByIdAsc(String intentId);
}
