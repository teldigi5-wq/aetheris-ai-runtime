package io.aetheris.orchestrator.organization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Genuine JDK Ed25519 root and candidate key tests. Keys are generated only
 * inside tests; none represents a registered or authenticated real owner.
 */
class EnterpriseOfflineKeyTrustTest {
    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private static final Clock NOW_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Instant START = NOW.minusSeconds(60);
    private static final Instant END = NOW.plusSeconds(3600);
    private KeyPair root;
    private KeyPair candidate;
    private KeyPair replacement;

    @BeforeEach
    void init() throws Exception {
        root = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        candidate = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        replacement = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private EnterpriseOfflineKeyTrust.CandidateKey key(
            String id, KeyPair pair, String state, Instant validFrom, Instant validUntil) {
        return new EnterpriseOfflineKeyTrust.CandidateKey(id,
                Base64.getUrlEncoder().withoutPadding().encodeToString(pair.getPublic().getEncoded()),
                state, validFrom, validUntil);
    }

    private EnterpriseOfflineKeyTrust.SignedBundle signed(
            long generation, List<EnterpriseOfflineKeyTrust.CandidateKey> keys,
            KeyPair signingRoot, Instant from, Instant to) throws Exception {
        var unsigned = new EnterpriseOfflineKeyTrust.SignedBundle(
                1, generation, from, to, keys, null);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(signingRoot.getPrivate());
        signer.update(EnterpriseOfflineKeyTrust.canonicalBytes(unsigned));
        return new EnterpriseOfflineKeyTrust.SignedBundle(
                1, generation, from, to, keys,
                Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
    }

    private EnterpriseOfflineKeyTrust.SignedBundle bundle() throws Exception {
        return signed(2, List.of(key("owner-key-v2", candidate,
                "ACTIVE_CANDIDATE_UNATTESTED", START, END)), root, START, END);
    }

    @Test
    void rootSignedCandidateResolvesWithoutGrantingHumanIdentity() throws Exception {
        var resolution = EnterpriseOfflineKeyTrust.resolve(
                bundle(), root.getPublic(), 2L, "owner-key-v2", NOW_CLOCK);
        assertThat(resolution.keyId()).isEqualTo("owner-key-v2");
        assertThat(resolution.candidatePublicKey().getEncoded())
                .isEqualTo(candidate.getPublic().getEncoded());
        assertThat(resolution.signedGeneration()).isEqualTo(2L);
        assertThat(resolution.evidenceState())
                .isEqualTo("ROOT_SIGNED_CANDIDATE_KEY_NOT_HUMAN_ATTESTED");
        assertThat(resolution.authenticatedHumanOwner()).isFalse();
        assertThat(resolution.ownerApprovalGranted()).isFalse();
        assertThat(resolution.executionPermitted()).isFalse();
    }

    @Test
    void unknownOrUnpinnedRootRejected() throws Exception {
        var rogue = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        assertThatThrownBy(() -> EnterpriseOfflineKeyTrust.resolve(
                bundle(), rogue.getPublic(), 2L, "owner-key-v2", NOW_CLOCK))
                .hasMessage("E22D_ROOT_SIGNATURE_INVALID");
        assertThatThrownBy(() -> EnterpriseOfflineKeyTrust.resolve(
                bundle(), null, 2L, "owner-key-v2", NOW_CLOCK))
                .hasMessage("E22D_TRUSTED_ROOT_MISSING");
    }

    @Test
    void alteredKeyCannotBeSubstitutedUnderOldRootSignature() throws Exception {
        var original = bundle();
        var altered = new EnterpriseOfflineKeyTrust.SignedBundle(
                original.schemaVersion(), original.generation(), original.issuedAt(),
                original.expiresAt(),
                List.of(key("owner-key-v2", replacement, "ACTIVE_CANDIDATE_UNATTESTED",
                        START, END)),
                original.operatorRootSignatureBase64Url());
        assertThatThrownBy(() -> EnterpriseOfflineKeyTrust.resolve(
                altered, root.getPublic(), 2L, "owner-key-v2", NOW_CLOCK))
                .hasMessage("E22D_ROOT_SIGNATURE_INVALID");
    }

    @Test
    void explicitlyRevokedKeyRejectedEvenWhenRootSigned() throws Exception {
        var revoked = signed(3, List.of(
                key("owner-key-v2", candidate, "REVOKED", START, END)),
                root, START, END);
        assertThatThrownBy(() -> EnterpriseOfflineKeyTrust.resolve(
                revoked, root.getPublic(), 3L, "owner-key-v2", NOW_CLOCK))
                .hasMessage("E22D_KEY_REVOKED");
    }

    @Test
    void keyRotationAcceptsNewCandidateAndRefusesFormerKey() throws Exception {
        var rotated = signed(3, List.of(
                key("old-key", candidate, "REVOKED", START, END),
                key("new-key", replacement, "ACTIVE_CANDIDATE_UNATTESTED", START, END)),
                root, START, END);
        var current = EnterpriseOfflineKeyTrust.resolve(
                rotated, root.getPublic(), 3L, "new-key", NOW_CLOCK);
        assertThat(current.candidatePublicKey().getEncoded())
                .isEqualTo(replacement.getPublic().getEncoded());
        assertThat(current.ownerApprovalGranted()).isFalse();
        assertThatThrownBy(() -> EnterpriseOfflineKeyTrust.resolve(
                rotated, root.getPublic(), 3L, "old-key", NOW_CLOCK))
                .hasMessage("E22D_KEY_REVOKED");
        assertThatThrownBy(() -> EnterpriseOfflineKeyTrust.resolve(
                bundle(), root.getPublic(), 3L, "owner-key-v2", NOW_CLOCK))
                .hasMessage("E22D_UNTRUSTED_OR_ROLLED_BACK_GENERATION");
    }

    @Test
    void expiredBundleOrFutureIssuedBundleRejected() throws Exception {
        var expired = signed(2, List.of(key("owner-key-v2", candidate,
                "ACTIVE_CANDIDATE_UNATTESTED", START, NOW.minusSeconds(1))),
                root, START, NOW.minusSeconds(1));
        assertThatThrownBy(() -> EnterpriseOfflineKeyTrust.resolve(
                expired, root.getPublic(), 2L, "owner-key-v2", NOW_CLOCK))
                .hasMessage("E22D_BUNDLE_EXPIRED_OR_UNSUPPORTED");
        var future = signed(2, List.of(key("owner-key-v2", candidate,
                "ACTIVE_CANDIDATE_UNATTESTED", NOW.plusSeconds(20), END)),
                root, NOW.plusSeconds(20), END);
        assertThatThrownBy(() -> EnterpriseOfflineKeyTrust.resolve(
                future, root.getPublic(), 2L, "owner-key-v2", NOW_CLOCK))
                .hasMessage("E22D_BUNDLE_EXPIRED_OR_UNSUPPORTED");
    }

    @Test
    void expiredIndividualCandidateRefusedEvenWithinLiveBundle() throws Exception {
        var data = signed(2, List.of(key("owner-key-v2", candidate,
                "ACTIVE_CANDIDATE_UNATTESTED", START, NOW.minusSeconds(1))),
                root, START, END);
        assertThatThrownBy(() -> EnterpriseOfflineKeyTrust.resolve(
                data, root.getPublic(), 2L, "owner-key-v2", NOW_CLOCK))
                .hasMessage("E22D_KEY_NOT_YET_ACTIVE_OR_EXPIRED");
    }

    @Test
    void unknownKeyIdAndInvalidStateFailClosed() throws Exception {
        assertThatThrownBy(() -> EnterpriseOfflineKeyTrust.resolve(
                bundle(), root.getPublic(), 2L, "not-in-manifest", NOW_CLOCK))
                .hasMessage("E22D_CANDIDATE_UNKNOWN");
        var invalid = new EnterpriseOfflineKeyTrust.SignedBundle(
                1, 2, START, END,
                List.of(key("owner-key-v2", candidate, "OWNER_CONFIRMED", START, END)), null);
        assertThatThrownBy(() -> EnterpriseOfflineKeyTrust.canonicalBytes(invalid))
                .hasMessage("E22D_INVALID_OR_DUPLICATE_KEY_ENTRY");
    }

    @Test
    void duplicateKeyIdOrExcessiveRosterRejected() {
        var repeated = key("owner-key-v2", candidate,
                "ACTIVE_CANDIDATE_UNATTESTED", START, END);
        var dup = new EnterpriseOfflineKeyTrust.SignedBundle(
                1, 2, START, END, List.of(repeated, repeated), null);
        assertThatThrownBy(() -> EnterpriseOfflineKeyTrust.canonicalBytes(dup))
                .hasMessage("E22D_INVALID_OR_DUPLICATE_KEY_ENTRY");
        var many = new ArrayList<EnterpriseOfflineKeyTrust.CandidateKey>();
        for (int i = 0; i < 9; i++) {
            many.add(key("key-" + i, candidate, "ACTIVE_CANDIDATE_UNATTESTED", START, END));
        }
        var over = new EnterpriseOfflineKeyTrust.SignedBundle(
                1, 2, START, END, many, null);
        assertThatThrownBy(() -> EnterpriseOfflineKeyTrust.canonicalBytes(over))
                .hasMessage("E22D_BUNDLE_STRUCTURE_INVALID");
    }

    @Test
    void wrongCurveAndMalformedCandidateEncodingRejected() throws Exception {
        var ed448 = KeyPairGenerator.getInstance("Ed448").generateKeyPair();
        var wrong = new EnterpriseOfflineKeyTrust.SignedBundle(
                1, 2, START, END,
                List.of(key("owner-key-v2", ed448, "ACTIVE_CANDIDATE_UNATTESTED", START, END)),
                null);
        assertThatThrownBy(() -> EnterpriseOfflineKeyTrust.canonicalBytes(wrong))
                .hasMessage("E22D_CANDIDATE_KEY_ENCODING_INVALID");
        var malformed = new EnterpriseOfflineKeyTrust.SignedBundle(
                1, 2, START, END,
                List.of(new EnterpriseOfflineKeyTrust.CandidateKey(
                        "owner-key-v2", "../secret", "ACTIVE_CANDIDATE_UNATTESTED", START, END)),
                null);
        assertThatThrownBy(() -> EnterpriseOfflineKeyTrust.canonicalBytes(malformed))
                .hasMessage("E22D_CANDIDATE_KEY_ENCODING_INVALID");
    }

    @Test
    void signedKeyBundleCannotExtendMoreThanTwentyFourHours() throws Exception {
        var tooLong = signed(2, List.of(key("owner-key-v2", candidate,
                "ACTIVE_CANDIDATE_UNATTESTED", START, START.plusSeconds(86401))),
                root, START, START.plusSeconds(86401));
        assertThatThrownBy(() -> EnterpriseOfflineKeyTrust.resolve(
                tooLong, root.getPublic(), 2L, "owner-key-v2", NOW_CLOCK))
                .hasMessage("E22D_BUNDLE_EXPIRED_OR_UNSUPPORTED");
    }

    @Test
    void signedProofWithPinnedCandidateVerifiesButStillGrantsNothing() throws Exception {
        var scope = new EnterpriseOwnerProofPreflight.ReviewScope(
                "00000000-0000-4000-8000-000000000001",
                "laptop-product", "aetheris-immersive", "scroll-experience-engineering",
                "a".repeat(64), "b".repeat(64), "c".repeat(64), START, START.plusSeconds(900));
        var unsigned = new EnterpriseOwnerProofPreflight.SignedReviewProof(
                scope, "REVIEW_STATIC_DESIGN_ONLY", "d".repeat(32),
                NOW.minusSeconds(15), NOW.plusSeconds(90), "A".repeat(86));
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(candidate.getPrivate());
        signer.update(EnterpriseOwnerProofPreflight.payload(unsigned));
        var signedProof = new EnterpriseOwnerProofPreflight.SignedReviewProof(
                scope, unsigned.purpose(), unsigned.nonceHex(),
                unsigned.issuedAt(), unsigned.expiresAt(),
                Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign()));
        var result = EnterpriseOfflineKeyTrust.inspectWithSignedBundle(
                signedProof, scope, bundle(), root.getPublic(),
                2L, "owner-key-v2", NOW_CLOCK);
        assertThat(result.cryptographicSignatureValid()).isTrue();
        assertThat(result.actualOwnerAuthenticated()).isFalse();
        assertThat(result.approvalGranted()).isFalse();
        assertThat(result.executionReleased()).isFalse();
        assertThat(result.replayProtectionCertified()).isFalse();
    }
}
