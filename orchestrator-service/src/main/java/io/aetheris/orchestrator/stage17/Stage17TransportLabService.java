package io.aetheris.orchestrator.stage17;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class Stage17TransportLabService {
    private final Stage17AdapterManifestService manifests;

    public Stage17TransportLabService(Stage17AdapterManifestService manifests) { this.manifests = manifests; }

    public TransportResult rehearse(TransportRequest request) {
        if (request == null) throw new IllegalArgumentException("Synthetic transport request is required");
        Stage17AdapterManifestEntity manifest = manifests.latest(request.adapterId());
        if (!manifest.getTransportModes().contains("SYNTHETIC_MTLS"))
            throw new IllegalStateException("Signed manifest does not allow SYNTHETIC_MTLS");
        if (request.protocolVersion() < manifest.getMinProtocolVersion() || request.protocolVersion() > manifest.getMaxProtocolVersion() ||
                request.protocolVersion() < Stage17AdapterManifestService.SERVER_PROTOCOL_MIN ||
                request.protocolVersion() > Stage17AdapterManifestService.SERVER_PROTOCOL_MAX)
            throw new IllegalArgumentException("Transport protocol version is not negotiated by manifest and server");
        if (!"TLS1.3".equalsIgnoreCase(request.tlsVersion()))
            throw new IllegalArgumentException("Stage 17 synthetic transport requires TLS1.3");
        if (!request.mutualTls() || !request.clientCertificatePinned() || !request.serverCertificatePinned() || !request.revocationChecked())
            throw new IllegalArgumentException("Synthetic mTLS requires mutual authentication, both certificate pins and revocation checks");
        String client = sha(request.clientCertificateSha256(), "clientCertificateSha256");
        String server = sha(request.serverCertificateSha256(), "serverCertificateSha256");
        if (client.equals(server)) throw new IllegalArgumentException("Client and server certificate fingerprints must be distinct");
        String canonical = manifest.getAdapterId() + "|" + request.protocolVersion() + "|TLS1.3|" + client + "|" + server
                + "|mtls=true|pins=true|revocation=true";
        return new TransportResult("SYNTHETIC_MTLS_PASS", manifest.getAdapterId(), request.protocolVersion(),
                Stage17AdapterManifestService.shaText(canonical), true, false, false, List.of());
    }

    private String sha(String value, String label) {
        if (value == null || !value.trim().toLowerCase(Locale.ROOT).matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException(label + " must be a SHA-256 value");
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record TransportRequest(String adapterId, int protocolVersion, String tlsVersion, boolean mutualTls,
                                   boolean clientCertificatePinned, boolean serverCertificatePinned,
                                   boolean revocationChecked, String clientCertificateSha256,
                                   String serverCertificateSha256) {}
    public record TransportResult(String status, String adapterId, int protocolVersion, String attestationSha256,
                                  boolean simulationOnly, boolean networkConnectionAttempted,
                                  boolean productionTransportActivated, List<String> blockers) {}
}
