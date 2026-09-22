package io.aetheris.orchestrator.mcp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "aetheris_mcp_servers")
public class McpServerEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String serverKey;

    @Column(nullable = false, length = 200)
    private String displayName;

    @Column(nullable = false, length = 2000)
    private String endpoint;

    @Column(nullable = false)
    private boolean local;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, length = 4000)
    private String approvedCapabilitiesCsv;

    @Column(nullable = false, length = 4000)
    private String allowedDataClassesCsv;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private McpServerStatus status;

    @Column(length = 2000)
    private String healthDetail;

    private Instant lastHealthAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected McpServerEntity() {
    }

    public McpServerEntity(UUID id, String serverKey, String displayName, String endpoint, boolean local, boolean enabled,
                           Set<String> approvedCapabilities, Set<String> allowedDataClasses) {
        this.id = id;
        this.serverKey = serverKey;
        this.displayName = displayName;
        this.endpoint = endpoint;
        this.local = local;
        this.enabled = enabled;
        this.approvedCapabilitiesCsv = join(approvedCapabilities);
        this.allowedDataClassesCsv = join(allowedDataClasses);
        this.status = enabled ? McpServerStatus.UNKNOWN : McpServerStatus.DISABLED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public String getServerKey() { return serverKey; }
    public String getDisplayName() { return displayName; }
    public String getEndpoint() { return endpoint; }
    public boolean isLocal() { return local; }
    public boolean isEnabled() { return enabled; }
    public Set<String> getApprovedCapabilities() { return split(approvedCapabilitiesCsv); }
    public Set<String> getAllowedDataClasses() { return split(allowedDataClassesCsv); }
    public McpServerStatus getStatus() { return status; }
    public String getHealthDetail() { return healthDetail; }
    public Instant getLastHealthAt() { return lastHealthAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void reconfigure(String displayName, String endpoint, boolean local, boolean enabled,
                            Set<String> approvedCapabilities, Set<String> allowedDataClasses) {
        this.displayName = displayName;
        this.endpoint = endpoint;
        this.local = local;
        this.enabled = enabled;
        this.approvedCapabilitiesCsv = join(approvedCapabilities);
        this.allowedDataClassesCsv = join(allowedDataClasses);
        if (!enabled) this.status = McpServerStatus.DISABLED;
        else if (this.status == McpServerStatus.DISABLED) this.status = McpServerStatus.UNKNOWN;
        this.updatedAt = Instant.now();
    }

    public void recordHealth(boolean healthy, String detail) {
        if (!enabled) {
            this.status = McpServerStatus.DISABLED;
        } else {
            this.status = healthy ? McpServerStatus.HEALTHY : McpServerStatus.UNHEALTHY;
        }
        this.healthDetail = detail;
        this.lastHealthAt = Instant.now();
        this.updatedAt = this.lastHealthAt;
    }

    private static String join(Set<String> values) {
        if (values == null || values.isEmpty()) return "";
        return values.stream().map(String::trim).filter(value -> !value.isBlank()).sorted().collect(Collectors.joining(","));
    }

    private static Set<String> split(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
