package io.aetheris.orchestrator.stage17;

import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/orchestrator/stage17")
public class Stage17OperationsController {
    private final Stage17AdapterManifestService manifests;
    private final Stage17CapabilityPolicyService policies;
    private final Stage17TransportLabService transport;
    private final Stage17OfflineQueueService queue;
    private final Stage17MeasuredReceiptService receipts;
    private final Stage17CertificationService certifications;

    public Stage17OperationsController(Stage17AdapterManifestService manifests,
                                       Stage17CapabilityPolicyService policies,
                                       Stage17TransportLabService transport,
                                       Stage17OfflineQueueService queue,
                                       Stage17MeasuredReceiptService receipts,
                                       Stage17CertificationService certifications) {
        this.manifests = manifests; this.policies = policies; this.transport = transport;
        this.queue = queue; this.receipts = receipts; this.certifications = certifications;
    }

    @PostMapping("/manifests")
    public Stage17AdapterManifestEntity registerManifest(@RequestBody Stage17AdapterManifestService.SignedManifestRequest request) {
        return manifests.register(request);
    }
    @GetMapping("/manifests")
    public List<Stage17AdapterManifestEntity> manifests() { return manifests.list(); }
    @GetMapping("/manifests/{adapterId}/latest")
    public Stage17AdapterManifestEntity latestManifest(@PathVariable String adapterId) { return manifests.latest(adapterId); }

    @PostMapping("/protocol/negotiate")
    public Stage17AdapterManifestService.ProtocolNegotiation negotiate(@RequestBody ProtocolRequest request) {
        return manifests.negotiate(request.adapterId(), request.clientMin(), request.clientMax());
    }
    @PostMapping("/policy/compile")
    public Stage17CapabilityPolicyService.CompiledPolicy compile(@RequestBody PolicyRequest request) {
        return policies.compile(request.adapterId(), request.capabilities());
    }
    @PostMapping("/transport/rehearse")
    public Stage17TransportLabService.TransportResult rehearse(@RequestBody Stage17TransportLabService.TransportRequest request) {
        return transport.rehearse(request);
    }

    @PostMapping("/queue/{envelopeId}")
    public Stage17QueuedCommandEntity enqueue(@PathVariable UUID envelopeId) { return queue.enqueue(envelopeId); }
    @PostMapping("/queue/{queueId}/cancel")
    public Stage17QueuedCommandEntity cancel(@PathVariable UUID queueId, @RequestBody ReasonRequest request) {
        return queue.cancel(queueId, request.reason());
    }
    @PostMapping("/queue/reconnect")
    public Stage17OfflineQueueService.ReconnectResult reconnect(@RequestBody ReconnectRequest request) {
        return queue.reconnect(request.adapterId(), request.transportAvailable());
    }
    @PostMapping("/queue/revoke")
    public Map<String, Object> revoke(@RequestBody RevokeRequest request) {
        int changed = queue.revokePending(request.adapterId(), request.reason());
        return Map.of("status", "REVOCATION_REHEARSED", "revoked", changed,
                "simulationOnly", true, "externalActionAttempted", false);
    }
    @GetMapping("/queue")
    public List<Stage17QueuedCommandEntity> queue() { return queue.list(); }
    @GetMapping("/queue/{adapterId}/conformance")
    public Stage17OfflineQueueService.QueueConformance queueConformance(@PathVariable String adapterId) {
        return queue.conformance(adapterId);
    }

    @PostMapping("/receipts")
    public Stage17MeasuredReceiptEntity recordReceipt(@RequestBody Stage17MeasuredReceiptService.ReceiptRequest request) {
        return receipts.record(request);
    }
    @GetMapping("/receipts")
    public List<Stage17MeasuredReceiptEntity> receipts() { return receipts.list(); }

    @PostMapping("/certifications")
    public Stage17AdapterCertificationEntity certify(@RequestBody Stage17CertificationService.CertificationRequest request) {
        return certifications.certify(request);
    }
    @GetMapping("/certifications")
    public List<Stage17AdapterCertificationEntity> certifications() { return certifications.list(); }
    @GetMapping("/compatibility")
    public Stage17CertificationService.CompatibilityMatrix compatibility() { return certifications.compatibilityMatrix(); }

    @GetMapping("/overview")
    public Overview overview() {
        var qs = queue.list();
        return new Overview(manifests.list().size(), qs.size(), receipts.list().size(), certifications.list().size(),
                qs.stream().filter(q -> "DELIVERED_SIMULATION".equals(q.getState())).count(),
                qs.stream().filter(q -> Set.of("CANCELLED", "REVOKED").contains(q.getState())).count(),
                false, false);
    }

    public record ProtocolRequest(String adapterId, int clientMin, int clientMax) {}
    public record PolicyRequest(String adapterId, Set<String> capabilities) {}
    public record ReasonRequest(String reason) {}
    public record ReconnectRequest(String adapterId, boolean transportAvailable) {}
    public record RevokeRequest(String adapterId, String reason) {}
    public record Overview(int manifestCount, int queuedCommandCount, int measuredReceiptCount,
                           int certificationCount, long simulatedDeliveries, long cancellationsOrRevocations,
                           boolean productionActivationAllowed, boolean externalActionAttempted) {}
}
