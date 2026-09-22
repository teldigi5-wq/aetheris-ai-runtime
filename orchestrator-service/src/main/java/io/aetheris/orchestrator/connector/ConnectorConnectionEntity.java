package io.aetheris.orchestrator.connector;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(
        name = "connector_connections",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_connector_provider_account",
                columnNames = {"provider", "external_account_ref"}
        )
)
public class ConnectorConnectionEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ConnectorProvider provider;

    @Column(name = "external_account_ref", nullable = false, length = 240)
    private String externalAccountRef;

    @Column(nullable = false, length = 240)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConnectorStatus status;

    @Column(nullable = false, length = 1200)
    private String capabilitiesCsv;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ConnectorConnectionEntity() {}

    public ConnectorConnectionEntity(UUID id, String ownerId, ConnectorProvider provider,
                                     String externalAccountRef, String displayName,
                                     Set<ConnectorCapability> capabilities) {
        Instant now = Instant.now();
        this.id = id;
        this.ownerId = ownerId;
        this.provider = provider;
        this.externalAccountRef = externalAccountRef;
        this.displayName = displayName;
        this.status = ConnectorStatus.REGISTERED;
        this.capabilitiesCsv = encodeCapabilities(capabilities);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateStatus(ConnectorStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public boolean supports(ConnectorCapability capability) {
        return capabilities().contains(capability);
    }

    public Set<ConnectorCapability> capabilities() {
        EnumSet<ConnectorCapability> result = EnumSet.noneOf(ConnectorCapability.class);
        if (capabilitiesCsv == null || capabilitiesCsv.isBlank()) return result;
        Arrays.stream(capabilitiesCsv.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(ConnectorCapability::valueOf)
                .forEach(result::add);
        return result;
    }

    private static String encodeCapabilities(Set<ConnectorCapability> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) return "";
        return capabilities.stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    public UUID getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public ConnectorProvider getProvider() { return provider; }
    public String getExternalAccountRef() { return externalAccountRef; }
    public String getDisplayName() { return displayName; }
    public ConnectorStatus getStatus() { return status; }
    public String getCapabilitiesCsv() { return capabilitiesCsv; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
