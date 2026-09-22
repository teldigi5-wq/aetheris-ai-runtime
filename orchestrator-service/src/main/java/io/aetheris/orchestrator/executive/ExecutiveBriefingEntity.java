package io.aetheris.orchestrator.executive;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "executive_briefings")
public class ExecutiveBriefingEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 120)
    private String ownerId;
    @Column(nullable = false)
    private Instant generatedAt;
    @Column(nullable = false)
    private Instant nextBriefingAt;
    private UUID voiceSessionId;
    private int totalSignals;
    private int autoHandled;
    private int drafts;
    private int approvalsRequired;
    private int signups;
    private int verifiedCodeUpdates;
    @Column(nullable = false, length = 8000)
    private String briefing;

    protected ExecutiveBriefingEntity() {}

    public ExecutiveBriefingEntity(UUID id, String ownerId, Instant generatedAt, Instant nextBriefingAt,
                                    UUID voiceSessionId, int totalSignals, int autoHandled, int drafts,
                                    int approvalsRequired, int signups, int verifiedCodeUpdates, String briefing) {
        this.id = id;
        this.ownerId = ownerId;
        this.generatedAt = generatedAt;
        this.nextBriefingAt = nextBriefingAt;
        this.voiceSessionId = voiceSessionId;
        this.totalSignals = totalSignals;
        this.autoHandled = autoHandled;
        this.drafts = drafts;
        this.approvalsRequired = approvalsRequired;
        this.signups = signups;
        this.verifiedCodeUpdates = verifiedCodeUpdates;
        this.briefing = briefing;
    }

    public UUID getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public Instant getGeneratedAt() { return generatedAt; }
    public Instant getNextBriefingAt() { return nextBriefingAt; }
    public UUID getVoiceSessionId() { return voiceSessionId; }
    public int getTotalSignals() { return totalSignals; }
    public int getAutoHandled() { return autoHandled; }
    public int getDrafts() { return drafts; }
    public int getApprovalsRequired() { return approvalsRequired; }
    public int getSignups() { return signups; }
    public int getVerifiedCodeUpdates() { return verifiedCodeUpdates; }
    public String getBriefing() { return briefing; }
}
