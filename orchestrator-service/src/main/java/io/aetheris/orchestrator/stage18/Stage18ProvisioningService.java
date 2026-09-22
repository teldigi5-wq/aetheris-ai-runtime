package io.aetheris.orchestrator.stage18;

import io.aetheris.orchestrator.stage16.*;
import io.aetheris.orchestrator.stage17.Stage17CertificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class Stage18ProvisioningService {
    public static final Set<String> TARGET_CAPABILITIES = Set.of(
            "PROCESS_READ", "APP_LAUNCH", "FILE_OPEN", "PC_TELEMETRY", "OLLAMA",
            "DPAPI", "VAD", "STT", "TTS", "PRIVATE_TRANSPORT");
    public static final Set<String> EVIDENCE_KINDS = Set.of(
            "DEVICE_IDENTITY", "OS_VAULT", "PACKAGE_INTEGRITY", "RESOURCE_BENCHMARK",
            "SPEECH_BENCHMARK", "LOCAL_MODEL_BENCHMARK", "PRIVATE_TRANSPORT", "PROVIDER_BINDING");
    private static final Set<String> GENERIC_EVIDENCE_KINDS = Set.of(
            "DEVICE_IDENTITY", "OS_VAULT", "PACKAGE_INTEGRITY", "PRIVATE_TRANSPORT", "PROVIDER_BINDING");
    private static final Set<String> FORBIDDEN = Set.of(
            "ARBITRARY_SHELL", "ADMIN_BYPASS", "LIVE_ORDER", "WITHDRAWAL", "TRANSFER",
            "DISABLE_SECURITY", "UAC_BYPASS", "CREDENTIAL_EXPORT");
    private static final Duration MANIFEST_MAX_AGE = Duration.ofMinutes(10);

    private final Stage18BootstrapManifestRepository manifests;
    private final Stage18TargetEvidenceRepository evidence;
    private final Stage16CommandTrustService trust;
    private final Stage16AdapterRegistryService adapters;
    private final Stage17CertificationService certifications;

    public Stage18ProvisioningService(Stage18BootstrapManifestRepository manifests,
                                      Stage18TargetEvidenceRepository evidence,
                                      Stage16CommandTrustService trust,
                                      Stage16AdapterRegistryService adapters,
                                      Stage17CertificationService certifications) {
        this.manifests = manifests; this.evidence = evidence; this.trust = trust;
        this.adapters = adapters; this.certifications = certifications;
    }

    @Transactional
    public Stage18BootstrapManifestEntity register(SignedBootstrapManifestRequest request) {
        if (request == null) throw new IllegalArgumentException("Stage 18 bootstrap manifest is required");
        String targetId = token(request.targetId(), "targetId", 80).toLowerCase(Locale.ROOT);
        String adapterId = token(request.adapterId(), "adapterId", 80).toLowerCase(Locale.ROOT);
        Stage16AdapterEntity adapter = adapters.get(adapterId);
        if (!adapter.isEnabled() || !adapter.isSimulationOnly())
            throw new IllegalStateException("Stage 18 repository mode requires an enabled Stage 16 simulation adapter");
        String platform = token(request.platform(), "platform", 24).toUpperCase(Locale.ROOT);
        if (!"WINDOWS".equals(platform)) throw new IllegalArgumentException("Stage 18 currently supports Windows target manifests only");
        if (request.minimumRamMb() < 4096 || request.minimumRamMb() > 262144)
            throw new IllegalArgumentException("minimumRamMb is outside the supported range");
        if (request.minimumVramMb() < 0 || request.minimumVramMb() > 65536)
            throw new IllegalArgumentException("minimumVramMb is outside the supported range");
        String packageSha = sha(request.packageSha256(), "packageSha256");
        String certSha = sha(request.deviceCertificateSha256(), "deviceCertificateSha256");
        Set<String> capabilities = normalizeUpper(request.capabilities(), "capability", 48);
        if (capabilities.isEmpty()) throw new IllegalArgumentException("At least one target capability is required");
        if (!TARGET_CAPABILITIES.containsAll(capabilities) || capabilities.stream().anyMatch(FORBIDDEN::contains))
            throw new IllegalArgumentException("Bootstrap manifest requests unsupported or forbidden target authority");
        Set<String> providers = normalizeLower(request.providerAliases(), "providerAlias", 80);
        String signerKeyId = token(request.signerKeyId(), "signerKeyId", 80).toLowerCase(Locale.ROOT);
        Instant issuedAt = Objects.requireNonNull(request.issuedAt(), "issuedAt is required");
        Instant now = Instant.now();
        if (issuedAt.isAfter(now.plusSeconds(30))) throw new IllegalArgumentException("Bootstrap manifest is materially future-dated");
        if (issuedAt.isBefore(now.minus(MANIFEST_MAX_AGE))) throw new IllegalArgumentException("Bootstrap manifest is older than ten minutes");

        String canonical = canonical(targetId, adapterId, platform, packageSha, certSha,
                request.minimumRamMb(), request.minimumVramMb(), capabilities, providers, issuedAt);
        String manifestSha = sha(request.manifestSha256(), "manifestSha256");
        if (!manifestSha.equals(shaText(canonical))) throw new IllegalArgumentException("Bootstrap manifest SHA-256 does not match canonical content");
        String signature = normalizeBase64(request.signatureBase64());
        if (!trust.verify(signerKeyId, canonical, signature))
            throw new IllegalArgumentException("Stage 18 bootstrap manifest signature verification failed");
        if (manifests.existsByTargetIdAndManifestSha256(targetId, manifestSha))
            throw new IllegalStateException("This Stage 18 target manifest revision already exists");
        return manifests.save(new Stage18BootstrapManifestEntity(UUID.randomUUID(), targetId, adapterId, platform,
                packageSha, certSha, request.minimumRamMb(), request.minimumVramMb(), capabilities, providers,
                signerKeyId, shaBytes(Base64.getDecoder().decode(signature)), manifestSha, issuedAt));
    }

    @Transactional
    public Stage18TargetEvidenceEntity recordEvidence(TargetEvidenceRequest request) {
        if (request == null) throw new IllegalArgumentException("Target evidence is required");
        Stage18BootstrapManifestEntity manifest = latest(request.targetId());
        String kind = token(request.kind(), "kind", 48).toUpperCase(Locale.ROOT);
        if (!GENERIC_EVIDENCE_KINDS.contains(kind))
            throw new IllegalArgumentException("Use the Stage 18 benchmark endpoint for resource, speech and local-model benchmark evidence");
        String component = token(request.component() == null ? "system" : request.component(), "component", 100).toLowerCase(Locale.ROOT);
        if ("PROVIDER_BINDING".equals(kind) && !manifest.getProviderAliases().contains(component))
            throw new IllegalArgumentException("Provider binding evidence does not match a provider alias in the signed bootstrap manifest");
        String subjectSha = request.subjectSha256() == null || request.subjectSha256().isBlank()
                ? null : sha(request.subjectSha256(), "subjectSha256");
        String attestation = sha(request.attestationSha256(), "attestationSha256");
        String source = token(request.source(), "source", 80);
        String detail = detail(request.detail());
        Instant observedAt = request.observedAt() == null ? Instant.now() : request.observedAt();
        if (observedAt.isAfter(Instant.now().plusSeconds(30))) throw new IllegalArgumentException("Evidence cannot be future-dated");
        String status = request.passed() ? (request.measuredOnTarget() ? "PASS" : "PASS_SIMULATED") : "FAIL";
        return evidence.save(new Stage18TargetEvidenceEntity(UUID.randomUUID(), manifest.getTargetId(), kind, component,
                status, request.measuredOnTarget(), subjectSha, attestation, source, detail, observedAt));
    }

    @Transactional
    public Stage18TargetEvidenceEntity recordBenchmark(BenchmarkRequest request) {
        if (request == null) throw new IllegalArgumentException("Benchmark evidence is required");
        Stage18BootstrapManifestEntity manifest = latest(request.targetId());
        String type = token(request.type(), "type", 40).toUpperCase(Locale.ROOT);
        String kind;
        boolean passed;
        String detail;
        switch (type) {
            case "RESOURCE" -> {
                kind = "RESOURCE_BENCHMARK";
                passed = request.ramMb() >= manifest.getMinimumRamMb() && request.vramMb() >= manifest.getMinimumVramMb()
                        && request.crashCount() == 0;
                detail = "ramMb=" + request.ramMb() + ";vramMb=" + request.vramMb() + ";crashes=" + request.crashCount();
            }
            case "SPEECH" -> {
                kind = "SPEECH_BENCHMARK";
                passed = request.primaryLatencyMs() > 0 && request.primaryLatencyMs() <= 900
                        && request.secondaryLatencyMs() > 0 && request.secondaryLatencyMs() <= 300
                        && request.crashCount() == 0;
                detail = "sttP95Ms=" + request.primaryLatencyMs() + ";bargeInP95Ms=" + request.secondaryLatencyMs()
                        + ";crashes=" + request.crashCount();
            }
            case "LOCAL_MODEL" -> {
                kind = "LOCAL_MODEL_BENCHMARK";
                passed = request.primaryLatencyMs() > 0 && request.primaryLatencyMs() <= 2500
                        && request.throughputPerSecond() >= 8.0 && request.crashCount() == 0;
                detail = "ttftP95Ms=" + request.primaryLatencyMs() + ";tokensPerSec=" + request.throughputPerSecond()
                        + ";crashes=" + request.crashCount();
            }
            default -> throw new IllegalArgumentException("Unsupported Stage 18 benchmark type");
        }
        String status = passed ? (request.measuredOnTarget() ? "PASS" : "PASS_SIMULATED") : "FAIL";
        return evidence.save(new Stage18TargetEvidenceEntity(UUID.randomUUID(), manifest.getTargetId(), kind,
                type.toLowerCase(Locale.ROOT), status, request.measuredOnTarget(), null,
                sha(request.attestationSha256(), "attestationSha256"), token(request.source(), "source", 80),
                detail, request.observedAt() == null ? Instant.now() : request.observedAt()));
    }

    public Stage18BootstrapManifestEntity latest(String targetId) {
        String id = token(targetId, "targetId", 80).toLowerCase(Locale.ROOT);
        return manifests.findTop20ByTargetIdOrderByCreatedAtDesc(id).stream().findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown Stage 18 target manifest"));
    }

    public List<Stage18BootstrapManifestEntity> manifests() { return manifests.findTop50ByOrderByCreatedAtDesc(); }
    public List<Stage18TargetEvidenceEntity> evidence() { return evidence.findTop200ByOrderByObservedAtDesc(); }
    public List<Stage18TargetEvidenceEntity> evidenceForTarget(String targetId) {
        return evidence.findTop200ByTargetIdOrderByObservedAtDesc(token(targetId, "targetId", 80).toLowerCase(Locale.ROOT));
    }

    public ReadinessAssessment readiness(String targetId) {
        Stage18BootstrapManifestEntity manifest = latest(targetId);
        List<Stage18TargetEvidenceEntity> all = evidenceForTarget(manifest.getTargetId());
        Map<String, Stage18TargetEvidenceEntity> latest = new LinkedHashMap<>();
        for (Stage18TargetEvidenceEntity item : all) latest.putIfAbsent(key(item.getKind(), item.getComponent()), item);
        List<String> blockers = new ArrayList<>();
        int score = 10;

        boolean stage17Certified = certifications.compatibilityMatrix().adapters().stream()
                .anyMatch(row -> row.adapterId().equals(manifest.getAdapterId())
                        && "CERTIFIED_SIMULATION_ONLY".equals(row.certificationStatus())
                        && row.certificationScore() == 100
                        && row.certificationExpiresAt() != null
                        && row.certificationExpiresAt().isAfter(Instant.now()));
        if (stage17Certified) score += 10; else blockers.add("current Stage 17 simulation certification is required for adapter " + manifest.getAdapterId());

        score += gate(latest, "DEVICE_IDENTITY", "system", manifest.getDeviceCertificateSha256(), blockers,
                "target-measured device identity must match the signed certificate fingerprint");
        score += gate(latest, "PACKAGE_INTEGRITY", "system", manifest.getPackageSha256(), blockers,
                "target-measured host-agent package SHA-256 must match the signed bootstrap manifest");
        score += gate(latest, "OS_VAULT", "system", null, blockers,
                "target-measured DPAPI/OS-vault binding evidence is required");
        score += gate(latest, "RESOURCE_BENCHMARK", "resource", null, blockers,
                "target-measured RAM/VRAM resource benchmark must pass");
        score += gate(latest, "SPEECH_BENCHMARK", "speech", null, blockers,
                "target-measured speech/VAD benchmark must pass");
        score += gate(latest, "LOCAL_MODEL_BENCHMARK", "local_model", null, blockers,
                "target-measured local-model benchmark must pass");
        score += gate(latest, "PRIVATE_TRANSPORT", "system", manifest.getDeviceCertificateSha256(), blockers,
                "target-measured private-transport evidence must be certificate-bound");

        boolean providersReady = true;
        for (String alias : manifest.getProviderAliases()) {
            Stage18TargetEvidenceEntity e = latest.get(key("PROVIDER_BINDING", alias));
            if (!passingTargetEvidence(e, null)) {
                providersReady = false;
                blockers.add("target-measured vault-only provider binding required for alias " + alias);
            }
        }
        if (providersReady) score += 10;

        score = Math.min(score, 100);
        String status = score == 100 && blockers.isEmpty()
                ? "READY_FOR_OWNER_ACTIVATION_REVIEW"
                : stage17Certified ? "TARGET_EVIDENCE_REQUIRED" : "STAGE17_CERTIFICATION_REQUIRED";
        return new ReadinessAssessment(manifest.getTargetId(), status, score, List.copyOf(blockers),
                stage17Certified, false, false, "Stage 18 never activates a physical adapter automatically");
    }

    public EvidenceBundle bundle(String targetId) {
        Stage18BootstrapManifestEntity manifest = latest(targetId);
        ReadinessAssessment readiness = readiness(targetId);
        List<Stage18TargetEvidenceEntity> items = evidenceForTarget(targetId);
        StringBuilder canonical = new StringBuilder(manifest.getTargetId()).append('|').append(manifest.getManifestSha256())
                .append('|').append(readiness.score());
        items.stream().sorted(Comparator.comparing(Stage18TargetEvidenceEntity::getKind)
                        .thenComparing(Stage18TargetEvidenceEntity::getComponent)
                        .thenComparing(Stage18TargetEvidenceEntity::getObservedAt))
                .forEach(e -> canonical.append('|').append(e.getKind()).append(':').append(e.getComponent())
                        .append(':').append(e.getStatus()).append(':').append(e.getAttestationSha256()));
        return new EvidenceBundle(manifest.getTargetId(), manifest.getManifestSha256(), readiness.status(),
                readiness.score(), items.size(), shaText(canonical.toString()), false, false);
    }

    public ProvisioningRehearsal rehearse(String targetId) {
        ReadinessAssessment readiness = readiness(targetId);
        List<String> steps = List.of(
                "verify signed bootstrap manifest", "verify Stage 17 simulation certification",
                "verify exact device certificate fingerprint", "verify host-agent package integrity",
                "verify DPAPI/OS-vault binding", "verify resource/model/speech benchmarks",
                "verify private transport", "verify provider aliases through vault metadata",
                "assemble evidence bundle", "stop for owner activation review");
        return new ProvisioningRehearsal(readiness.targetId(), "SIMULATION_ONLY_REHEARSAL",
                readiness.status(), readiness.score(), steps, false, false, false);
    }

    private int gate(Map<String, Stage18TargetEvidenceEntity> latest, String kind, String component,
                     String expectedSubjectSha, List<String> blockers, String blocker) {
        Stage18TargetEvidenceEntity item = latest.get(key(kind, component));
        if (passingTargetEvidence(item, expectedSubjectSha)) return 10;
        blockers.add(blocker); return 0;
    }

    private boolean passingTargetEvidence(Stage18TargetEvidenceEntity item, String expectedSubjectSha) {
        if (item == null || !item.isMeasuredOnTarget() || !"PASS".equals(item.getStatus())) return false;
        return expectedSubjectSha == null || expectedSubjectSha.equals(item.getSubjectSha256());
    }

    private String key(String kind, String component) { return kind + "|" + component; }
    private Set<String> normalizeUpper(Set<String> values, String label, int max) {
        if (values == null) return Set.of();
        TreeSet<String> out = new TreeSet<>();
        for (String value : values) out.add(token(value, label, max).toUpperCase(Locale.ROOT));
        return Set.copyOf(out);
    }
    private Set<String> normalizeLower(Set<String> values, String label, int max) {
        if (values == null) return Set.of();
        TreeSet<String> out = new TreeSet<>();
        for (String value : values) out.add(token(value, label, max).toLowerCase(Locale.ROOT));
        return Set.copyOf(out);
    }
    private String token(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String v = value.trim();
        if (v.length() > max || !v.matches("[A-Za-z0-9._:/-]{1," + max + "}"))
            throw new IllegalArgumentException(label + " is invalid");
        return v;
    }
    private String detail(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("detail is required");
        String v = value.trim();
        if (v.length() > 2000) throw new IllegalArgumentException("detail is too long");
        String lower = v.toLowerCase(Locale.ROOT);
        for (String marker : List.of("password=", "api_key=", "api-key=", "api_secret=", "authorization:", "bearer ", "private_key="))
            if (lower.contains(marker)) throw new IllegalArgumentException("Stage 18 evidence detail appears to contain secret material");
        return v;
    }
    private String sha(String value, String label) {
        if (value == null || !value.trim().toLowerCase(Locale.ROOT).matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException(label + " must be a SHA-256 value");
        return value.trim().toLowerCase(Locale.ROOT);
    }
    private String normalizeBase64(String value) {
        if (value == null || value.isBlank() || value.length() > 512) throw new IllegalArgumentException("signatureBase64 is invalid");
        try { return Base64.getEncoder().encodeToString(Base64.getDecoder().decode(value.trim())); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("signatureBase64 must be valid Base64"); }
    }
    private static String sorted(Set<String> values) { return String.join(",", values == null ? Set.of() : new TreeSet<>(values)); }
    public static String canonical(String targetId, String adapterId, String platform, String packageSha256,
                                   String deviceCertificateSha256, int minimumRamMb, int minimumVramMb,
                                   Set<String> capabilities, Set<String> providerAliases, Instant issuedAt) {
        return targetId.toLowerCase(Locale.ROOT) + "|" + adapterId.toLowerCase(Locale.ROOT) + "|"
                + platform.toUpperCase(Locale.ROOT) + "|" + packageSha256.toLowerCase(Locale.ROOT) + "|"
                + deviceCertificateSha256.toLowerCase(Locale.ROOT) + "|" + minimumRamMb + "|" + minimumVramMb + "|"
                + sorted(capabilities).toUpperCase(Locale.ROOT) + "|" + sorted(providerAliases).toLowerCase(Locale.ROOT)
                + "|" + issuedAt.toEpochMilli();
    }
    public static String shaText(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private String shaBytes(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public record SignedBootstrapManifestRequest(String targetId, String adapterId, String platform,
                                                  String packageSha256, String deviceCertificateSha256,
                                                  int minimumRamMb, int minimumVramMb, Set<String> capabilities,
                                                  Set<String> providerAliases, Instant issuedAt, String signerKeyId,
                                                  String manifestSha256, String signatureBase64) {}
    public record TargetEvidenceRequest(String targetId, String kind, String component, boolean passed,
                                        boolean measuredOnTarget, String subjectSha256, String attestationSha256,
                                        String source, String detail, Instant observedAt) {}
    public record BenchmarkRequest(String targetId, String type, boolean measuredOnTarget, long ramMb, long vramMb,
                                   double primaryLatencyMs, double secondaryLatencyMs, double throughputPerSecond,
                                   int crashCount, String attestationSha256, String source, Instant observedAt) {}
    public record ReadinessAssessment(String targetId, String status, int score, List<String> blockers,
                                      boolean stage17Certified, boolean productionActivationAllowed,
                                      boolean externalActionAttempted, String detail) {}
    public record EvidenceBundle(String targetId, String manifestSha256, String readinessStatus, int readinessScore,
                                 int evidenceCount, String bundleSha256, boolean productionActivationAllowed,
                                 boolean externalActionAttempted) {}
    public record ProvisioningRehearsal(String targetId, String status, String readinessStatus, int readinessScore,
                                        List<String> steps, boolean targetMutated, boolean productionActivationAllowed,
                                        boolean externalActionAttempted) {}
}
