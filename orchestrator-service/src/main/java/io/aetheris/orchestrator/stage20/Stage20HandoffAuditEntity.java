package io.aetheris.orchestrator.stage20;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stage20_handoff_audit")
public class Stage20HandoffAuditEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID authorizationId;
    @Column(nullable = false, length = 80) private String targetId;
    @Column(nullable = false, length = 64) private String eventType;
    @Column(nullable = false, length = 64) private String state;
    @Column(nullable = false, length = 64) private String detailSha256;
    @Column(nullable = false) private boolean simulationOnly;
    @Column(nullable = false) private boolean productionActivationAllowed;
    @Column(nullable = false) private boolean targetMutated;
    @Column(nullable = false) private boolean externalActionAttempted;
    @Column(nullable = false) private Instant observedAt;
    @Version private long version;

    protected Stage20HandoffAuditEntity() {}

    public Stage20HandoffAuditEntity(UUID id, UUID authorizationId, String targetId, String eventType,
                                     String state, String detailSha256) {
        this.id = id; this.authorizationId = authorizationId; this.targetId = targetId;
        this.eventType = eventType; this.state = state; this.detailSha256 = detailSha256;
        this.simulationOnly = true; this.productionActivationAllowed = false;
        this.targetMutated = false; this.externalActionAttempted = false; this.observedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAuthorizationId() { return authorizationId; }
    public String getTargetId() { return targetId; }
    public String getEventType() { return eventType; }
    public String getState() { return state; }
    public String getDetailSha256() { return detailSha256; }
    public boolean isSimulationOnly() { return simulationOnly; }
    public boolean isProductionActivationAllowed() { return productionActivationAllowed; }
    public boolean isTargetMutated() { return targetMutated; }
    public boolean isExternalActionAttempted() { return externalActionAttempted; }
    public Instant getObservedAt() { return observedAt; }
    public long getVersion() { return version; }
}
