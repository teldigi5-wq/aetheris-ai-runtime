package io.aetheris.orchestrator.organization;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * E2.2D: offline-signed trust of CANDIDATE public keys, not human-owner identity.
 *
 * The root public key and minimum accepted generation MUST be provisioned
 * independently through a trusted, non-model-controlled operator channel.
 * Passing a root key from a network client would destroy this trust boundary.
 *
 * This is deliberately NOT a Spring Bean, enrollment API, owner session,
 * consent service, approval grant, action executor, or durable anti-rollback
 * state store. All resulting authority fields remain false.
 */
public final class EnterpriseOfflineKeyTrust {
    private static final String DOMAIN = "AETHERIS_OWNER_CANDIDATE_KEY_BUNDLE_V1";
    private static final String KEY_STATE = "ACTIVE_CANDIDATE_UNATTESTED";
    private static final String REVOKED = "REVOKED";
    private static final long MAX_BUNDLE_SECONDS = 86400L;
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern SIG = Pattern.compile("[A-Za-z0-9_-]{86}");

    private EnterpriseOfflineKeyTrust() {}

    public record CandidateKey(String keyId, String ed25519SpkiBase64Url, String state,
                               Instant validFrom, Instant validUntil) {}

    /**
     * The signature covers ALL fields via canonical length-prefixed encoding.
     * A detached root public key and a separately pinned minimum generation
     * must be provided by the deployment operator, never by bundle contents.
     */
    public record SignedBundle(int schemaVersion, long generation,
                               Instant issuedAt, Instant expiresAt,
                               List<CandidateKey> candidateKeys,
                               String operatorRootSignatureBase64Url) {}

    /** A resolved candidate key is not an authenticated human or action approval. */
    public record CandidateResolution(String keyId, PublicKey candidatePublicKey,
                                      long signedGeneration, String evidenceState,
                                      boolean authenticatedHumanOwner,
                                      boolean ownerApprovalGranted,
                                      boolean executionPermitted) {}

