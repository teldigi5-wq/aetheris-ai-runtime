package io.aetheris.orchestrator.organization;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class EnterpriseSignedProofReplayLedgerTest {
    @Autowired EnterpriseDesignArtifactService artifacts;
    @Autowired EnterpriseDesignReviewIntakeService review;
    @Autowired EnterpriseDesignReviewIntentRepository intents;
    @Autowired EnterpriseReviewProofNonceRepository nonces;
    @Autowired EnterpriseDesignReviewDenialRepository denials;
    @Autowired EnterpriseSignedProofReplayLedger ledger;

    @AfterEach
    void cleanup() {
        // FK preservation: evidence must be removed before the test review intent.
        nonces.deleteAll();
        denials.deleteAll();
        intents.deleteAll();
    }

    private record Fixture(EnterpriseOwnerProofPreflight.SignedReviewProof proof,
                           KeyPair key, String intentId) {}

    private Fixture createProof(String nonce, String project) throws Exception {
        var bundle = artifacts.create(new EnterpriseAdvisoryPlanningService.DesignBrief(
                project, "aetheris-immersive", "scroll-experience-engineering",
                "Design scroll-controlled animated laptop scene",
                List.of("Reverse-scroll tests", "Reduced-motion fallback")));
        var receipt = review.submitForUntrustedReview(bundle);
        var row = intents.findById(receipt.intentId()).orElseThrow();
        var scope = EnterpriseOwnerProofPreflight.ReviewScope.fromStoredIntent(row);
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Instant issued = row.getIssuedAt();
        Instant expiry = issued.plusSeconds(120);
        var unsigned = new EnterpriseOwnerProofPreflight.SignedReviewProof(
                scope, "REVIEW_STATIC_DESIGN_ONLY", nonce, issued, expiry, "A".repeat(86));
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(keys.getPrivate());
        signature.update(EnterpriseOwnerProofPreflight.payload(unsigned));
        var signed = new EnterpriseOwnerProofPreflight.SignedReviewProof(
                scope, unsigned.purpose(), nonce, issued, expiry,
                Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign()));
        return new Fixture(signed, keys, receipt.intentId());
    }

    @Test
    void realDatabaseReservationRecordsOnlyDenyOnlyEvidence() throws Exception {
        Fixture f = createProof("a".repeat(32), "laptop-alpha");
        var result = ledger.inspectAndRecord(f.proof(), f.key().getPublic());
        assertThat(result.nonceRecorded()).isTrue();
        assertThat(result.actualOwnerAuthenticated()).isFalse();
        assertThat(result.approvalGranted()).isFalse();
        assertThat(result.nonceConsumedForAction()).isFalse();
        assertThat(result.executionReleased()).isFalse();
        assertThat(result.positiveActionReplayProtectionCertified()).isFalse();
        assertThat(result.evidenceState()).isEqualTo(
                "SIGNATURE_INSPECTED_NONCE_RESERVED_OWNER_UNVERIFIED");
        var stored = nonces.findById("a".repeat(32)).orElseThrow();
        assertThat(stored.getIntentId()).isEqualTo(f.intentId());
        assertThat(stored.getProjectId()).isEqualTo("laptop-alpha");
        assertThat(stored.getEvidenceState()).isEqualTo("SIGNED_BYTES_SEEN_OWNER_UNVERIFIED");
        assertThat(stored.getProofSha256()).matches("[0-9a-f]{64}");
    }

    @Test
    void identicalProofCannotBeRecordedTwice() throws Exception {
        Fixture f = createProof("b".repeat(32), "laptop-beta");
        ledger.inspectAndRecord(f.proof(), f.key().getPublic());
        assertThatThrownBy(() -> ledger.inspectAndRecord(f.proof(), f.key().getPublic()))
                .isInstanceOf(RuntimeException.class);
        assertThat(nonces.count()).isEqualTo(1);
    }

    @Test
    void globalNonceCannotBeReusedInAnotherSignedIntent() throws Exception {
        Fixture first = createProof("c".repeat(32), "laptop-first");
        Fixture second = createProof("c".repeat(32), "laptop-second");
        ledger.inspectAndRecord(first.proof(), first.key().getPublic());
        assertThatThrownBy(() -> ledger.inspectAndRecord(second.proof(), second.key().getPublic()))
                .isInstanceOf(RuntimeException.class);
        assertThat(nonces.count()).isEqualTo(1);
        assertThat(nonces.findById("c".repeat(32)).orElseThrow().getIntentId())
                .isEqualTo(first.intentId());
    }

    @Test
    void alteredArtifactScopeAndWrongSignatureNeverWriteNonce() throws Exception {
        Fixture f = createProof("d".repeat(32), "laptop-delta");
        var scope = f.proof().claimedScope();
        var shifted = new EnterpriseOwnerProofPreflight.ReviewScope(
                scope.intentId(), scope.projectId(), "aetheris-finance", scope.divisionId(),
                scope.briefSha256(), scope.storyboardSha256(), scope.manifestSha256(),
                scope.intentIssuedAt(), scope.intentExpiresAt());
        var forged = new EnterpriseOwnerProofPreflight.SignedReviewProof(
                shifted, f.proof().purpose(), f.proof().nonceHex(),
                f.proof().issuedAt(), f.proof().expiresAt(), f.proof().signatureBase64Url());
        assertThatThrownBy(() -> ledger.inspectAndRecord(forged, f.key().getPublic()))
                .hasMessage("E22B_PROOF_SCOPE_MISMATCH");
        var untrusted = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        assertThatThrownBy(() -> ledger.inspectAndRecord(f.proof(), untrusted.getPublic()))
                .hasMessage("E22B_PROOF_SIGNATURE_INVALID");
        assertThat(nonces.count()).isZero();
    }

    @Test
    void concurrentReplayRaceCanPersistExactlyOneAndNeverApprove() throws Exception {
        Fixture f = createProof("e".repeat(32), "laptop-race");
        var start = new CountDownLatch(1);
        var success = new AtomicInteger(0);
        var denied = new AtomicInteger(0);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                start.await();
                try {
                    var result = ledger.inspectAndRecord(f.proof(), f.key().getPublic());
                    if (result.nonceRecorded() && !result.approvalGranted()
                            && !result.executionReleased()) success.incrementAndGet();
                } catch (RuntimeException duplicateOrConflict) {
                    denied.incrementAndGet();
                }
                return null;
            })).toList();
            start.countDown();
            for (var task : tasks) task.get(20, TimeUnit.SECONDS);
        }
        assertThat(success.get()).isEqualTo(1);
        assertThat(denied.get()).isEqualTo(1);
        assertThat(nonces.count()).isEqualTo(1);
        assertThat(nonces.findById("e".repeat(32)).orElseThrow().getIntentId())
                .isEqualTo(f.intentId());
    }
}
