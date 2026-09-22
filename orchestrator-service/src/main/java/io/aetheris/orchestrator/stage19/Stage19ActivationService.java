package io.aetheris.orchestrator.stage19;

import io.aetheris.orchestrator.stage18.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class Stage19ActivationService {
    public static final Set<String> CANARY_CAPABILITIES = Set.of(
            "PROCESS_READ", "PC_TELEMETRY", "OLLAMA", "VAD", "STT", "TTS", "PRIVATE_TRANSPORT");
    private static final Duration EVIDENCE_MAX_AGE = Duration.ofMinutes(30);

    private final Stage19ActivationProposalRepository proposals;
    private final Stage19CanaryObservationRepository observations;
    private final Stage19ActivationAuditRepository audit;
    private final Stage18ProvisioningService stage18;

    public Stage19ActivationService(Stage19ActivationProposalRepository proposals,
                                    Stage19CanaryObservationRepository observations,
                                    Stage19ActivationAuditRepository audit,
                                    Stage18ProvisioningService stage18) {
        this.proposals = proposals; this.observations = observations; this.audit = audit; this.stage18 = stage18;
    }

    @Transactional
    public Stage19ActivationProposalEntity propose(ProposalRequest request) {
        if (request == null) throw new IllegalArgumentException("Stage 19 activation proposal is required");
        Stage18BootstrapManifestEntity manifest = stage18.latest(request.targetId());
        Stage18ProvisioningService.ReadinessAssessment readiness = stage18.readiness(manifest.getTargetId());
        Stage18ProvisioningService.EvidenceBundle bundle = stage18.bundle(manifest.getTargetId());
        if (!"READY_FOR_OWNER_ACTIVATION_REVIEW".equals(readiness.status()) || readiness.score() != 100)
            throw new IllegalStateException("Stage 18 target is not ready for owner activation review");
        if (!bundle.bundleSha256().equals(normalizeSha(request.evidenceBundleSha256(), "evidenceBundleSha256")))
            throw new IllegalArgumentException("Activation proposal must bind the current exact Stage 18 evidence bundle");
        assertFreshTargetEvidence(manifest);

        Set<String> requested = normalizeCapabilities(request.canaryCapabilities());
        if (requested.isEmpty()) throw new IllegalArgumentException("At least one canary capability is required");
        if (!CANARY_CAPABILITIES.containsAll(requested))
            throw new IllegalArgumentException("Stage 19 canary capability is outside the narrow canary allowlist");
        if (!manifest.getCapabilities().containsAll(requested))
            throw new IllegalArgumentException("Stage 19 canary capability was not signed into the Stage 18 target manifest");

        String canonical = canonical(manifest.getTargetId(), manifest.getAdapterId(), manifest.getManifestSha256(),
                bundle.bundleSha256(), manifest.getPackageSha256(), manifest.getDeviceCertificateSha256(), requested);
        String proposalSha = Stage18ProvisioningService.shaText(canonical);
        if (proposals.existsByProposalSha256(proposalSha))
            throw new IllegalStateException("An identical Stage 19 activation proposal already exists");
        Stage19ActivationProposalEntity entity = proposals.save(new Stage19ActivationProposalEntity(UUID.randomUUID(),
                manifest.getTargetId(), manifest.getAdapterId(), manifest.getManifestSha256(), bundle.bundleSha256(),
                manifest.getPackageSha256(), manifest.getDeviceCertificateSha256(), requested, proposalSha,
                "AWAITING_OWNER_APPROVAL"));
        appendAudit(entity, "PROPOSAL_CREATED", proposalSha + "|" + bundle.bundleSha256());
        return entity;
    }

    @Transactional
    public Stage19ActivationProposalEntity approve(UUID proposalId, ApprovalRequest request) {
        Stage19ActivationProposalEntity proposal = get(proposalId);
        if (!"AWAITING_OWNER_APPROVAL".equals(proposal.getStatus()))
            throw new IllegalStateException("Only an awaiting Stage 19 proposal can receive owner approval");
        if (request == null || request.approvedBy() == null || !"OWNER".equalsIgnoreCase(request.approvedBy().trim()))
            throw new IllegalArgumentException("Stage 19 activation approval must be explicitly issued by OWNER");
        int minutes = request.validForMinutes();
        if (minutes < 1 || minutes > 30) throw new IllegalArgumentException("Owner approval expiry must be between 1 and 30 minutes");
        revalidateExactTarget(proposal);
        proposal.approve("OWNER", Instant.now().plus(Duration.ofMinutes(minutes)));
        Stage19ActivationProposalEntity saved = proposals.save(proposal);
        appendAudit(saved, "OWNER_APPROVED", saved.getProposalSha256() + "|" + saved.getApprovalExpiresAt().toEpochMilli());
        return saved;
    }

    @Transactional
    public Stage19ActivationProposalEntity startCanary(UUID proposalId) {
        Stage19ActivationProposalEntity proposal = get(proposalId);
        if (!"OWNER_APPROVED_SIMULATION_ONLY".equals(proposal.getStatus()))
            throw new IllegalStateException("Stage 19 canary requires a current explicit owner approval");
        if (!proposal.isOwnerApproved() || proposal.getApprovalExpiresAt() == null || !proposal.getApprovalExpiresAt().isAfter(Instant.now()))
            throw new IllegalStateException("Stage 19 owner approval is missing or expired");
        revalidateExactTarget(proposal);
        proposal.startCanary();
        Stage19ActivationProposalEntity saved = proposals.save(proposal);
        appendAudit(saved, "CANARY_STARTED", saved.getProposalSha256() + "|simulation-only");
        return saved;
    }

    @Transactional
    public Stage19CanaryObservationEntity observe(UUID proposalId, ObservationRequest request) {
        Stage19ActivationProposalEntity proposal = get(proposalId);
        if (!"CANARY_ACTIVE_SIMULATION".equals(proposal.getStatus()))
            throw new IllegalStateException("Canary observations require an active Stage 19 simulation canary");
        if (request == null) throw new IllegalArgumentException("Canary observation is required");
        if (request.windowSeconds() < 60 || request.windowSeconds() > 3600)
            throw new IllegalArgumentException("Canary observation window must be between 60 and 3600 seconds");
        if (request.availabilityPct() < 0 || request.availabilityPct() > 100
                || request.errorRatePct() < 0 || request.errorRatePct() > 100
                || request.p95LatencyMs() <= 0 || request.crashCount() < 0)
            throw new IllegalArgumentException("Canary SLO measurement is invalid");
        String source = token(request.source(), "source", 80);
        String attestation = normalizeSha(request.attestationSha256(), "attestationSha256");
        Instant observedAt = request.observedAt() == null ? Instant.now() : request.observedAt();
        if (observedAt.isAfter(Instant.now().plusSeconds(30))) throw new IllegalArgumentException("Canary evidence cannot be future-dated");
        boolean pass = request.availabilityPct() >= 99.0 && request.errorRatePct() <= 1.0
                && request.p95LatencyMs() <= 2000 && request.crashCount() == 0;
        String status = pass ? (request.measuredOnTarget() ? "PASS_TARGET_REPORTED" : "PASS_SIMULATED") : "FAIL";
        Stage19CanaryObservationEntity item = observations.save(new Stage19CanaryObservationEntity(UUID.randomUUID(),
                proposal.getId(), proposal.getTargetId(), request.windowSeconds(), request.availabilityPct(),
                request.errorRatePct(), request.p95LatencyMs(), request.crashCount(), status,
                request.measuredOnTarget(), source, attestation, observedAt));
        appendAudit(proposal, pass ? "CANARY_OBSERVATION_PASS" : "CANARY_OBSERVATION_FAIL",
                status + "|" + attestation + "|" + request.windowSeconds());
        if (!pass) {
            proposal.rehearseRollback();
            proposals.save(proposal);
            appendAudit(proposal, "AUTO_ROLLBACK_REHEARSED", proposal.getProposalSha256() + "|failed-canary-evidence");
        }
        return item;
    }

    public CanaryAssessment assess(UUID proposalId) {
        Stage19ActivationProposalEntity proposal = get(proposalId);
        List<Stage19CanaryObservationEntity> items = observations.findTop100ByProposalIdOrderByObservedAtDesc(proposalId);
        long passes = items.stream().filter(x -> x.getStatus().startsWith("PASS_")).count();
        long failures = items.stream().filter(x -> "FAIL".equals(x.getStatus())).count();
        int totalSeconds = items.stream().filter(x -> x.getStatus().startsWith("PASS_")).mapToInt(Stage19CanaryObservationEntity::getWindowSeconds).sum();
        boolean healthy = passes >= 3 && failures == 0 && totalSeconds >= 300;
        String status = failures > 0 ? "ROLLBACK_REQUIRED_SIMULATION"
                : healthy ? "CANARY_HEALTHY_SIMULATION" : "MORE_OBSERVATION_REQUIRED";
        return new CanaryAssessment(proposalId, status, passes, failures, totalSeconds, healthy,
                false, false, false);
    }

    @Transactional
    public Stage19ActivationProposalEntity validateCanary(UUID proposalId) {
        Stage19ActivationProposalEntity proposal = get(proposalId);
        if (!"CANARY_ACTIVE_SIMULATION".equals(proposal.getStatus()))
            throw new IllegalStateException("Only an active simulation canary can be validated");
        CanaryAssessment assessment = assess(proposalId);
        if (!assessment.healthy()) throw new IllegalStateException("Stage 19 canary has not completed the healthy observation window");
        revalidateExactTarget(proposal);
        proposal.markValidated();
        Stage19ActivationProposalEntity saved = proposals.save(proposal);
        appendAudit(saved, "CANARY_VALIDATED", saved.getProposalSha256() + "|owner-activation-still-disabled");
        return saved;
    }

    @Transactional
    public Stage19ActivationProposalEntity revoke(UUID proposalId, ReasonRequest request) {
        Stage19ActivationProposalEntity proposal = get(proposalId);
        String reason = reason(request);
        proposal.revoke();
        Stage19ActivationProposalEntity saved = proposals.save(proposal);
        appendAudit(saved, "CANARY_REVOKED", reason);
        return saved;
    }

    @Transactional
    public Stage19ActivationProposalEntity rehearseRollback(UUID proposalId, ReasonRequest request) {
        Stage19ActivationProposalEntity proposal = get(proposalId);
        String reason = reason(request);
        proposal.rehearseRollback();
        Stage19ActivationProposalEntity saved = proposals.save(proposal);
        appendAudit(saved, "ROLLBACK_REHEARSED", reason);
        return saved;
    }

    @Transactional
    public AttestationRefresh refreshAttestation(UUID proposalId) {
        Stage19ActivationProposalEntity proposal = get(proposalId);
        revalidateExactTarget(proposal);
        String sha = Stage18ProvisioningService.shaText(proposal.getProposalSha256() + "|"
                + proposal.getEvidenceBundleSha256() + "|" + proposal.getManifestSha256() + "|"
                + proposal.getPackageSha256() + "|" + proposal.getDeviceCertificateSha256());
        proposal.refreshAttestation(sha);
        proposals.save(proposal);
        appendAudit(proposal, "ATTESTATION_REFRESH_VERIFIED", sha);
        return new AttestationRefresh(proposalId, "ATTESTATION_REFRESH_VERIFIED_SIMULATION_ONLY", sha,
                proposal.getAttestationRefreshedAt(), false, false, false);
    }

    public Stage19ActivationProposalEntity get(UUID id) {
        if (id == null) throw new IllegalArgumentException("proposalId is required");
        return proposals.findById(id).orElseThrow(() -> new NoSuchElementException("Unknown Stage 19 activation proposal"));
    }
    public List<Stage19ActivationProposalEntity> proposals() { return proposals.findTop100ByOrderByCreatedAtDesc(); }
    public List<Stage19CanaryObservationEntity> observations() { return observations.findTop200ByOrderByObservedAtDesc(); }
    public List<Stage19ActivationAuditEntity> audit() { return audit.findTop200ByOrderByObservedAtDesc(); }
    public List<Stage19ActivationAuditEntity> audit(UUID proposalId) { return audit.findTop100ByProposalIdOrderByObservedAtDesc(proposalId); }

    private void revalidateExactTarget(Stage19ActivationProposalEntity proposal) {
        Stage18BootstrapManifestEntity manifest = stage18.latest(proposal.getTargetId());
        Stage18ProvisioningService.ReadinessAssessment readiness = stage18.readiness(proposal.getTargetId());
        Stage18ProvisioningService.EvidenceBundle bundle = stage18.bundle(proposal.getTargetId());
        if (!manifest.getAdapterId().equals(proposal.getAdapterId())
                || !manifest.getManifestSha256().equals(proposal.getManifestSha256())
                || !manifest.getPackageSha256().equals(proposal.getPackageSha256())
                || !manifest.getDeviceCertificateSha256().equals(proposal.getDeviceCertificateSha256()))
            throw new IllegalStateException("Stage 19 exact target/package/certificate/adapter binding changed");
        if (!bundle.bundleSha256().equals(proposal.getEvidenceBundleSha256()))
            throw new IllegalStateException("Stage 18 evidence bundle changed after Stage 19 proposal creation");
        if (!"READY_FOR_OWNER_ACTIVATION_REVIEW".equals(readiness.status()) || readiness.score() != 100)
            throw new IllegalStateException("Stage 18 target readiness is no longer complete");
        assertFreshTargetEvidence(manifest);
    }

    private void assertFreshTargetEvidence(Stage18BootstrapManifestEntity manifest) {
        Map<String, Stage18TargetEvidenceEntity> latest = new HashMap<>();
        for (Stage18TargetEvidenceEntity e : stage18.evidenceForTarget(manifest.getTargetId()))
            latest.putIfAbsent(e.getKind() + "|" + e.getComponent(), e);
        List<String> required = new ArrayList<>(List.of(
                "DEVICE_IDENTITY|system", "PACKAGE_INTEGRITY|system", "OS_VAULT|system",
                "RESOURCE_BENCHMARK|resource", "SPEECH_BENCHMARK|speech",
                "LOCAL_MODEL_BENCHMARK|local_model", "PRIVATE_TRANSPORT|system"));
        for (String alias : manifest.getProviderAliases()) required.add("PROVIDER_BINDING|" + alias);
        Instant cutoff = Instant.now().minus(EVIDENCE_MAX_AGE);
        for (String key : required) {
            Stage18TargetEvidenceEntity e = latest.get(key);
            if (e == null || !e.isMeasuredOnTarget() || !"PASS".equals(e.getStatus()) || e.getObservedAt().isBefore(cutoff))
                throw new IllegalStateException("Stage 19 requires fresh target-measured Stage 18 evidence for " + key);
        }
    }

    private void appendAudit(Stage19ActivationProposalEntity proposal, String event, String detail) {
        audit.save(new Stage19ActivationAuditEntity(UUID.randomUUID(), proposal.getId(), proposal.getTargetId(),
                event, proposal.getStatus(), Stage18ProvisioningService.shaText(detail)));
    }
    private Set<String> normalizeCapabilities(Set<String> values) {
        if (values == null) return Set.of();
        TreeSet<String> out = new TreeSet<>();
        for (String value : values) out.add(token(value, "canaryCapability", 48).toUpperCase(Locale.ROOT));
        return Set.copyOf(out);
    }
    private String normalizeSha(String value, String label) {
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
    private String reason(ReasonRequest request) {
        if (request == null || request.reason() == null || request.reason().isBlank()) throw new IllegalArgumentException("reason is required");
        String v = request.reason().trim();
        if (v.length() > 300) throw new IllegalArgumentException("reason is too long");
        String lower = v.toLowerCase(Locale.ROOT);
        if (lower.contains("password=") || lower.contains("api_key=") || lower.contains("authorization:") || lower.contains("bearer "))
            throw new IllegalArgumentException("reason appears to contain secret material");
        return v;
    }
    public static String canonical(String targetId, String adapterId, String manifestSha256,
                                   String evidenceBundleSha256, String packageSha256,
                                   String deviceCertificateSha256, Set<String> capabilities) {
        return targetId.toLowerCase(Locale.ROOT) + "|" + adapterId.toLowerCase(Locale.ROOT) + "|"
                + manifestSha256.toLowerCase(Locale.ROOT) + "|" + evidenceBundleSha256.toLowerCase(Locale.ROOT) + "|"
                + packageSha256.toLowerCase(Locale.ROOT) + "|" + deviceCertificateSha256.toLowerCase(Locale.ROOT) + "|"
                + String.join(",", new TreeSet<>(capabilities));
    }

    public record ProposalRequest(String targetId, String evidenceBundleSha256, Set<String> canaryCapabilities) {}
    public record ApprovalRequest(String approvedBy, int validForMinutes) {}
    public record ObservationRequest(int windowSeconds, double availabilityPct, double errorRatePct,
                                     double p95LatencyMs, int crashCount, boolean measuredOnTarget,
                                     String source, String attestationSha256, Instant observedAt) {}
    public record ReasonRequest(String reason) {}
    public record CanaryAssessment(UUID proposalId, String status, long passingObservations, long failingObservations,
                                   int passingWindowSeconds, boolean healthy, boolean productionActivationAllowed,
                                   boolean targetMutated, boolean externalActionAttempted) {}
    public record AttestationRefresh(UUID proposalId, String status, String attestationSha256, Instant refreshedAt,
                                     boolean productionActivationAllowed, boolean targetMutated,
                                     boolean externalActionAttempted) {}
}
