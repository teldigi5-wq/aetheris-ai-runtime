package io.aetheris.orchestrator.stage16;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "stage16_adapters")
public class Stage16AdapterEntity {
    @Id @Column(length = 80)
    private String id;
    @Column(nullable = false, length = 160)
    private String displayName;
    @Column(nullable = false, length = 40)
    private String adapterType;
    @Column(nullable = false, length = 800)
    private String capabilitiesCsv;
    @Column(nullable = false)
    private boolean simulationOnly;
    @Column(nullable = false, length = 32)
    private String attestationKind;
    @Column(nullable = false, length = 64)
    private String attestationSha256;
    @Column(nullable = false)
    private Instant attestedAt;
    @Column(nullable = false)
    private boolean enabled;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Stage16AdapterEntity() {}

    public Stage16AdapterEntity(String id, String displayName, String adapterType, Set<String> capabilities,
                                boolean simulationOnly, String attestationKind, String attestationSha256,
                                Instant attestedAt) {
        this.id = id;
        this.displayName = displayName;
        this.adapterType = adapterType;
        this.capabilitiesCsv = csv(capabilities);
        this.simulationOnly = simulationOnly;
        this.attestationKind = attestationKind;
        this.attestationSha256 = attestationSha256;
        this.attestedAt = attestedAt;
        this.enabled = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    private static String csv(Set<String> values) {
        return values == null ? "" : values.stream().sorted().collect(Collectors.joining(","));
    }
    private static Set<String> set(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(",")).filter(v -> !v.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getAdapterType() { return adapterType; }
    public Set<String> getCapabilities() { return set(capabilitiesCsv); }
    public boolean isSimulationOnly() { return simulationOnly; }
    public String getAttestationKind() { return attestationKind; }
    public String getAttestationSha256() { return attestationSha256; }
    public Instant getAttestedAt() { return attestedAt; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
