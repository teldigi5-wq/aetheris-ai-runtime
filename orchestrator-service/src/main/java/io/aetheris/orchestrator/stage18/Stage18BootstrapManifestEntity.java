package io.aetheris.orchestrator.stage18;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "stage18_bootstrap_manifests",
        uniqueConstraints = @UniqueConstraint(name = "uk_stage18_target_manifest_sha", columnNames = {"targetId", "manifestSha256"}))
public class Stage18BootstrapManifestEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 80) private String targetId;
    @Column(nullable = false, length = 80) private String adapterId;
    @Column(nullable = false, length = 24) private String platform;
    @Column(nullable = false, length = 64) private String packageSha256;
    @Column(nullable = false, length = 64) private String deviceCertificateSha256;
    @Column(nullable = false) private int minimumRamMb;
    @Column(nullable = false) private int minimumVramMb;
    @Column(nullable = false, length = 1200) private String capabilitiesCsv;
    @Column(nullable = false, length = 1200) private String providerAliasesCsv;
    @Column(nullable = false, length = 80) private String signerKeyId;
    @Column(nullable = false, length = 64) private String signatureSha256;
    @Column(nullable = false, length = 64) private String manifestSha256;
    @Column(nullable = false) private Instant issuedAt;
    @Column(nullable = false, length = 48) private String status;
    @Column(nullable = false) private boolean simulationOnly;
    @Column(nullable = false) private Instant createdAt;
    @Version private long version;

    protected Stage18BootstrapManifestEntity() {}

    public Stage18BootstrapManifestEntity(UUID id, String targetId, String adapterId, String platform,
                                          String packageSha256, String deviceCertificateSha256,
                                          int minimumRamMb, int minimumVramMb, Set<String> capabilities,
                                          Set<String> providerAliases, String signerKeyId,
                                          String signatureSha256, String manifestSha256, Instant issuedAt) {
        this.id = id; this.targetId = targetId; this.adapterId = adapterId; this.platform = platform;
        this.packageSha256 = packageSha256; this.deviceCertificateSha256 = deviceCertificateSha256;
        this.minimumRamMb = minimumRamMb; this.minimumVramMb = minimumVramMb;
        this.capabilitiesCsv = csv(capabilities); this.providerAliasesCsv = csv(providerAliases);
        this.signerKeyId = signerKeyId; this.signatureSha256 = signatureSha256;
        this.manifestSha256 = manifestSha256; this.issuedAt = issuedAt;
        this.status = "MANIFEST_VERIFIED_SIMULATION_ONLY"; this.simulationOnly = true;
        this.createdAt = Instant.now();
    }

    private static String csv(Set<String> values) {
        return values == null ? "" : values.stream().sorted().collect(Collectors.joining(","));
    }
    private static Set<String> set(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(",")).filter(v -> !v.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    public UUID getId() { return id; }
    public String getTargetId() { return targetId; }
    public String getAdapterId() { return adapterId; }
    public String getPlatform() { return platform; }
    public String getPackageSha256() { return packageSha256; }
    public String getDeviceCertificateSha256() { return deviceCertificateSha256; }
    public int getMinimumRamMb() { return minimumRamMb; }
    public int getMinimumVramMb() { return minimumVramMb; }
    public Set<String> getCapabilities() { return set(capabilitiesCsv); }
    public Set<String> getProviderAliases() { return set(providerAliasesCsv); }
    public String getSignerKeyId() { return signerKeyId; }
    public String getSignatureSha256() { return signatureSha256; }
    public String getManifestSha256() { return manifestSha256; }
    public Instant getIssuedAt() { return issuedAt; }
    public String getStatus() { return status; }
    public boolean isSimulationOnly() { return simulationOnly; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
