package io.aetheris.orchestrator.stage16;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

@Service
public class Stage16CommandTrustService {
    private final Stage16TrustedSignerRepository repository;

    public Stage16CommandTrustService(Stage16TrustedSignerRepository repository) { this.repository = repository; }

    @Transactional
    public Stage16TrustedSignerEntity registerSigner(SignerRegistration request) {
        if (request == null) throw new IllegalArgumentException("Signer registration is required");
        String keyId = token(request.keyId(), "keyId", 80).toLowerCase(Locale.ROOT);
        if (repository.existsById(keyId)) throw new IllegalStateException("Stage 16 signer already exists");
        String name = text(request.displayName(), "displayName", 160);
        String publicKeyBase64 = normalizeBase64(request.publicKeyBase64(), "publicKeyBase64", 512);
        byte[] encoded = Base64.getDecoder().decode(publicKeyBase64);
        parsePublicKey(encoded);
        return repository.save(new Stage16TrustedSignerEntity(keyId, name, publicKeyBase64, sha256(encoded)));
    }

    public Stage16TrustedSignerEntity get(String keyId) {
        String key = token(keyId, "keyId", 80).toLowerCase(Locale.ROOT);
        return repository.findById(key).orElseThrow(() -> new NoSuchElementException("Unknown Stage 16 signer"));
    }

    public List<Stage16TrustedSignerEntity> list() { return repository.findTop100ByOrderByCreatedAtDesc(); }

    public boolean verify(String keyId, String canonicalPayload, String signatureBase64) {
        Stage16TrustedSignerEntity signer = get(keyId);
        if (!signer.isEnabled()) throw new IllegalStateException("Stage 16 signer is disabled");
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(parsePublicKey(Base64.getDecoder().decode(signer.getPublicKeyBase64())));
            signature.update(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(normalizeBase64(signatureBase64, "signatureBase64", 512)));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Ed25519 signature material", e);
        }
    }

    private PublicKey parsePublicKey(byte[] encoded) {
        try {
            PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
            if (!"EdDSA".equalsIgnoreCase(key.getAlgorithm()) && !"Ed25519".equalsIgnoreCase(key.getAlgorithm()))
                throw new IllegalArgumentException("Only Ed25519 public keys are accepted");
            return key;
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("publicKeyBase64 is not a valid Ed25519 X.509 public key", e);
        }
    }

    private String normalizeBase64(String value, String label, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException(label + " is invalid");
        try { return Base64.getEncoder().encodeToString(Base64.getDecoder().decode(value.trim())); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException(label + " must be valid Base64"); }
    }
    private String token(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String v = value.trim();
        if (v.length() > max || !v.matches("[A-Za-z0-9._:/-]{1," + max + "}")) throw new IllegalArgumentException(label + " is invalid");
        return v;
    }
    private String text(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String v = value.trim();
        if (v.length() > max) throw new IllegalArgumentException(label + " is too long");
        return v;
    }
    public static String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }

    public record SignerRegistration(String keyId, String displayName, String publicKeyBase64) {}
}
