package io.aetheris.orchestrator.stage21;

import io.aetheris.orchestrator.stage18.Stage18ProvisioningService;
import io.aetheris.orchestrator.stage20.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class Stage21PilotService {
    public static final Set<String> RESTRICTED_CAPABILITIES = Set.of(
            "PROCESS_READ", "PC_TELEMETRY", "OLLAMA", "VAD", "STT", "TTS", "PRIVATE_TRANSPORT");
    public static final List<String> REQUIRED_EVIDENCE = List.of(
            "HOST_AGENT_INSTALL", "DEVICE_IDENTITY", "PACKAGE_INTEGRITY", "OS_VAULT",
            "RESOURCE_BENCHMARK", "LOCAL_MODEL_BENCHMARK", "SPEECH_BENCHMARK", "PRIVATE_TRANSPORT",
            "STAGE20_LEASE_RECEIPT", "STOP_DRILL", "REVOCATION_DRILL", "ROLLBACK_DRILL");
    private static final Duration EVIDENCE_MAX_AGE = Duration.ofHours(24);

    private final Stage21PilotRepository pilots;
    private final Stage21PilotEvidenceRepository evidence;
    private final Stage20ActivationAuthorizationRepository authorizations;
    private final Stage20TargetReceiptRepository receipts;

    public Stage21PilotService(Stage21PilotRepository pilots,
                               Stage21PilotEvidenceRepository evidence,
                               Stage20ActivationAuthorizationRepository authorizations,
                               Stage20TargetReceiptRepository receipts) {
        this.pilots = pilots;
        this.evidence = evidence;
        this.authorizations = authorizations;
        this.receipts = receipts;
    }

    @Transactional
    public Stage21PilotEntity prepare(PilotRequest request) {
        if (request == null || request.stage20AuthorizationId() == null)
            throw new IllegalArgumentException("Stage 21 pilot requires a Stage 20 authorization");
        Stage20ActivationAuthorizationEntity auth = authorization(request.stage20AuthorizationId());
        if (auth.isEmergencyStopEngaged() || auth.getStatus().startsWith("REVOKED_"))
            throw new IllegalStateException("Stage 21 cannot prepare a pilot from a stopped or revoked Stage 20 authorization");
        if (!auth.getExpiresAt().isAfter(Instant.now()))
            throw new IllegalStateException("Stage 21 requires a current Stage 20 authorization");

        Set<String> requested = normalizeCapabilities(request.capabilities());
        if (requested.isEmpty()) throw new IllegalArgumentException("At least one restricted pilot capability is required");
        if (!RESTRICTED_CAPABILITIES.containsAll(requested))
            throw new IllegalArgumentException("Stage 21 capability is outside the restricted physical-pilot allowlist");
        if (!auth.getCapabilities().containsAll(requested))
            throw new IllegalArgumentException("Stage 21 capability cannot widen the Stage 20 authorization");

        String canonical = auth.getId() + "|" + auth.getAuthorizationSha256() + "|" + auth.getTargetId().toLowerCase(Locale.ROOT)
                + "|" + auth.getAdapterId().toLowerCase(Locale.ROOT) + "|" + auth.getPackageSha256()
                + "|" + auth.getDeviceCertificateSha256() + "|" + String.join(",", new TreeSet<>(requested));
        String pilotSha = Stage18ProvisioningService.shaText(canonical);
        if (pilots.existsByPilotSha256(pilotSha))
            throw new IllegalStateException("An identical Stage 21 pilot plan already exists");
        return pilots.save(new Stage21PilotEntity(UUID.randomUUID(), auth.getId(), auth.getTargetId(), auth.getAdapterId(),
                auth.getAuthorizationSha256(), auth.getPackageSha256(), auth.getDeviceCertificateSha256(), requested, pilotSha));
    }

    @Transactional
    public Stage21PilotEvidenceEntity recordEvidence(UUID pilotId, EvidenceRequest request) {
        Stage21PilotEntity pilot = get(pilotId);
        if (request == null) throw new IllegalArgumentException("Stage 21 pilot evidence is required");
        String kind = token(request.kind(), "kind", 64).toUpperCase(Locale.ROOT);
        if (!REQUIRED_EVIDENCE.contains(kind)) throw new IllegalArgumentException("Unsupported Stage 21 evidence kind");
        String status = token(request.status(), "status", 16).toUpperCase(Locale.ROOT);
        if (!Set.of("PASS", "FAIL").contains(status)) throw new IllegalArgumentException("Stage 21 evidence status must be PASS or FAIL");
        String source = token(request.source(), "source", 100);
        String evidenceSha = sha(request.evidenceSha256(), "evidenceSha256");
        String subjectSha = request.subjectSha256() == null || request.subjectSha256().isBlank()
                ? null : sha(request.subjectSha256(), "subjectSha256");
        Instant observedAt = request.observedAt() == null ? Instant.now() : request.observedAt();
        if (observedAt.isAfter(Instant.now().plusSeconds(30)))
            throw new IllegalArgumentException("Stage 21 evidence cannot be future-dated");

        if ("PASS".equals(status) && request.measuredOnTarget()) {
            if ("DEVICE_IDENTITY".equals(kind) && !pilot.getDeviceCertificateSha256().equals(subjectSha))
                throw new IllegalArgumentException("Stage 21 device identity does not match the authorized certificate");
            if ("PACKAGE_INTEGRITY".equals(kind) && !pilot.getPackageSha256().equals(subjectSha))
                throw new IllegalArgumentException("Stage 21 package integrity does not match the authorized package");
            if ("STAGE20_LEASE_RECEIPT".equals(kind)) validateStage20Receipt(pilot, subjectSha);
        }

        return evidence.save(new Stage21PilotEvidenceEntity(UUID.randomUUID(), pilot.getId(), kind, status,
                request.measuredOnTarget(), source, subjectSha, evidenceSha, observedAt));
    }

    public Readiness readiness(UUID pilotId) {
        Stage21PilotEntity pilot = get(pilotId);
        Map<String, Stage21PilotEvidenceEntity> latest = latestByKind(pilotId);
        Instant cutoff = Instant.now().minus(EVIDENCE_MAX_AGE);
        List<String> missing = new ArrayList<>();
        int passed = 0;
        for (String kind : REQUIRED_EVIDENCE) {
            Stage21PilotEvidenceEntity item = latest.get(kind);
            boolean ok = item != null && "PASS".equals(item.getStatus()) && item.isMeasuredOnTarget()
                    && !item.getObservedAt().isBefore(cutoff);
            if (ok && "DEVICE_IDENTITY".equals(kind)) ok = pilot.getDeviceCertificateSha256().equals(item.getSubjectSha256());
            if (ok && "PACKAGE_INTEGRITY".equals(kind)) ok = pilot.getPackageSha256().equals(item.getSubjectSha256());
            if (ok && "STAGE20_LEASE_RECEIPT".equals(kind)) ok = hasValidStage20Receipt(pilot, item.getSubjectSha256());
            if (ok) passed++; else missing.add(kind);
        }
        int score = (int) Math.round((passed * 100.0) / REQUIRED_EVIDENCE.size());
        String state = passed == 0 ? "BLOCKED_PENDING_HARDWARE"
                : missing.isEmpty() ? "READY_FOR_OWNER_RESTRICTED_PILOT_REVIEW" : "HARDWARE_EVIDENCE_INCOMPLETE";
        return new Readiness(pilotId, state, score, passed, REQUIRED_EVIDENCE.size(), List.copyOf(missing),
                missing.isEmpty(), true, false, false, false);
    }

    public PilotReport report(UUID pilotId) {
        Stage21PilotEntity pilot = get(pilotId);
        Readiness readiness = readiness(pilotId);
        Map<String, Stage21PilotEvidenceEntity> latest = latestByKind(pilotId);
        StringBuilder canonical = new StringBuilder(pilot.getPilotSha256()).append('|').append(readiness.status())
                .append('|').append(readiness.score());
        for (String kind : REQUIRED_EVIDENCE) {
            Stage21PilotEvidenceEntity item = latest.get(kind);
            canonical.append('|').append(kind).append('=').append(item == null ? "missing" : item.getEvidenceSha256());
        }
        return new PilotReport(pilotId, pilot.getTargetId(), readiness.status(), readiness.score(),
                Stage18ProvisioningService.shaText(canonical.toString()), readiness.physicalEvidenceComplete(),
                false, false, false, false);
    }

    public Stage21PilotEntity get(UUID id) {
        if (id == null) throw new IllegalArgumentException("pilotId is required");
        return pilots.findById(id).orElseThrow(() -> new NoSuchElementException("Unknown Stage 21 pilot"));
    }
    public List<Stage21PilotEntity> pilots() { return pilots.findTop100ByOrderByCreatedAtDesc(); }
    public List<Stage21PilotEvidenceEntity> evidence() { return evidence.findTop200ByOrderByObservedAtDesc(); }
    public List<Stage21PilotEvidenceEntity> evidence(UUID pilotId) { get(pilotId); return evidence.findTop100ByPilotIdOrderByObservedAtDesc(pilotId); }

    private Map<String, Stage21PilotEvidenceEntity> latestByKind(UUID pilotId) {
        Map<String, Stage21PilotEvidenceEntity> latest = new HashMap<>();
        for (Stage21PilotEvidenceEntity item : evidence.findTop100ByPilotIdOrderByObservedAtDesc(pilotId))
            latest.putIfAbsent(item.getKind(), item);
        return latest;
    }

    private void validateStage20Receipt(Stage21PilotEntity pilot, String receiptSha) {
        if (receiptSha == null) throw new IllegalArgumentException("Stage 21 Stage 20 lease evidence requires receipt SHA-256");
        if (!hasValidStage20Receipt(pilot, receiptSha))
            throw new IllegalArgumentException("Stage 21 could not correlate a target-measured successful Stage 20 receipt");
    }

    private boolean hasValidStage20Receipt(Stage21PilotEntity pilot, String receiptSha) {
        if (receiptSha == null) return false;
        return receipts.findTop100ByAuthorizationIdOrderByObservedAtDesc(pilot.getStage20AuthorizationId()).stream()
                .anyMatch(r -> receiptSha.equals(r.getReceiptSha256()) && r.isMeasuredOnTarget()
                        && r.getStatus().startsWith("VERIFIED_TARGET_REPORTED")
                        && Set.of("SUCCESS", "HEALTHY").contains(r.getResultCode())
                        && pilot.getPackageSha256().equals(r.getPackageSha256())
                        && pilot.getDeviceCertificateSha256().equals(r.getDeviceCertificateSha256()));
    }

    private Stage20ActivationAuthorizationEntity authorization(UUID id) {
        return authorizations.findById(id).orElseThrow(() -> new NoSuchElementException("Unknown Stage 20 authorization"));
    }
    private Set<String> normalizeCapabilities(Set<String> values) {
        if (values == null) return Set.of();
        TreeSet<String> out = new TreeSet<>();
        for (String value : values) out.add(token(value, "capability", 48).toUpperCase(Locale.ROOT));
        return Set.copyOf(out);
    }
    private String sha(String value, String label) {
        if (value == null || !value.trim().toLowerCase(Locale.ROOT).matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException(label + " must be a SHA-256 value");
        return value.trim().toLowerCase(Locale.ROOT);
    }
    private String token(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String v = value.trim();
        if (v.length() > max || !v.matches("[A-Za-z0-9._:/-]{1," + max + "}"))
            throw new IllegalArgumentException(label + " is invalid");
        return v;
    }

    public record PilotRequest(UUID stage20AuthorizationId, Set<String> capabilities) {}
    public record EvidenceRequest(String kind, String status, boolean measuredOnTarget, String source,
                                  String subjectSha256, String evidenceSha256, Instant observedAt) {}
    public record Readiness(UUID pilotId, String status, int score, int passingEvidence, int requiredEvidence,
                            List<String> missingEvidence, boolean physicalEvidenceComplete, boolean hardwareRequired,
                            boolean ownerPilotActivationAllowed, boolean productionActivationAllowed,
                            boolean externalActionAttempted) {}
    public record PilotReport(UUID pilotId, String targetId, String status, int score, String reportSha256,
                              boolean physicalEvidenceComplete, boolean physicalPilotComplete,
                              boolean ownerPilotActivationAllowed, boolean productionActivationAllowed,
                              boolean externalActionAttempted) {}
}
