package io.aetheris.orchestrator.trading;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Stage46.31: durable anti-replay anchor for OFFLINE SYNTHETIC PAPER messages.
 * Holds no API keys, cash balances, positions, broker accounts or permissions.
 * There is deliberately no method to reset or delete a consumed sequence.
 */
@Entity
@Table(name = "aetheris_synthetic_paper_replay_anchor")
public class OfflinePaperReplayAnchorEntity {
    @Id
    @Column(length = 80, nullable = false)
    private String accountKey;

    @Version
    private long version;

    private long lastAcceptedSequence;

    @Column(nullable = false, length = 64)
    private String lastAcceptedDigest;

    @Column(nullable = false)
    private Instant updatedAt;

    protected OfflinePaperReplayAnchorEntity() {}

    public OfflinePaperReplayAnchorEntity(String accountKey, long sequence, String digest) {
        if (!"paper-default".equals(accountKey) || sequence != 1 ||
                digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("INVALID_INERT_REPLAY_ANCHOR_SEED");
        }
        this.accountKey = accountKey;
        this.lastAcceptedSequence = sequence;
        this.lastAcceptedDigest = digest;
        this.updatedAt = Instant.now();
    }

    public void advance(long nextSequence, String previousDigest, String nextDigest) {
        if (nextSequence <= 1 || lastAcceptedSequence == Long.MAX_VALUE ||
                nextSequence != lastAcceptedSequence + 1 ||
                previousDigest == null ||
                !previousDigest.equals(lastAcceptedDigest) ||
                nextDigest == null || !nextDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("REPLAY_OR_CHAIN_DISCONTINUITY");
        }
        this.lastAcceptedSequence = nextSequence;
        this.lastAcceptedDigest = nextDigest;
        this.updatedAt = Instant.now();
    }

    public boolean invalid() {
        return !"paper-default".equals(accountKey) || lastAcceptedSequence <= 0 ||
                lastAcceptedDigest == null ||
                !lastAcceptedDigest.matches("[0-9a-f]{64}") ||
                updatedAt == null;
    }

    public String getAccountKey() { return accountKey; }
    public long getVersion() { return version; }
    public long getLastAcceptedSequence() { return lastAcceptedSequence; }
    public String getLastAcceptedDigest() { return lastAcceptedDigest; }
    public Instant getUpdatedAt() { return updatedAt; }
}
