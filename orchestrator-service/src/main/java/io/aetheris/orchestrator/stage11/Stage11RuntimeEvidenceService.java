package io.aetheris.orchestrator.stage11;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class Stage11RuntimeEvidenceService {
    private static final Set<String> KINDS = Set.of(
            "WORKSTATION", "SLO", "SPEECH", "REMOTE", "SOURCE_HEALTH", "MARKET_SOURCE", "INSTALLER", "INCIDENT");
    private final Stage11RuntimeEvidenceRepository repository;

    public Stage11RuntimeEvidenceService(Stage11RuntimeEvidenceRepository repository) {
        this.repository = repository;
    }

    public Stage11RuntimeEvidenceEntity record(EvidenceRequest request) {
        if (request == null) throw new IllegalArgumentException("Runtime evidence is required");
        String kind = require(request.kind(), "kind").toUpperCase(Locale.ROOT);
        if (!KINDS.contains(kind)) throw new IllegalArgumentException("Unsupported Stage 11 evidence kind: " + kind);
        String status = safeToken(request.status(), "status", 48);
        String source = safeToken(request.source(), "source", 80);
        String detail = require(request.detail(), "detail");
        if (detail.length() > 2000) throw new IllegalArgumentException("Evidence detail is too long");
        rejectSecretLikeDetail(detail);
        String attestation = request.attestationSha256() == null ? "" : request.attestationSha256().trim().toLowerCase(Locale.ROOT);
        if (request.targetMeasured() && !attestation.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Target-measured evidence requires a SHA-256 attestation reference");
        }
        if (!request.targetMeasured() && !attestation.isBlank() && !attestation.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Attestation reference must be SHA-256 when supplied");
        }
        String evidenceStatus = request.targetMeasured() ? "TARGET_REPORTED:" + status : "CI_RECORDED:" + status;
        return repository.save(new Stage11RuntimeEvidenceEntity(UUID.randomUUID(), kind, evidenceStatus, source,
                request.targetMeasured(), attestation.isBlank() ? null : attestation, detail.trim()));
    }

    public List<Stage11RuntimeEvidenceEntity> recent() {
        return repository.findTop200ByOrderByObservedAtDesc();
    }

    private void rejectSecretLikeDetail(String value) {
        String s = value.toLowerCase(Locale.ROOT);
        for (String marker : List.of("password=", "api_key=", "api-key=", "api_secret=", "api-secret=", "authorization:", "bearer ")) {
            if (s.contains(marker)) throw new IllegalArgumentException("Evidence detail appears to contain credential material");
        }
    }

    private String safeToken(String value, String label, int max) {
        String v = require(value, label);
        if (v.length() > max || !v.matches("[A-Za-z0-9._:-]{1," + max + "}")) throw new IllegalArgumentException(label + " is invalid");
        return v;
    }

    private String require(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }

    public record EvidenceRequest(String kind, String status, String source, boolean targetMeasured,
                                  String attestationSha256, String detail) {}
}
