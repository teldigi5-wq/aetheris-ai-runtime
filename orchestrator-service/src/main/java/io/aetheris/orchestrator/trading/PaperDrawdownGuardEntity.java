package io.aetheris.orchestrator.trading;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/** Persistent PAPER-only drawdown circuit; no external release/reset route. */
@Entity
@Table(name = "aetheris_paper_drawdown_guard")
public class PaperDrawdownGuardEntity {
    @Id
    @Column(length = 80)
    private String accountKey;
    @Version
    private long version;
    private double peakEquity;
    private double lastEquity;
    private double maxObservedDrawdownPct;
    private boolean halted;
    @Column(nullable = false, length = 120)
    private String reason;
    @Column(nullable = false)
    private Instant updatedAt;
    private Instant haltedAt;
    private Instant lastMarketMarkAt;

    protected PaperDrawdownGuardEntity() {}

    public PaperDrawdownGuardEntity(String accountKey, double initialEquity) {
        if (accountKey == null || accountKey.isBlank() ||
                !Double.isFinite(initialEquity) || initialEquity <= 0) {
            throw new IllegalArgumentException("Valid paper circuit identity and equity required");
        }
        this.accountKey = accountKey;
        this.peakEquity = initialEquity;
        this.lastEquity = initialEquity;
        this.maxObservedDrawdownPct = 0;
        this.reason = "INITIAL_PAPER_CASH_BASELINE_NOT_MARKET_CERTIFIED";
        this.updatedAt = Instant.now();
    }

    public void observe(double newEquity, boolean marketMark) {
        PaperDrawdownPolicy.Decision decision = PaperDrawdownPolicy.review(
                peakEquity, lastEquity, maxObservedDrawdownPct, halted, reason, newEquity);
        this.halted = decision.halted();
        this.reason = decision.reason();
        this.peakEquity = decision.peakEquity();
        this.lastEquity = decision.observedEquity();
        this.maxObservedDrawdownPct = decision.maxObservedDrawdownPct();
        this.updatedAt = Instant.now();
        if (marketMark && Double.isFinite(newEquity) && newEquity > 0) {
            this.lastMarketMarkAt = updatedAt;
        }
        if (halted && haltedAt == null) this.haltedAt = updatedAt;
    }

    public boolean invalidPersistedState() {
        return accountKey == null || accountKey.isBlank() ||
                !Double.isFinite(peakEquity) || peakEquity <= 0 ||
                !Double.isFinite(lastEquity) || lastEquity <= 0 ||
                lastEquity > peakEquity + 0.0000001 ||
                !Double.isFinite(maxObservedDrawdownPct) ||
                maxObservedDrawdownPct < 0 || maxObservedDrawdownPct > 100 ||
                reason == null || reason.isBlank() || updatedAt == null ||
                (halted && haltedAt == null);
    }

    public String getAccountKey() { return accountKey; }
    public long getVersion() { return version; }
    public double getPeakEquity() { return peakEquity; }
    public double getLastEquity() { return lastEquity; }
    public double getMaxObservedDrawdownPct() { return maxObservedDrawdownPct; }
    public boolean isHalted() { return halted; }
    public String getReason() { return reason; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getHaltedAt() { return haltedAt; }
    public Instant getLastMarketMarkAt() { return lastMarketMarkAt; }
}
