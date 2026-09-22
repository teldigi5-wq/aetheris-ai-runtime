package io.aetheris.orchestrator.stage17;

import io.aetheris.orchestrator.stage15.Stage15ServiceCatalogService;
import io.aetheris.orchestrator.stage16.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class Stage17AdapterManifestService {
    public static final int SERVER_PROTOCOL_MIN = 1;
    public static final int SERVER_PROTOCOL_MAX = 2;
    public static final String RECEIPT_SCHEMA_V1 = "RECEIPT_V1";
    private static final Set<String> TRANSPORTS = Set.of("SYNTHETIC_MTLS", "OFFLINE_QUEUE", "LOOPBACK_SIMULATION");

    private final Stage17AdapterManifestRepository repository;
    private final Stage16AdapterRegistryService adapters;
    private final Stage16CommandTrustService trust;

    public Stage17AdapterManifestService(Stage17AdapterManifestRepository repository,
                                         Stage16AdapterRegistryService adapters,
                                         Stage16CommandTrustService trust) {
        this.repository = repository; this.adapters = adapters; this.trust = trust;
    }

    @Transactional
    public Stage17AdapterManifestEntity register(SignedManifestRequest request) {
        if (request == null) throw new IllegalArgumentException("Signed adapter manifest is required");
        Stage16AdapterEntity adapter = adapters.get(request.adapterId());
        if (!adapter.isEnabled() || !adapter.isSimulationOnly())
            throw new IllegalStateException("Stage 17 repository certification accepts enabled simulation-only Stage 16 adapters");
        String adapterId = adapter.getId();
        String sdkVersion = semanticVersion(request.sdkVersion());
        if (request.minProtocolVersion() < 1 || request.maxProtocolVersion() < request.minProtocolVersion() || request.maxProtocolVersion() > 99)
            throw new IllegalArgumentException("Manifest protocol range is invalid");
        Set<String> capabilities = normalizeCapabilities(request.capabilities());
        if (capabilities.isEmpty()) throw new IllegalArgumentException("Manifest must declare at least one bounded capability");
        if (!adapter.getCapabilities().containsAll(capabilities))
            throw new IllegalArgumentException("Manifest capability exceeds Stage 16 adapter capability");
        if (!Stage15ServiceCatalogService.BOUNDED_ACTIONS.containsAll(capabilities) ||
                capabilities.stream().anyMatch(Stage15ServiceCatalogService.FORBIDDEN_ACTIONS::contains))
            throw new IllegalArgumentException("Manifest declares forbidden or unsupported capability");
        Set<String> transports = normalizeTransports(request.transportModes());
        if (transports.isEmpty()) throw new IllegalArgumentException("Manifest must declare at least one supported transport mode");
        String receiptSchema = token(request.receiptSchemaVersion(), "receiptSchemaVersion", 32).toUpperCase(Locale.ROOT);
        if (!RECEIPT_SCHEMA_V1.equals(receiptSchema)) throw new IllegalArgumentException("Unsupported Stage 17 receipt schema");
        String manifestSha = sha(request.manifestSha256(), "manifestSha256");
        String content = contentCanonical(adapterId, sdkVersion, request.minProtocolVersion(), request.maxProtocolVersion(),
                capabilities, transports, receiptSchema);
        if (!manifestSha.equals(shaText(content))) throw new IllegalArgumentException("Manifest SHA-256 does not match canonical manifest content");
        if (repository.existsByAdapterIdAndManifestSha256(adapterId, manifestSha))
            throw new IllegalStateException("Stage 17 manifest already registered");
        String signerKeyId = token(request.signerKeyId(), "signerKeyId", 80).toLowerCase(Locale.ROOT);
        String signatureBase64 = normalizeBase64(request.signatureBase64());
        if (!trust.verify(signerKeyId, signedCanonical(content, manifestSha), signatureBase64))
            throw new IllegalArgumentException("Stage 17 manifest signature verification failed");
        return repository.save(new Stage17AdapterManifestEntity(UUID.randomUUID(), adapterId, sdkVersion,
                request.minProtocolVersion(), request.maxProtocolVersion(), capabilities, transports, receiptSchema,
                manifestSha, signerKeyId, shaBytes(Base64.getDecoder().decode(signatureBase64))));
    }

    public Stage17AdapterManifestEntity latest(String adapterId) {
        String id = adapters.get(adapterId).getId();
        return repository.findTop20ByAdapterIdOrderByCreatedAtDesc(id).stream().findFirst()
                .orElseThrow(() -> new NoSuchElementException("No verified Stage 17 manifest exists for adapter"));
    }

    public List<Stage17AdapterManifestEntity> list() { return repository.findTop100ByOrderByCreatedAtDesc(); }

    public ProtocolNegotiation negotiate(String adapterId, int clientMin, int clientMax) {
        if (clientMin < 1 || clientMax < clientMin) throw new IllegalArgumentException("Client protocol range is invalid");
        Stage17AdapterManifestEntity manifest = latest(adapterId);
        int min = Math.max(Math.max(clientMin, manifest.getMinProtocolVersion()), SERVER_PROTOCOL_MIN);
        int max = Math.min(Math.min(clientMax, manifest.getMaxProtocolVersion()), SERVER_PROTOCOL_MAX);
        if (max < min) return new ProtocolNegotiation("INCOMPATIBLE", adapterId, null,
                manifest.getMinProtocolVersion(), manifest.getMaxProtocolVersion(), SERVER_PROTOCOL_MIN, SERVER_PROTOCOL_MAX,
                true, false, List.of("no protocol-version overlap"));
        return new ProtocolNegotiation("NEGOTIATED", adapterId, max, manifest.getMinProtocolVersion(),
                manifest.getMaxProtocolVersion(), SERVER_PROTOCOL_MIN, SERVER_PROTOCOL_MAX, true, false, List.of());
    }

    public static String contentCanonical(String adapterId, String sdkVersion, int minProtocolVersion, int maxProtocolVersion,
                                          Set<String> capabilities, Set<String> transportModes, String receiptSchemaVersion) {
        return adapterId.toLowerCase(Locale.ROOT) + "|" + sdkVersion + "|" + minProtocolVersion + "|" + maxProtocolVersion + "|"
                + String.join(",", new TreeSet<>(capabilities)) + "|" + String.join(",", new TreeSet<>(transportModes)) + "|"
                + receiptSchemaVersion.toUpperCase(Locale.ROOT);
    }
    public static String signedCanonical(String contentCanonical, String manifestSha256) {
        return contentCanonical + "|" + manifestSha256.toLowerCase(Locale.ROOT);
    }
    public static String shaText(String value) { return shaBytes(value.getBytes(StandardCharsets.UTF_8)); }
    public static String shaBytes(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private Set<String> normalizeCapabilities(Set<String> values) {
        if (values == null) return Set.of();
        TreeSet<String> out = new TreeSet<>();
        for (String value : values) out.add(token(value, "capability", 48).toUpperCase(Locale.ROOT));
        return Set.copyOf(out);
    }
    private Set<String> normalizeTransports(Set<String> values) {
        if (values == null) return Set.of();
        TreeSet<String> out = new TreeSet<>();
        for (String value : values) {
            String transport = token(value, "transportMode", 40).toUpperCase(Locale.ROOT);
            if (!TRANSPORTS.contains(transport)) throw new IllegalArgumentException("Unsupported Stage 17 transport mode: " + transport);
            out.add(transport);
        }
        return Set.copyOf(out);
    }
    private String semanticVersion(String value) {
        if (value == null || !value.trim().matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?"))
            throw new IllegalArgumentException("sdkVersion must be a semantic version");
        return value.trim();
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
    private String normalizeBase64(String value) {
        if (value == null || value.isBlank() || value.length() > 512) throw new IllegalArgumentException("signatureBase64 is invalid");
        try { return Base64.getEncoder().encodeToString(Base64.getDecoder().decode(value.trim())); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("signatureBase64 must be valid Base64"); }
    }

    public record SignedManifestRequest(String adapterId, String sdkVersion, int minProtocolVersion,
                                        int maxProtocolVersion, Set<String> capabilities, Set<String> transportModes,
                                        String receiptSchemaVersion, String manifestSha256,
                                        String signerKeyId, String signatureBase64) {}
    public record ProtocolNegotiation(String status, String adapterId, Integer selectedProtocolVersion,
                                      int manifestMin, int manifestMax, int serverMin, int serverMax,
                                      boolean simulationOnly, boolean externalActionAttempted, List<String> blockers) {}
}
