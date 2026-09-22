package io.aetheris.orchestrator.stage17;

import io.aetheris.orchestrator.stage16.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class Stage17MeasuredReceiptService {
    private final Stage17MeasuredReceiptRepository repository;
    private final Stage16SecureRecoveryService secureRecovery;
    private final Stage17AdapterManifestService manifests;

    public Stage17MeasuredReceiptService(Stage17MeasuredReceiptRepository repository,
                                         Stage16SecureRecoveryService secureRecovery,
                                         Stage17AdapterManifestService manifests) {
        this.repository = repository; this.secureRecovery = secureRecovery; this.manifests = manifests;
    }

    @Transactional
    public Stage17MeasuredReceiptEntity record(ReceiptRequest request) {
        if (request == null || request.envelopeId() == null) throw new IllegalArgumentException("Measured receipt is required");
        if (repository.existsByEnvelopeId(request.envelopeId())) throw new IllegalStateException("Stage 17 receipt already exists for envelope");
        Stage16ExecutionEnvelopeEntity envelope = secureRecovery.get(request.envelopeId());
        if (!"SIMULATION_COMPLETED".equals(envelope.getStatus()))
            throw new IllegalStateException("Stage 17 receipt requires a completed Stage 16 simulation envelope");
        if (envelope.getReceiptSha256() == null) throw new IllegalStateException("Stage 16 envelope has no sandbox receipt");
        String adapterId = envelope.getAdapterId();
        Stage17AdapterManifestEntity manifest = manifests.latest(adapterId);
        String schema = token(request.schemaVersion(), "schemaVersion", 32).toUpperCase(Locale.ROOT);
        if (!schema.equals(manifest.getReceiptSchemaVersion())) throw new IllegalArgumentException("Receipt schema does not match signed manifest");
        String outcome = token(request.outcome(), "outcome", 80).toUpperCase(Locale.ROOT);
        if (!outcome.equals(envelope.getOutcome())) throw new IllegalArgumentException("Receipt outcome does not match Stage 16 envelope outcome");
        String receiptSha = sha(request.receiptSha256(), "receiptSha256");
        if (!receiptSha.equals(envelope.getReceiptSha256())) throw new IllegalArgumentException("Receipt SHA-256 does not match Stage 16 sandbox receipt");
        if (!request.sourceMeasured()) throw new IllegalArgumentException("Stage 17 receipt must be source-measured");
        if (!request.simulationOnly() || request.targetMutated() || request.externalActionAttempted())
            throw new IllegalArgumentException("Repository certification only accepts simulation-only receipts with no target mutation/external action");
        String attestation = sha(request.attestationSha256(), "attestationSha256");
        Instant observedAt = request.observedAt() == null ? Instant.now() : request.observedAt();
        if (observedAt.isAfter(Instant.now().plusSeconds(30))) throw new IllegalArgumentException("Receipt cannot be future-dated");
        return repository.save(new Stage17MeasuredReceiptEntity(UUID.randomUUID(), adapterId, envelope.getId(), schema,
                outcome, receiptSha, attestation, true, true, false, false, observedAt));
    }

    public Stage17MeasuredReceiptEntity get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NoSuchElementException("Unknown Stage 17 measured receipt"));
    }
    public List<Stage17MeasuredReceiptEntity> list() { return repository.findTop100ByOrderByObservedAtDesc(); }
    public List<Stage17MeasuredReceiptEntity> forAdapter(String adapterId) {
        manifests.latest(adapterId); return repository.findTop50ByAdapterIdOrderByObservedAtDesc(adapterId.toLowerCase(Locale.ROOT));
    }

    private String token(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String v = value.trim();
        if (v.length() > max || !v.matches("[A-Za-z0-9._:/-]{1," + max + "}")) throw new IllegalArgumentException(label + " is invalid");
        return v;
    }
    private String sha(String value, String label) {
        if (value == null || !value.trim().toLowerCase(Locale.ROOT).matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException(label + " must be a SHA-256 value");
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record ReceiptRequest(UUID envelopeId, String schemaVersion, String outcome, String receiptSha256,
                                 boolean sourceMeasured, boolean simulationOnly, boolean targetMutated,
                                 boolean externalActionAttempted, String attestationSha256, Instant observedAt) {}
}
