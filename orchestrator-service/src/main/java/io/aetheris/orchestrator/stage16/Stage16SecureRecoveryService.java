package io.aetheris.orchestrator.stage16;

import io.aetheris.orchestrator.stage15.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class Stage16SecureRecoveryService {
    private static final Duration MAX_ENVELOPE_LIFETIME = Duration.ofMinutes(5);
    private static final Duration MAX_CLOCK_SKEW = Duration.ofSeconds(30);
    private final Stage16ExecutionEnvelopeRepository envelopes;
    private final Stage16AdapterRegistryService adapters;
    private final Stage16CommandTrustService trust;
    private final Stage16RecoverySandboxService sandbox;
    private final Stage15RecoveryService recovery;
    private final Stage15ReliabilityService reliability;
    private final Stage15ServiceCatalogService catalog;

    public Stage16SecureRecoveryService(Stage16ExecutionEnvelopeRepository envelopes,
                                        Stage16AdapterRegistryService adapters,
                                        Stage16CommandTrustService trust,
                                        Stage16RecoverySandboxService sandbox,
                                        Stage15RecoveryService recovery,
                                        Stage15ReliabilityService reliability,
                                        Stage15ServiceCatalogService catalog) {
        this.envelopes = envelopes; this.adapters = adapters; this.trust = trust; this.sandbox = sandbox;
        this.recovery = recovery; this.reliability = reliability; this.catalog = catalog;
    }

    @Transactional
    public Stage16ExecutionEnvelopeEntity admit(SignedEnvelopeRequest request) {
        if (request == null || request.planId() == null) throw new IllegalArgumentException("Signed execution envelope is required");
        Stage15RecoveryPlanEntity plan = recovery.get(request.planId());
        if (plan.getApprovalId() == null || !"AUTHORIZED_PENDING_TARGET_EVIDENCE".equals(plan.getStatus()))
            throw new IllegalStateException("Stage 15 recovery plan must be exactly owner-authorized and pending target evidence");

        Stage15ServiceCatalogEntity service = catalog.get(plan.getServiceId());
        Stage15ReliabilityService.ReliabilityAssessment assessment = reliability.assess(plan.getServiceId());
        if ("ERROR_BUDGET_EXHAUSTED".equals(assessment.status()))
            throw new IllegalStateException("Error budget is exhausted; Stage 16 execution admission is blocked");
        if (assessment.maintenanceActive())
            throw new IllegalStateException("Service is inside a maintenance window; Stage 16 execution admission is blocked");

        Stage16AdapterEntity adapter = adapters.get(request.adapterId());
        if (!adapter.isEnabled() || !adapter.isSimulationOnly())
            throw new IllegalStateException("Only enabled simulation-only Stage 16 adapters are admitted before target validation");
        String action = token(request.action(), "action", 48).toUpperCase(Locale.ROOT);
        String target = token(request.target(), "target", 80).toLowerCase(Locale.ROOT);
        if (!action.equals(plan.getAction())) throw new IllegalArgumentException("Envelope action does not match Stage 15 recovery plan");
        if (!target.equals(plan.getServiceId())) throw new IllegalArgumentException("Envelope target does not match Stage 15 recovery service");
        if (!adapter.getCapabilities().contains(action)) throw new IllegalArgumentException("Adapter lacks the requested bounded capability");
        if (Stage15ServiceCatalogService.FORBIDDEN_ACTIONS.contains(action)) throw new IllegalArgumentException("Forbidden recovery authority requested");
        if (!service.getAllowedActions().contains(action)) throw new IllegalArgumentException("Service catalog does not allow this action");

        String nonce = nonce(request.nonce());
        if (envelopes.existsByNonce(nonce)) throw new IllegalArgumentException("Replay detected: Stage 16 nonce already exists");
        Instant now = Instant.now();
        Instant issuedAt = Objects.requireNonNull(request.issuedAt(), "issuedAt is required");
        Instant expiresAt = Objects.requireNonNull(request.expiresAt(), "expiresAt is required");
        if (issuedAt.isAfter(now.plus(MAX_CLOCK_SKEW))) throw new IllegalArgumentException("Envelope is materially future-dated");
        if (issuedAt.isBefore(now.minus(MAX_ENVELOPE_LIFETIME).minus(MAX_CLOCK_SKEW))) throw new IllegalArgumentException("Envelope is too old");
        if (!expiresAt.isAfter(issuedAt)) throw new IllegalArgumentException("Envelope expiry must be after issuance");
        if (Duration.between(issuedAt, expiresAt).compareTo(MAX_ENVELOPE_LIFETIME) > 0)
            throw new IllegalArgumentException("Envelope lifetime exceeds five minutes");
        if (!expiresAt.isAfter(now)) throw new IllegalArgumentException("Envelope has expired");
        String planSha = sha(request.planSha256(), "planSha256");
        if (!planSha.equals(plan.getPlanSha256())) throw new IllegalArgumentException("Envelope plan SHA does not match Stage 15 plan");
        String signerKeyId = token(request.signerKeyId(), "signerKeyId", 80).toLowerCase(Locale.ROOT);
        String signatureBase64 = base64(request.signatureBase64());
        String canonical = canonical(plan.getId(), adapter.getId(), action, target, nonce, issuedAt, expiresAt, planSha);
        if (!trust.verify(signerKeyId, canonical, signatureBase64)) throw new IllegalArgumentException("Stage 16 envelope signature verification failed");

        return envelopes.save(new Stage16ExecutionEnvelopeEntity(UUID.randomUUID(), plan.getId(), adapter.getId(), action,
                target, nonce, planSha, signerKeyId, shaBytes(Base64.getDecoder().decode(signatureBase64)), issuedAt, expiresAt));
    }

    @Transactional
    public Stage16ExecutionEnvelopeEntity cancel(UUID envelopeId, CancelRequest request) {
        Stage16ExecutionEnvelopeEntity envelope = get(envelopeId);
        String reason = text(request == null ? null : request.reason(), "reason", 600);
        envelope.cancel(reason);
        return envelopes.save(envelope);
    }

    @Transactional
    public ExecutionResult execute(UUID envelopeId, ExecuteRequest request) {
        Stage16ExecutionEnvelopeEntity envelope = get(envelopeId);
        if (!"ADMITTED".equals(envelope.getStatus())) throw new IllegalStateException("Stage 16 envelope is not executable");
        if (!envelope.getExpiresAt().isAfter(Instant.now())) throw new IllegalStateException("Stage 16 envelope expired before execution");
        Stage15RecoveryPlanEntity plan = recovery.get(envelope.getPlanId());
        if (plan.getApprovalId() == null || !"AUTHORIZED_PENDING_TARGET_EVIDENCE".equals(plan.getStatus()))
            throw new IllegalStateException("Stage 15 recovery authorization is no longer valid for this envelope");
        Stage16AdapterEntity adapter = adapters.get(envelope.getAdapterId());
        Stage16RecoverySandboxService.SandboxResult result = sandbox.run(plan, adapter,
                request == null ? "NORMAL" : request.scenario());
        envelope.complete(result.outcome(), result.receiptSha256());
        envelopes.save(envelope);
        return new ExecutionResult(envelope.getId(), result.outcome(), result.scenario(), result.actionAttempted(),
                result.actionSucceeded(), result.verificationAttempted(), result.verificationSucceeded(),
                result.rollbackAttempted(), result.rollbackSucceeded(), result.receiptSha256(), true, false, false);
    }

    public Stage16ExecutionEnvelopeEntity get(UUID id) {
        return envelopes.findById(id).orElseThrow(() -> new NoSuchElementException("Unknown Stage 16 execution envelope"));
    }
    public List<Stage16ExecutionEnvelopeEntity> recent() { return envelopes.findTop100ByOrderByUpdatedAtDesc(); }
    public List<Stage16ExecutionEnvelopeEntity> forPlan(UUID planId) { recovery.get(planId); return envelopes.findTop100ByPlanIdOrderByUpdatedAtDesc(planId); }

    public static String canonical(UUID planId, String adapterId, String action, String target, String nonce,
                                   Instant issuedAt, Instant expiresAt, String planSha256) {
        return planId + "|" + adapterId.toLowerCase(Locale.ROOT) + "|" + action.toUpperCase(Locale.ROOT) + "|"
                + target.toLowerCase(Locale.ROOT) + "|" + nonce + "|" + issuedAt.toEpochMilli() + "|"
                + expiresAt.toEpochMilli() + "|" + planSha256.toLowerCase(Locale.ROOT);
    }

    private String token(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String v = value.trim();
        if (v.length() > max || !v.matches("[A-Za-z0-9._:/-]{1," + max + "}")) throw new IllegalArgumentException(label + " is invalid");
        return v;
    }
    private String nonce(String value) {
        if (value == null || !value.trim().matches("[A-Za-z0-9._:-]{16,120}")) throw new IllegalArgumentException("nonce is invalid");
        return value.trim();
    }
    private String text(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String v = value.trim();
        if (v.length() > max) throw new IllegalArgumentException(label + " is too long");
        return v;
    }
    private String sha(String value, String label) {
        if (value == null || !value.trim().toLowerCase(Locale.ROOT).matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException(label + " must be a SHA-256 value");
        return value.trim().toLowerCase(Locale.ROOT);
    }
    private String base64(String value) {
        if (value == null || value.isBlank() || value.length() > 512) throw new IllegalArgumentException("signatureBase64 is invalid");
        try { return Base64.getEncoder().encodeToString(Base64.getDecoder().decode(value.trim())); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("signatureBase64 must be valid Base64"); }
    }
    private String shaBytes(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public record SignedEnvelopeRequest(UUID planId, String adapterId, String action, String target, String nonce,
                                        Instant issuedAt, Instant expiresAt, String planSha256,
                                        String signerKeyId, String signatureBase64) {}
    public record CancelRequest(String reason) {}
    public record ExecuteRequest(String scenario) {}
    public record ExecutionResult(UUID envelopeId, String outcome, String scenario, boolean actionAttempted,
                                  boolean actionSucceeded, boolean verificationAttempted, boolean verificationSucceeded,
                                  boolean rollbackAttempted, boolean rollbackSucceeded, String receiptSha256,
                                  boolean simulationOnly, boolean targetMutated, boolean externalActionAttempted) {}
}
