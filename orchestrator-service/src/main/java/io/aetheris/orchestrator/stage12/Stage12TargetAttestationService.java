package io.aetheris.orchestrator.stage12;

import io.aetheris.orchestrator.stage11.Stage11RuntimeEvidenceEntity;
import io.aetheris.orchestrator.stage11.Stage11RuntimeEvidenceService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class Stage12TargetAttestationService {
    private final Stage11RuntimeEvidenceService stage11Evidence;

    public Stage12TargetAttestationService(Stage11RuntimeEvidenceService stage11Evidence) {
        this.stage11Evidence = stage11Evidence;
    }

    public TargetAttestation assess(String kind, String expectedAttestationSha256) {
        String requested = requireKind(kind).toUpperCase(Locale.ROOT);
        String expected = requireAttestation(expectedAttestationSha256);
        Optional<Stage11RuntimeEvidenceEntity> target = stage11Evidence.recent().stream()
                .filter(e -> requested.equals(e.getKind()))
                .filter(Stage11RuntimeEvidenceEntity::isTargetMeasured)
                .filter(e -> e.getStatus().startsWith("TARGET_REPORTED:"))
                .filter(e -> expected.equals(e.getAttestationSha256()))
                .findFirst();
        if (target.isEmpty()) {
            return new TargetAttestation("EVIDENCE_REQUIRED", requested, false, expected, null,
                    "No exact Stage 11 target attestation matches the expected SHA-256; CI/configuration or unrelated historical evidence cannot satisfy this Stage 12 gate");
        }
        Stage11RuntimeEvidenceEntity e = target.get();
        return new TargetAttestation("TARGET_ATTESTATION_AVAILABLE", requested, true,
                e.getAttestationSha256(), e.getObservedAt(),
                "Exact target-reported Stage 11 attestation matched; downstream gates must still verify their own domain requirements");
    }

    private String requireKind(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("attestation kind is required");
        String v = value.trim();
        if (!v.matches("[A-Za-z0-9._:-]{1,32}")) throw new IllegalArgumentException("attestation kind is invalid");
        return v;
    }

    private String requireAttestation(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("expected attestation SHA-256 is required");
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (!v.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("expected attestation must be a SHA-256 value");
        return v;
    }

    public record TargetAttestation(String status, String kind, boolean targetMeasured,
                                    String attestationSha256, Instant observedAt, String detail) {}
}
