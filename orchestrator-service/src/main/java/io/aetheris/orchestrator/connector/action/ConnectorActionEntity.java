package io.aetheris.orchestrator.connector.action;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "connector_actions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_connector_action_idempotency",
                columnNames = {"connection_id", "idempotency_key"}
        )
)
public class ConnectorActionEntity {
    @Id
    private UUID id;

    @Column(name = "connection_id", nullable = false)
    private UUID connectionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private io.aetheris.orchestrator.connector.ConnectorProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private ConnectorActionKind actionKind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ConnectorActionExecutionMode executionMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConnectorActionStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 240)
    private String idempotencyKey;

    @Column(nullable = false, length = 1200)
    private String targetRef;

    @Column(nullable = false, length = 2400)
    private String summary;

    @Column(nullable = false)
    private UUID taskId;

    @Column(nullable = false)
    private UUID approvalId;

    @Column(nullable = false, length = 160)
    private String actionType;

    @Column(length = 600)
    private String externalReference;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant executedAt;

    protected ConnectorActionEntity() {}

    public ConnectorActionEntity(UUID id, UUID connectionId,
                                 io.aetheris.orchestrator.connector.ConnectorProvider provider,
                                 ConnectorActionKind actionKind,
                                 ConnectorActionExecutionMode executionMode,
                                 String idempotencyKey, String targetRef, String summary,
                                 UUID taskId, UUID approvalId, String actionType) {
        this.id = id;
        this.connectionId = connectionId;
        this.provider = provider;
        this.actionKind = actionKind;
        this.executionMode = executionMode;
        this.status = ConnectorActionStatus.PENDING_APPROVAL;
        this.idempotencyKey = idempotencyKey;
        this.targetRef = targetRef;
        this.summary = summary;
        this.taskId = taskId;
        this.approvalId = approvalId;
        this.actionType = actionType;
        this.createdAt = Instant.now();
    }

    public void markExecuted(String externalReference) {
        this.status = ConnectorActionStatus.EXECUTED;
        this.externalReference = externalReference;
        this.executedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getConnectionId() { return connectionId; }
    public io.aetheris.orchestrator.connector.ConnectorProvider getProvider() { return provider; }
    public ConnectorActionKind getActionKind() { return actionKind; }
    public ConnectorActionExecutionMode getExecutionMode() { return executionMode; }
    public ConnectorActionStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getTargetRef() { return targetRef; }
    public String getSummary() { return summary; }
    public UUID getTaskId() { return taskId; }
    public UUID getApprovalId() { return approvalId; }
    public String getActionType() { return actionType; }
    public String getExternalReference() { return externalReference; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExecutedAt() { return executedAt; }
}
