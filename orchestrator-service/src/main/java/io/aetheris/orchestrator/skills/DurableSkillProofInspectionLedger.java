package io.aetheris.orchestrator.skills;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * E3.9G-5: durable global nonce INSERT for signed candidate skill proof inspections.
 *
 * Caller MUST supply a verifier that has been independently pinned to a trusted
 * candidate public key; the ledger does NOT enroll/authenticate the signer.
 * Verifier validates signed bytes + durable active task/role/mode + default-off
 * skill policy. This code records READ-ONLY evidence only; NO permission follows.
 *
 * INSERT+flush uses the database global primary-key uniqueness constraint,
 * not JpaRepository.save(MERGE) or an existsById check-then-write race.
 * A second process with the same database cannot reserve an existing nonce.
 * Database durability/deployment and purge policy require separate certification.
 */
@Service
public class DurableSkillProofInspectionLedger {
    private final EntityManager entityManager;
    private final Clock clock;

    @Autowired
    public DurableSkillProofInspectionLedger(EntityManager entityManager) {
        this(entityManager, Clock.systemUTC());
    }

    DurableSkillProofInspectionLedger(EntityManager entityManager, Clock clock) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public RecordedInspection inspectAndRecord(CandidateSkillProofPreflight.Proof proof,
            String exactTaskSummary, CandidateSkillProofPreflight pinnedCandidateVerifier) {
        if (proof == null || exactTaskSummary == null || pinnedCandidateVerifier == null) {
            throw new IllegalArgumentException("E39G5_PROOF_AND_PINNED_VERIFIER_REQUIRED");
        }
        // Never accept a caller-manufactured Inspection object or a claimed
        // 'verified' boolean: perform the genuine Ed25519 validation here.
        CandidateSkillProofPreflight.Inspection check =
                pinnedCandidateVerifier.inspect(proof, exactTaskSummary);
        if (!check.candidateSignatureVerified() || !check.taskScopeMatched()
                || !check.reference().readOnly()
                || check.actualOwnerAuthenticated() || check.ownerApprovalGranted()
                || check.executionAuthorized() || check.durableReplayCertified()
                || check.productionReleased()
                || check.modelCalls() != 0 || check.toolCalls() != 0
                || check.reference().toolCalled() || check.reference().modelCalled()
                || !proof.taskId().equals(check.taskId())
                || !proof.agentId().equals(check.agentId())
                || !proof.skillId().equals(check.skillId())) {
            throw new IllegalStateException("E39G5_UNEXPECTED_PROOF_AUTHORITY_OR_SCOPE");
        }
        Instant seen = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (seen.isBefore(proof.issuedAt()) || !seen.isBefore(proof.expiresAt())) {
            throw new IllegalArgumentException("E39G5_EXPIRED_AT_DURABLE_RESERVATION");
        }
        SkillProofNonceEntity nonce = new SkillProofNonceEntity(
                proof.nonceHex(), check.taskId(), check.agentId(), check.skillId(),
                check.proofSha256(), seen,
                proof.expiresAt().truncatedTo(ChronoUnit.MICROS));
        entityManager.persist(nonce);
        // Force unique-key adjudication in this transaction BEFORE returning.
        // On races, DB primary key throws and rolls back: always fail closed.
        entityManager.flush();
        return new RecordedInspection(
                "CANDIDATE_SKILL_PROOF_NONCE_RECORDED_OWNER_UNVERIFIED",
                check.taskId(), check.agentId(), check.skillId(),
                nonce.getNonceHex(), check.proofSha256(),
                true, false, false, false, false, false, 0, 0);
    }

    public record RecordedInspection(String evidenceClass, UUID taskId,
            String agentId, String skillId, String nonceHex, String proofSha256,
            boolean databaseNonceRecorded,
            boolean humanOwnerAuthenticated, boolean ownerApprovalGranted,
            boolean durableActionReplayCertified, boolean toolExecutionAuthorized,
            boolean stage45Released, int modelCalls, int toolCalls) {}
}
