package io.aetheris.orchestrator.stage12;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

@Service
public class Stage12ReleaseTrustService {
    private final String trustedSignerSha256;
    private final String trustedPublicKeyBase64;

    public Stage12ReleaseTrustService(
            @Value("${aetheris.stage12.release.trusted-signer-sha256:}") String trustedSignerSha256,
            @Value("${aetheris.stage12.release.trusted-public-key-base64:}") String trustedPublicKeyBase64) {
        this.trustedSignerSha256 = trustedSignerSha256 == null ? "" : trustedSignerSha256.trim().toLowerCase(Locale.ROOT);
        this.trustedPublicKeyBase64 = trustedPublicKeyBase64 == null ? "" : trustedPublicKeyBase64.trim();
    }

    public TrustReadiness readiness() {
        boolean fingerprintConfigured = trustedSignerSha256.matches("[a-f0-9]{64}");
        boolean publicKeyConfigured = !trustedPublicKeyBase64.isBlank();
        boolean keyMatchesFingerprint = false;
        if (fingerprintConfigured && publicKeyConfigured) {
            try {
                byte[] encoded = Base64.getDecoder().decode(trustedPublicKeyBase64);
                KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
                keyMatchesFingerprint = sha256(encoded).equals(trustedSignerSha256);
            } catch (Exception ignored) {
                keyMatchesFingerprint = false;
            }
        }
        boolean ready = fingerprintConfigured && publicKeyConfigured && keyMatchesFingerprint;
        return new TrustReadiness(ready ? "TRUST_ANCHOR_CONFIGURED" : "OWNER_TRUST_ANCHOR_REQUIRED",
                fingerprintConfigured, publicKeyConfigured, keyMatchesFingerprint, false,
                ready ? "Ed25519 public key and SHA-256 signer fingerprint are configured consistently; every release still requires per-artifact signature verification"
                        : "A valid owner-configured Ed25519 public key and matching SHA-256 signer fingerprint are required before release promotion");
    }

    public ReleaseVerification verify(ReleaseManifest manifest) {
        List<String> blockers = new ArrayList<>();
        if (manifest == null) return new ReleaseVerification("BLOCKED", List.of("release manifest is required"), false, false, false);
        String version = safe(manifest.version());
        String channel = safe(manifest.channel()).toUpperCase(Locale.ROOT);
        String artifact = safe(manifest.artifactSha256()).toLowerCase(Locale.ROOT);
        String observed = safe(manifest.observedArtifactSha256()).toLowerCase(Locale.ROOT);
        String signer = safe(manifest.signerSha256()).toLowerCase(Locale.ROOT);
        if (!version.matches("[A-Za-z0-9._+-]{1,80}")) blockers.add("release version is invalid");
        if (!Set.of("CANARY", "STABLE").contains(channel)) blockers.add("release channel must be CANARY or STABLE");
        if (!artifact.matches("[a-f0-9]{64}") || !observed.matches("[a-f0-9]{64}")) blockers.add("artifact SHA-256 evidence is invalid");
        boolean hashMatches = artifact.matches("[a-f0-9]{64}") && artifact.equals(observed);
        if (!hashMatches) blockers.add("observed artifact SHA-256 does not match the signed manifest");
        if (!signer.matches("[a-f0-9]{64}")) blockers.add("signer fingerprint is invalid");
        boolean signerTrusted = !trustedSignerSha256.isBlank() && signer.equals(trustedSignerSha256);
        if (!signerTrusted) blockers.add("release signer is not in the owner-configured trust anchor");
        if (trustedPublicKeyBase64.isBlank()) blockers.add("trusted release public key is not configured");
        if (manifest.signatureBase64() == null || manifest.signatureBase64().isBlank()) blockers.add("release signature is required");

        boolean signatureVerified = false;
        if (blockers.stream().noneMatch(x -> x.contains("public key") || x.contains("signature") || x.contains("signer"))) {
            try {
                byte[] encoded = Base64.getDecoder().decode(trustedPublicKeyBase64);
                PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
                String derived = sha256(encoded);
                if (!derived.equals(trustedSignerSha256)) blockers.add("configured release public key does not match trusted signer fingerprint");
                else {
                    Signature verifier = Signature.getInstance("Ed25519");
                    verifier.initVerify(key);
                    verifier.update(canonicalPayload(version, artifact, channel).getBytes(StandardCharsets.UTF_8));
                    signatureVerified = verifier.verify(Base64.getDecoder().decode(manifest.signatureBase64()));
                    if (!signatureVerified) blockers.add("release signature verification failed");
                }
            } catch (Exception e) {
                blockers.add("release signature material is invalid");
            }
        }
        return new ReleaseVerification(blockers.isEmpty() ? "CRYPTOGRAPHICALLY_VERIFIED" : "BLOCKED",
                List.copyOf(blockers), hashMatches, signerTrusted, signatureVerified);
    }

    public static String canonicalPayload(String version, String artifactSha256, String channel) {
        return version.trim() + "|" + artifactSha256.trim().toLowerCase(Locale.ROOT) + "|" + channel.trim().toUpperCase(Locale.ROOT);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private String safe(String value) { return value == null ? "" : value.trim(); }

    public record TrustReadiness(String status, boolean signerFingerprintConfigured, boolean publicKeyConfigured,
                                 boolean publicKeyMatchesFingerprint, boolean privateKeyExposed, String detail) {}
    public record ReleaseManifest(String version, String channel, String artifactSha256,
                                  String observedArtifactSha256, String signerSha256, String signatureBase64) {}
    public record ReleaseVerification(String status, List<String> blockers, boolean artifactHashMatches,
                                      boolean signerTrusted, boolean signatureVerified) {}
}
