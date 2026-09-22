package io.aetheris.orchestrator.stage17;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "stage17_adapter_manifests",
        uniqueConstraints = @UniqueConstraint(name = "uk_stage17_adapter_manifest_sha", columnNames = {"adapterId", "manifestSha256"}))
public class Stage17AdapterManifestEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 80) private String adapterId;
    @Column(nullable = false, length = 64) private String sdkVersion;
    @Column(nullable = false) private int minProtocolVersion;
    @Column(nullable = false) private int maxProtocolVersion;
    @Column(nullable = false, length = 800) private String capabilitiesCsv;
    @Column(nullable = false, length = 400) private String transportModesCsv;
    @Column(nullable = false, length = 32) private String receiptSchemaVersion;
    @Column(nullable = false, length = 64) private String manifestSha256;
    @Column(nullable = false, length = 80) private String signerKeyId;
    @Column(nullable = false, length = 64) private String signatureSha256;
    @Column(nullable = false, length = 40) private String status;
    @Column(nullable = false) private boolean simulationOnly;
    @Column(nullable = false) private Instant createdAt;
    @Version private long version;

    protected Stage17AdapterManifestEntity() {}

    public Stage17AdapterManifestEntity(UUID id, String adapterId, String sdkVersion,
                                        int minProtocolVersion, int maxProtocolVersion,
                                        Set<String> capabilities, Set<String> transportModes,
                                        String receiptSchemaVersion, String manifestSha256,
                                        String signerKeyId, String signatureSha256) {
        this.id = id; this.adapterId = adapterId; this.sdkVersion = sdkVersion;
        this.minProtocolVersion = minProtocolVersion; this.maxProtocolVersion = maxProtocolVersion;
        this.capabilitiesCsv = csv(capabilities); this.transportModesCsv = csv(transportModes);
        this.receiptSchemaVersion = receiptSchemaVersion; this.manifestSha256 = manifestSha256;
        this.signerKeyId = signerKeyId; this.signatureSha256 = signatureSha256;
        this.status = "MANIFEST_VERIFIED"; this.simulationOnly = true; this.createdAt = Instant.now();
    }

    private static String csv(Set<String> values) {
        return values == null ? "" : values.stream().sorted().collect(Collectors.joining(","));
    }
    private static Set<String> set(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(",")).filter(v -> !v.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    public UUID getId() { return id; }
    public String getAdapterId() { return adapterId; }
    public String getSdkVersion() { return sdkVersion; }
    public int getMinProtocolVersion() { return minProtocolVersion; }
    public int getMaxProtocolVersion() { return maxProtocolVersion; }
    public Set<String> getCapabilities() { return set(capabilitiesCsv); }
    public Set<String> getTransportModes() { return set(transportModesCsv); }
    public String getReceiptSchemaVersion() { return receiptSchemaVersion; }
    public String getManifestSha256() { return manifestSha256; }
    public String getSignerKeyId() { return signerKeyId; }
    public String getSignatureSha256() { return signatureSha256; }
    public String getStatus() { return status; }
    public boolean isSimulationOnly() { return simulationOnly; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
