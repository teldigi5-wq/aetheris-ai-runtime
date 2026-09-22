package io.aetheris.orchestrator.connector;

import io.aetheris.orchestrator.executive.ExecutiveDisposition;
import io.aetheris.orchestrator.executive.ExecutiveSignalType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "connector_event_receipts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_connector_event",
                columnNames = {"connection_id", "external_event_id"}
        )
)
public class ConnectorEventReceiptEntity {
    @Id
    private UUID id;

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    @Column(name = "external_event_id", nullable = false, length = 240)
    private String externalEventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ConnectorProvider provider;

    @Column(nullable = false, length = 320)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ExecutiveSignalType signalType;

    @Column(nullable = false)
    private Instant receivedAt;

    private Instant occurredAt;

    private boolean deliveryVerified;
    private boolean trustedLowRiskRequested;
    private boolean trustedLowRiskEffective;

    @Column(nullable = false)
    private UUID executiveRunId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ExecutiveDisposition disposition;

    private UUID taskId;
    private UUID approvalId;

    @Column(nullable = false, length = 2400)
    private String summary;

    protected ConnectorEventReceiptEntity() {}

    public ConnectorEventReceiptEntity(UUID id, UUID connectionId, String externalEventId,
                                       ConnectorProvider provider, String source,
                                       ExecutiveSignalType signalType, Instant receivedAt,
                                       Instant occurredAt, boolean deliveryVerified,
                                       boolean trustedLowRiskRequested,
                                       boolean trustedLowRiskEffective, UUID executiveRunId,
                                       ExecutiveDisposition disposition, UUID taskId,
                                       UUID approvalId, String summary) {
        this.id = id;
        this.connectionId = connectionId;
        this.externalEventId = externalEventId;
        this.provider = provider;
        this.source = source;
        this.signalType = signalType;
        this.receivedAt = receivedAt;
        this.occurredAt = occurredAt;
        this.deliveryVerified = deliveryVerified;
        this.trustedLowRiskRequested = trustedLowRiskRequested;
        this.trustedLowRiskEffective = trustedLowRiskEffective;
        this.executiveRunId = executiveRunId;
        this.disposition = disposition;
        this.taskId = taskId;
        this.approvalId = approvalId;
        this.summary = summary;
    }

    public UUID getId() { return id; }
    public UUID getConnectionId() { return connectionId; }
    public String getExternalEventId() { return externalEventId; }
    public ConnectorProvider getProvider() { return provider; }
    public String getSource() { return source; }
    public ExecutiveSignalType getSignalType() { return signalType; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getOccurredAt() { return occurredAt; }
    public boolean isDeliveryVerified() { return deliveryVerified; }
    public boolean isTrustedLowRiskRequested() { return trustedLowRiskRequested; }
    public boolean isTrustedLowRiskEffective() { return trustedLowRiskEffective; }
    public UUID getExecutiveRunId() { return executiveRunId; }
    public ExecutiveDisposition getDisposition() { return disposition; }
    public UUID getTaskId() { return taskId; }
    public UUID getApprovalId() { return approvalId; }
    public String getSummary() { return summary; }
}
