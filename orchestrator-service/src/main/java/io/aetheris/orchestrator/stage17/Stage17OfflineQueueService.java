package io.aetheris.orchestrator.stage17;

import io.aetheris.orchestrator.stage16.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class Stage17OfflineQueueService {
    private final Stage17QueuedCommandRepository repository;
    private final Stage16SecureRecoveryService secureRecovery;
    private final Stage16AdapterRegistryService adapters;
    private final Stage17AdapterManifestService manifests;
    private final Stage17CapabilityPolicyService policies;

    public Stage17OfflineQueueService(Stage17QueuedCommandRepository repository,
                                      Stage16SecureRecoveryService secureRecovery,
                                      Stage16AdapterRegistryService adapters,
                                      Stage17AdapterManifestService manifests,
                                      Stage17CapabilityPolicyService policies) {
        this.repository = repository; this.secureRecovery = secureRecovery; this.adapters = adapters;
        this.manifests = manifests; this.policies = policies;
    }

    @Transactional
    public Stage17QueuedCommandEntity enqueue(UUID envelopeId) {
        if (envelopeId == null) throw new IllegalArgumentException("envelopeId is required");
        if (repository.existsByEnvelopeId(envelopeId)) throw new IllegalStateException("Stage 17 queue already contains this envelope");
        Stage16ExecutionEnvelopeEntity envelope = secureRecovery.get(envelopeId);
        if (!"ADMITTED".equals(envelope.getStatus())) throw new IllegalStateException("Only ADMITTED Stage 16 envelopes may enter the offline queue");
        Stage16AdapterEntity adapter = adapters.get(envelope.getAdapterId());
        Stage17AdapterManifestEntity manifest = manifests.latest(adapter.getId());
        if (!manifest.getTransportModes().contains("OFFLINE_QUEUE"))
            throw new IllegalStateException("Signed manifest does not allow OFFLINE_QUEUE");
        policies.compile(adapter.getId(), Set.of(envelope.getAction()));
        String commandSha = Stage17AdapterManifestService.shaText(adapter.getId() + "|" + envelope.getId() + "|"
                + envelope.getAction() + "|" + envelope.getTarget() + "|" + envelope.getNonce());
        return repository.save(new Stage17QueuedCommandEntity(UUID.randomUUID(), adapter.getId(), envelope.getId(),
                envelope.getAction(), envelope.getTarget(), commandSha, envelope.getExpiresAt()));
    }

    @Transactional
    public Stage17QueuedCommandEntity cancel(UUID queueId, String reason) {
        Stage17QueuedCommandEntity item = get(queueId);
        item.cancel(text(reason, "reason", 600));
        return repository.save(item);
    }

    @Transactional
    public ReconnectResult reconnect(String adapterId, boolean transportAvailable) {
        String id = adapters.get(adapterId).getId();
        List<Stage17QueuedCommandEntity> items = repository.findTop100ByAdapterIdOrderByQueuedAtAsc(id);
        if (!transportAvailable) return new ReconnectResult("TRANSPORT_UNAVAILABLE", id, 0, 0, 0, 0, true, false);
        int delivered = 0, cancelled = 0, revoked = 0, expired = 0;
        Instant now = Instant.now();
        for (Stage17QueuedCommandEntity item : items) {
            if (!"QUEUED_OFFLINE".equals(item.getState())) continue;
            Stage16ExecutionEnvelopeEntity envelope = secureRecovery.get(item.getEnvelopeId());
            if ("CANCELLED".equals(envelope.getStatus())) {
                item.cancel("underlying Stage 16 envelope was cancelled"); cancelled++;
            } else if (!item.getExpiresAt().isAfter(now) || !envelope.getExpiresAt().isAfter(now)) {
                item.expire(); expired++;
            } else if (!"ADMITTED".equals(envelope.getStatus())) {
                item.revoke("underlying Stage 16 envelope is no longer admitted"); revoked++;
            } else {
                item.deliverSimulation(); delivered++;
            }
            repository.save(item);
        }
        return new ReconnectResult("RECONNECT_REHEARSED", id, delivered, cancelled, revoked, expired, true, false);
    }

    @Transactional
    public int revokePending(String adapterId, String reason) {
        String id = adapters.get(adapterId).getId();
        String why = text(reason, "reason", 600);
        int changed = 0;
        for (Stage17QueuedCommandEntity item : repository.findTop100ByAdapterIdOrderByQueuedAtAsc(id)) {
            if ("QUEUED_OFFLINE".equals(item.getState())) {
                item.revoke(why); repository.save(item); changed++;
            }
        }
        return changed;
    }

    public QueueConformance conformance(String adapterId) {
        String id = adapters.get(adapterId).getId();
        List<Stage17QueuedCommandEntity> items = repository.findTop100ByAdapterIdOrderByQueuedAtAsc(id);
        boolean delivered = items.stream().anyMatch(i -> "DELIVERED_SIMULATION".equals(i.getState()));
        boolean cancellationOrRevocation = items.stream().anyMatch(i -> Set.of("CANCELLED", "REVOKED").contains(i.getState()));
        boolean external = items.stream().anyMatch(Stage17QueuedCommandEntity::isExternalActionAttempted);
        boolean conformant = delivered && cancellationOrRevocation && !external;
        List<String> blockers = new ArrayList<>();
        if (!delivered) blockers.add("no simulated reconnect delivery evidence");
        if (!cancellationOrRevocation) blockers.add("no cancellation/revocation evidence");
        if (external) blockers.add("queue evidence contains an external action attempt");
        return new QueueConformance(conformant ? "CONFORMANT" : "INSUFFICIENT_EVIDENCE", id,
                delivered, cancellationOrRevocation, !external, true, false, List.copyOf(blockers));
    }

    public Stage17QueuedCommandEntity get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NoSuchElementException("Unknown Stage 17 queued command"));
    }
    public List<Stage17QueuedCommandEntity> list() { return repository.findTop100ByOrderByQueuedAtDesc(); }

    private String text(String value, String label, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        String v = value.trim(); if (v.length() > max) throw new IllegalArgumentException(label + " is too long"); return v;
    }

    public record ReconnectResult(String status, String adapterId, int delivered, int cancelled, int revoked,
                                  int expired, boolean simulationOnly, boolean externalActionAttempted) {}
    public record QueueConformance(String status, String adapterId, boolean deliveryEvidence,
                                   boolean cancellationOrRevocationEvidence, boolean noExternalActions,
                                   boolean simulationOnly, boolean externalActionAttempted, List<String> blockers) {}
}
