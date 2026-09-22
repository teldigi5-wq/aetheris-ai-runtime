package io.aetheris.orchestrator.stage15;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stage15_delivery_receipts")
public class Stage15DeliveryReceiptEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID incidentId;
    @Column(nullable = false, length = 80) private String providerId;
    @Column(nullable = false, length = 160) private String providerMessageId;
    @Column(nullable = false, length = 24) private String deliveryStatus;
    @Column(nullable = false, length = 64) private String attestationSha256;
    @Column(nullable = false) private boolean providerMeasured;
    @Column(nullable = false) private Instant observedAt;
    @Column(nullable = false) private Instant createdAt;

    protected Stage15DeliveryReceiptEntity() {}
    public Stage15DeliveryReceiptEntity(UUID id, UUID incidentId, String providerId, String providerMessageId,
                                        String deliveryStatus, String attestationSha256, boolean providerMeasured, Instant observedAt) {
        this.id = id; this.incidentId = incidentId; this.providerId = providerId; this.providerMessageId = providerMessageId;
        this.deliveryStatus = deliveryStatus; this.attestationSha256 = attestationSha256;
        this.providerMeasured = providerMeasured; this.observedAt = observedAt; this.createdAt = Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getIncidentId() { return incidentId; }
    public String getProviderId() { return providerId; }
    public String getProviderMessageId() { return providerMessageId; }
    public String getDeliveryStatus() { return deliveryStatus; }
    public String getAttestationSha256() { return attestationSha256; }
    public boolean isProviderMeasured() { return providerMeasured; }
    public Instant getObservedAt() { return observedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
