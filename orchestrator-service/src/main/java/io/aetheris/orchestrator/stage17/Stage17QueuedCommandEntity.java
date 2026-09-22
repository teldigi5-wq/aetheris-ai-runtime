package io.aetheris.orchestrator.stage17;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stage17_offline_queue",
        uniqueConstraints = @UniqueConstraint(name = "uk_stage17_queue_envelope", columnNames = "envelopeId"))
public class Stage17QueuedCommandEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 80) private String adapterId;
    @Column(nullable = false) private UUID envelopeId;
    @Column(nullable = false, length = 48) private String action;
    @Column(nullable = false, length = 80) private String target;
    @Column(nullable = false, length = 64) private String commandSha256;
    @Column(nullable = false, length = 40) private String state;
    @Column(length = 600) private String terminalReason;
    @Column(nullable = false) private Instant queuedAt;
    @Column(nullable = false) private Instant expiresAt;
    private Instant deliveredAt;
    @Column(nullable = false) private boolean externalActionAttempted;
    @Version private long version;

    protected Stage17QueuedCommandEntity() {}

    public Stage17QueuedCommandEntity(UUID id, String adapterId, UUID envelopeId, String action, String target,
                                      String commandSha256, Instant expiresAt) {
        this.id = id; this.adapterId = adapterId; this.envelopeId = envelopeId; this.action = action;
        this.target = target; this.commandSha256 = commandSha256; this.state = "QUEUED_OFFLINE";
        this.queuedAt = Instant.now(); this.expiresAt = expiresAt; this.externalActionAttempted = false;
    }

    public void cancel(String reason) {
        requirePending(); this.state = "CANCELLED"; this.terminalReason = reason;
    }
    public void revoke(String reason) {
        requirePending(); this.state = "REVOKED"; this.terminalReason = reason;
    }
    public void expire() {
        requirePending(); this.state = "EXPIRED"; this.terminalReason = "underlying Stage 16 envelope expired";
    }
    public void deliverSimulation() {
        requirePending(); this.state = "DELIVERED_SIMULATION"; this.deliveredAt = Instant.now();
        this.externalActionAttempted = false;
    }
    private void requirePending() {
        if (!"QUEUED_OFFLINE".equals(state)) throw new IllegalStateException("Only queued Stage 17 commands can transition");
    }

    public UUID getId() { return id; }
    public String getAdapterId() { return adapterId; }
    public UUID getEnvelopeId() { return envelopeId; }
    public String getAction() { return action; }
    public String getTarget() { return target; }
    public String getCommandSha256() { return commandSha256; }
    public String getState() { return state; }
    public String getTerminalReason() { return terminalReason; }
    public Instant getQueuedAt() { return queuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public boolean isExternalActionAttempted() { return externalActionAttempted; }
    public long getVersion() { return version; }
}
