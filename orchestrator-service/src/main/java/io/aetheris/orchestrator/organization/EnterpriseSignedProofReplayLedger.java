package io.aetheris.orchestrator.organization;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

/**
 * E2.2B-2C: durable, deny-only *signed-proof inspection* nonce reservation.
 *
 * The candidate public key comes from an internal caller, NOT an enrolled owner
 * identity. Signature verification proves only signed bytes. A primary-key
 * uniqueness violation (including a racing insert) aborts the transaction and
 * is intentionally propagated FAIL CLOSED; no replay returns an approval.
 *
 * No public controller, no key registration, no owner authentication, no
 * positive approval, no tool/agent execution, and no action consumption.
 */
@Service
public class EnterpriseSignedProofReplayLedger {
    private final EnterpriseOwnerProofPreflightBoundary boundary;
    private final EnterpriseDesignReviewIntentRepository intents;
    private final EnterpriseReviewProofNonceRepository nonces;
    private final Clock clock;

    @Autowired
    public EnterpriseSignedProofReplayLedger(
            EnterpriseDesignReviewIntentRepository intents,
            EnterpriseReviewProofNonceRepository nonces) {
        this(EnterpriseOwnerProofPreflight::inspect, intents, nonces, Clock.systemUTC());
    }

    EnterpriseSignedProofReplayLedger(
            EnterpriseOwnerProofPreflightBoundary boundary,
            EnterpriseDesignReviewIntentRepository intents,
            EnterpriseReviewProofNonceRepository nonces, Clock clock) {
        this.boundary = boundary;
        this.intents = intents;
        this.nonces = nonces;
        this.clock = clock;
    }

    /**
     * Real database transaction. Reserve exactly one nonce globally only after
     * valid signed bytes and exact stored-intent scope. Duplicates raise a DB
     * constraint exception, including two parallel transactions.
     */
    @Transactional
    public SeenReceipt inspectAndRecord(EnterpriseOwnerProofPreflight.SignedReviewProof proof,
                                        PublicKey candidateVerificationKey) {
        if (proof == null || proof.claimedScope() == null) {
            throw new IllegalArgumentException("E22C_PROOF_REQUIRED");
        }
        String intentId = proof.claimedScope().intentId();
        if (intentId == null || !intentId.matches(
                "[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}")) {
            throw new IllegalArgumentException("E22C_INTENT_ID_INVALID");
        }
        var persisted = intents.findById(intentId)
                .orElseThrow(() -> new IllegalArgumentException("E22C_INTENT_NOT_FOUND"));
        var actual = EnterpriseOwnerProofPreflight.ReviewScope.fromStoredIntent(persisted);
        var check = boundary.inspect(proof, actual, candidateVerificationKey, clock);
        if (!check.cryptographicSignatureValid() || check.actualOwnerAuthenticated()
                || check.approvalGranted() || check.nonceConsumed()
                || check.executionReleased() || check.replayProtectionCertified()) {
            throw new IllegalStateException("E22C_UNEXPECTED_PROOF_AUTHORITY");
        }
        Instant seen = clock.instant().truncatedTo(ChronoUnit.MICROS);
        // No preflight existsById: that would introduce a check-then-insert race.
        // The DB primary key is the single linearizable reservation point.
        var row = new EnterpriseReviewProofNonce(
                proof.nonceHex(), actual.intentId(), actual.projectId(),
                sha256(EnterpriseOwnerProofPreflight.payload(proof)), seen,
                proof.expiresAt().truncatedTo(ChronoUnit.MICROS));
        nonces.saveAndFlush(row);
        return new SeenReceipt(row.getNonceHex(), row.getIntentId(),
                "SIGNATURE_INSPECTED_NONCE_RESERVED_OWNER_UNVERIFIED",
                true, false, false, false, false, false);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception ex) {
            throw new IllegalStateException("E22C_DIGEST_UNAVAILABLE", ex);
        }
    }

    /** Injectable verifier contract solely for deterministic negative test injection. */
    @FunctionalInterface
    interface EnterpriseOwnerProofPreflightBoundary {
        EnterpriseOwnerProofPreflight.Inspection inspect(
                EnterpriseOwnerProofPreflight.SignedReviewProof proof,
                EnterpriseOwnerProofPreflight.ReviewScope stored,
                PublicKey key, Clock clock);
    }

    public record SeenReceipt(String nonceHex, String intentId, String evidenceState,
                              boolean nonceRecorded,
                              boolean actualOwnerAuthenticated, boolean approvalGranted,
                              boolean nonceConsumedForAction, boolean executionReleased,
                              boolean positiveActionReplayProtectionCertified) {}
}
