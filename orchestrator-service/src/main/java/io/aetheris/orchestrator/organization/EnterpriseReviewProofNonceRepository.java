package io.aetheris.orchestrator.organization;

import org.springframework.data.jpa.repository.JpaRepository;

/** Internal only: the database primary-key constraint is the replay arbitration. */
interface EnterpriseReviewProofNonceRepository extends JpaRepository<EnterpriseReviewProofNonce, String> {}