    /**
     * Verifies operator-root signature, expiry, generation rollback limits and
     * explicit revocation before returning an Ed25519 candidate verification key.
     */
    public static CandidateResolution resolve(SignedBundle bundle, PublicKey operatorPinnedRoot,
                                               long trustedMinimumGeneration, String keyId,
                                               Clock clock) {
        require(clock != null && ed25519(operatorPinnedRoot),
                "E22D_TRUSTED_ROOT_MISSING");
        require(bundle != null && trustedMinimumGeneration >= 1L
                && bundle.generation() >= trustedMinimumGeneration,
                "E22D_UNTRUSTED_OR_ROLLED_BACK_GENERATION");
        require(bundle.schemaVersion() == 1 && bundle.issuedAt() != null
                && bundle.expiresAt() != null && bundle.expiresAt().isAfter(bundle.issuedAt())
                && !bundle.expiresAt().isAfter(bundle.issuedAt().plusSeconds(MAX_BUNDLE_SECONDS))
                && !clock.instant().isBefore(bundle.issuedAt())
                && clock.instant().isBefore(bundle.expiresAt()),
                "E22D_BUNDLE_EXPIRED_OR_UNSUPPORTED");
        require(slug(keyId), "E22D_CANDIDATE_KEY_ID_INVALID");
        // Snapshot the untrusted roster BEFORE signing-byte verification and
        // reuse the exact same immutable entries for key selection (no TOCTOU).
        require(bundle.candidateKeys() != null, "E22D_BUNDLE_STRUCTURE_INVALID");
        SignedBundle immutableBundle = new SignedBundle(bundle.schemaVersion(),
                bundle.generation(), bundle.issuedAt(), bundle.expiresAt(),
                List.copyOf(bundle.candidateKeys()), bundle.operatorRootSignatureBase64Url());
        byte[] message = canonicalBytes(immutableBundle);
        require(immutableBundle.operatorRootSignatureBase64Url() != null
                && SIG.matcher(immutableBundle.operatorRootSignatureBase64Url()).matches(),
                "E22D_ROOT_SIGNATURE_ENCODING_INVALID");
        try {
            byte[] signature = Base64.getUrlDecoder().decode(immutableBundle.operatorRootSignatureBase64Url());
            require(signature.length == 64, "E22D_ROOT_SIGNATURE_ENCODING_INVALID");
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(operatorPinnedRoot);
            verifier.update(message);
            require(verifier.verify(signature), "E22D_ROOT_SIGNATURE_INVALID");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("E22D_ROOT_SIGNATURE_INVALID", e);
        }

        CandidateKey candidate = immutableBundle.candidateKeys().stream()
                .filter(k -> k.keyId().equals(keyId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("E22D_CANDIDATE_UNKNOWN"));
        require(!REVOKED.equals(candidate.state()), "E22D_KEY_REVOKED");
        require(KEY_STATE.equals(candidate.state()), "E22D_CANDIDATE_STATE_INVALID");
        require(!clock.instant().isBefore(candidate.validFrom())
                && clock.instant().isBefore(candidate.validUntil()),
                "E22D_KEY_NOT_YET_ACTIVE_OR_EXPIRED");
        return new CandidateResolution(keyId, decodePublic(candidate.ed25519SpkiBase64Url()),
                bundle.generation(), "ROOT_SIGNED_CANDIDATE_KEY_NOT_HUMAN_ATTESTED",
                false, false, false);
    }

    /**
     * This combines a preflight proof with an offline-verified key bundle.
     * It cannot produce owner approval: the downstream proof is deny-only.
     */
    public static EnterpriseOwnerProofPreflight.Inspection inspectWithSignedBundle(
            EnterpriseOwnerProofPreflight.SignedReviewProof proof,
            EnterpriseOwnerProofPreflight.ReviewScope stored,
            SignedBundle bundle, PublicKey operatorPinnedRoot,
            long trustedMinimumGeneration, String candidateId, Clock clock) {
        CandidateResolution candidate = resolve(bundle, operatorPinnedRoot,
                trustedMinimumGeneration, candidateId, clock);
        require(!candidate.authenticatedHumanOwner() && !candidate.ownerApprovalGranted()
                && !candidate.executionPermitted(), "E22D_UNEXPECTED_OWNER_AUTHORITY");
        return EnterpriseOwnerProofPreflight.inspect(
                proof, stored, candidate.candidatePublicKey(), clock);
    }

    /**
     * Exposed package-privately only for signing deterministic unit fixtures.
     * No user private key, root private key or signing adapter is bundled.
     */
    static byte[] canonicalBytes(SignedBundle bundle) {
        require(bundle != null && bundle.schemaVersion() == 1 && bundle.generation() > 0
                && bundle.issuedAt() != null && bundle.expiresAt() != null
                && bundle.candidateKeys() != null
                && !bundle.candidateKeys().isEmpty() && bundle.candidateKeys().size() <= 8,
                "E22D_BUNDLE_STRUCTURE_INVALID");

        Set<String> keys = new HashSet<>();
        List<CandidateKey> ordered = new ArrayList<>(bundle.candidateKeys());
        for (CandidateKey key : ordered) {
            require(key != null && slug(key.keyId()) && keys.add(key.keyId())
                    && (KEY_STATE.equals(key.state()) || REVOKED.equals(key.state()))
                    && key.validFrom() != null && key.validUntil() != null
                    && key.validUntil().isAfter(key.validFrom())
                    // An existing key can predate the newly signed bundle on rotation.
                    && key.validFrom().isBefore(bundle.expiresAt())
                    && key.validUntil().isAfter(bundle.issuedAt())
                    && !key.validUntil().isAfter(bundle.expiresAt()),
                    "E22D_INVALID_OR_DUPLICATE_KEY_ENTRY");
            decodePublic(key.ed25519SpkiBase64Url());
        }
        ordered.sort(Comparator.comparing(CandidateKey::keyId));
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buffer);
            writeAscii(out, DOMAIN);
            out.writeInt(bundle.schemaVersion());
            out.writeLong(bundle.generation());
            writeInstant(out, bundle.issuedAt());
            writeInstant(out, bundle.expiresAt());
            out.writeInt(ordered.size());
            for (CandidateKey key : ordered) {
                writeAscii(out, key.keyId());
                writeAscii(out, key.ed25519SpkiBase64Url());
                writeAscii(out, key.state());
                writeInstant(out, key.validFrom());
                writeInstant(out, key.validUntil());
            }
            out.flush();
            return buffer.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("E22D_CANONICAL_SERIALIZATION_FAILED", e);
        }
    }

    private static void writeAscii(DataOutputStream out, String text) throws Exception {
        byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
        require(bytes.length <= 200, "E22D_FIELD_TOO_LONG");
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static void writeInstant(DataOutputStream out, Instant at) throws Exception {
        out.writeLong(at.getEpochSecond());
        out.writeInt(at.getNano());
    }

    private static PublicKey decodePublic(String encoded) {
        require(encoded != null && encoded.length() <= 150
                && encoded.matches("[A-Za-z0-9_-]+"), "E22D_CANDIDATE_KEY_ENCODING_INVALID");
        try {
            byte[] der = Base64.getUrlDecoder().decode(encoded);
            require(Base64.getUrlEncoder().withoutPadding().encodeToString(der).equals(encoded)
                    && der.length >= 40 && der.length <= 90,
                    "E22D_CANDIDATE_KEY_ENCODING_INVALID");
            PublicKey key = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(der));
            require(ed25519(key), "E22D_CANDIDATE_NOT_ED25519");
            return key;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("E22D_CANDIDATE_KEY_ENCODING_INVALID", e);
        }
    }

    private static boolean ed25519(PublicKey key) {
        return key instanceof EdECPublicKey ed
                && "Ed25519".equals(ed.getParams().getName());
    }
    private static boolean slug(String id) {
        return id != null && id.length() <= 80 && SLUG.matcher(id).matches();
    }
    private static void require(boolean value, String reason) {
        if (!value) throw new IllegalArgumentException(reason);
    }
}
