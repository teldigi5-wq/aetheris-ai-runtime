package io.aetheris.orchestrator.stage13;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aetheris_stage13_testnet_journal", uniqueConstraints = @UniqueConstraint(columnNames = "clientOrderId"))
public class Stage13TestnetJournalEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID taskId;
    @Column(nullable = false, length = 80) private String providerId;
    @Column(nullable = false, length = 100) private String clientOrderId;
    @Column(nullable = false, length = 32) private String symbol;
    @Column(nullable = false, length = 8) private String side;
    @Column(nullable = false, length = 24) private String orderType;
    @Column(nullable = false, precision = 30, scale = 12) private BigDecimal quantity;
    @Column(nullable = false, length = 64) private String riskDecisionSha256;
    @Column(nullable = false, length = 56) private String status;
    @Column(length = 64) private String providerEvidenceSha256;
    @Column(nullable = false) private boolean liveMoneyEnabled;
    @Column(nullable = false) private boolean withdrawalOrTransferEnabled;
    @Column(nullable = false) private boolean externalActionAttempted;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected Stage13TestnetJournalEntity() {}

    public Stage13TestnetJournalEntity(UUID id, UUID taskId, String providerId, String clientOrderId, String symbol,
                                       String side, String orderType, BigDecimal quantity, String riskDecisionSha256) {
        this.id = id;
        this.taskId = taskId;
        this.providerId = providerId;
        this.clientOrderId = clientOrderId;
        this.symbol = symbol;
        this.side = side;
        this.orderType = orderType;
        this.quantity = quantity;
        this.riskDecisionSha256 = riskDecisionSha256;
        this.status = "TESTNET_INTENT_AUTHORIZED_NOT_EXECUTED";
        this.liveMoneyEnabled = false;
        this.withdrawalOrTransferEnabled = false;
        this.externalActionAttempted = false;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public UUID getTaskId() { return taskId; }
    public String getProviderId() { return providerId; }
    public String getClientOrderId() { return clientOrderId; }
    public String getSymbol() { return symbol; }
    public String getSide() { return side; }
    public String getOrderType() { return orderType; }
    public BigDecimal getQuantity() { return quantity; }
    public String getRiskDecisionSha256() { return riskDecisionSha256; }
    public String getStatus() { return status; }
    public String getProviderEvidenceSha256() { return providerEvidenceSha256; }
    public boolean isLiveMoneyEnabled() { return liveMoneyEnabled; }
    public boolean isWithdrawalOrTransferEnabled() { return withdrawalOrTransferEnabled; }
    public boolean isExternalActionAttempted() { return externalActionAttempted; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    public void recordProviderEvidence(String providerStatus, String attestationSha256) {
        this.status = "TESTNET_PROVIDER_REPORTED_" + providerStatus;
        this.providerEvidenceSha256 = attestationSha256;
        this.liveMoneyEnabled = false;
        this.withdrawalOrTransferEnabled = false;
        this.externalActionAttempted = false;
        this.updatedAt = Instant.now();
    }
}
