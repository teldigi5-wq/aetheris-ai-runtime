package io.aetheris.orchestrator.stage17;

import io.aetheris.orchestrator.stage16.Stage16AdapterRegistryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class Stage17CertificationService {
    private final Stage17AdapterCertificationRepository repository;
    private final Stage17AdapterManifestService manifests;
    private final Stage17CapabilityPolicyService policies;
    private final Stage17TransportLabService transport;
    private final Stage17OfflineQueueService queue;
    private final Stage17MeasuredReceiptService receipts;
    private final Stage16AdapterRegistryService adapters;

    public Stage17CertificationService(Stage17AdapterCertificationRepository repository,
                                       Stage17AdapterManifestService manifests,
                                       Stage17CapabilityPolicyService policies,
                                       Stage17TransportLabService transport,
                                       Stage17OfflineQueueService queue,
                                       Stage17MeasuredReceiptService receipts,
                                       Stage16AdapterRegistryService adapters) {
        this.repository = repository; this.manifests = manifests; this.policies = policies;
        this.transport = transport; this.queue = queue; this.receipts = receipts; this.adapters = adapters;
    }

    @Transactional
    public Stage17AdapterCertificationEntity certify(CertificationRequest request) {
        if (request == null || request.receiptId() == null || request.transportRequest() == null)
            throw new IllegalArgumentException("Stage 17 certification evidence is required");
        String adapterId = adapters.get(request.adapterId()).getId();
        Stage17AdapterManifestEntity manifest = manifests.latest(adapterId);
        Stage17AdapterManifestService.ProtocolNegotiation protocol = manifests.negotiate(adapterId,
                request.clientProtocolMin(), request.clientProtocolMax());
        if (!"NEGOTIATED".equals(protocol.status()) || protocol.selectedProtocolVersion() == null)
            throw new IllegalStateException("Adapter cannot be certified without protocol negotiation");
        Stage17CapabilityPolicyService.CompiledPolicy policy = policies.compile(adapterId, request.requestedCapabilities());
        Stage17TransportLabService.TransportRequest tr = request.transportRequest();
        if (!adapterId.equalsIgnoreCase(tr.adapterId())) throw new IllegalArgumentException("Transport evidence adapter does not match certification adapter");
        if (tr.protocolVersion() != protocol.selectedProtocolVersion()) throw new IllegalArgumentException("Transport protocol must match negotiated protocol version");
        Stage17TransportLabService.TransportResult transportResult = transport.rehearse(tr);
        Stage17OfflineQueueService.QueueConformance queueResult = queue.conformance(adapterId);
        if (!"CONFORMANT".equals(queueResult.status()))
            throw new IllegalStateException("Offline/reconnect cancellation conformance evidence is incomplete: " + String.join("; ", queueResult.blockers()));
        Stage17MeasuredReceiptEntity receipt = receipts.get(request.receiptId());
        if (!adapterId.equals(receipt.getAdapterId())) throw new IllegalArgumentException("Measured receipt belongs to a different adapter");
        if (!receipt.isSourceMeasured() || !receipt.isSimulationOnly() || receipt.isTargetMutated() || receipt.isExternalActionAttempted())
            throw new IllegalStateException("Measured receipt violates Stage 17 simulation certification boundary");

        int score = 100;
        return repository.save(new Stage17AdapterCertificationEntity(UUID.randomUUID(), adapterId, manifest.getId(),
                protocol.selectedProtocolVersion(), policy.policySha256(), transportResult.attestationSha256(),
                receipt.getId(), score, Instant.now().plus(Duration.ofDays(30))));
    }

    public List<Stage17AdapterCertificationEntity> list() { return repository.findTop100ByOrderByCertifiedAtDesc(); }

    public CompatibilityMatrix compatibilityMatrix() {
        List<CompatibilityRow> rows = new ArrayList<>();
        adapters.list().forEach(adapter -> {
            Optional<Stage17AdapterManifestEntity> manifest;
            try { manifest = Optional.of(manifests.latest(adapter.getId())); }
            catch (RuntimeException e) { manifest = Optional.empty(); }
            Optional<Stage17AdapterCertificationEntity> certification = repository
                    .findTop20ByAdapterIdOrderByCertifiedAtDesc(adapter.getId()).stream().findFirst();
            rows.add(new CompatibilityRow(adapter.getId(), adapter.getAdapterType(), adapter.isSimulationOnly(),
                    manifest.map(Stage17AdapterManifestEntity::getSdkVersion).orElse(null),
                    manifest.map(Stage17AdapterManifestEntity::getMinProtocolVersion).orElse(null),
                    manifest.map(Stage17AdapterManifestEntity::getMaxProtocolVersion).orElse(null),
                    certification.map(Stage17AdapterCertificationEntity::getStatus).orElse("NOT_CERTIFIED"),
                    certification.map(Stage17AdapterCertificationEntity::getScore).orElse(0),
                    certification.map(Stage17AdapterCertificationEntity::getExpiresAt).orElse(null), false));
        });
        rows.sort(Comparator.comparing(CompatibilityRow::adapterId));
        return new CompatibilityMatrix("SIMULATION_CERTIFICATION_MATRIX", List.copyOf(rows), false, false);
    }

    public record CertificationRequest(String adapterId, Set<String> requestedCapabilities,
                                       int clientProtocolMin, int clientProtocolMax,
                                       Stage17TransportLabService.TransportRequest transportRequest,
                                       UUID receiptId) {}
    public record CompatibilityRow(String adapterId, String adapterType, boolean simulationOnly,
                                   String sdkVersion, Integer minProtocolVersion, Integer maxProtocolVersion,
                                   String certificationStatus, int certificationScore, Instant certificationExpiresAt,
                                   boolean productionActivationAllowed) {}
    public record CompatibilityMatrix(String status, List<CompatibilityRow> adapters,
                                      boolean productionActivationAllowed, boolean externalActionAttempted) {}
}
