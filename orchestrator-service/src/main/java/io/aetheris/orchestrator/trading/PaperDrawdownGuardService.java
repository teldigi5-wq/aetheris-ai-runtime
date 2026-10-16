package io.aetheris.orchestrator.trading;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistent paper-only circuit, never an exchange or testnet kill switch.
 * Row writes serialize existing circuit updates. An absent row is initialized
 * conservatively from account starting cash; simultaneous first-row creation
 * may fail closed via the primary-key constraint, not silently authorize risk.
 */
@Service
public class PaperDrawdownGuardService {
    private static final String PAPER_ACCOUNT = "paper-default";
    private final PaperDrawdownGuardRepository guards;

    public PaperDrawdownGuardService(PaperDrawdownGuardRepository guards) {
        this.guards = guards;
    }

    public record OpeningDecision(boolean allowed, String reason,
                                  boolean paperHalted, double peakEquity,
                                  double lastEquity, double maxDrawdownPct,
                                  boolean exchangeAuthorized) {}

    /**
     * Called before all new Java paper position risk approvals. The last
     * observed market mark is NOT refreshed here; consequently stale/failed
     * pricing remains a separate unresolved integration requirement.
     */
    @Transactional
    public OpeningDecision forNewPaperExposure(PaperAccountEntity account) {
        if (!validAccount(account)) {
            return new OpeningDecision(false, "INVALID_PAPER_ACCOUNT_FOR_DRAWDOWN",
                    true, 0, 0, 0, false);
        }
        PaperDrawdownGuardEntity guard = lockedOrInitialize(account);
        if (guard.invalidPersistedState()) {
            return new OpeningDecision(false, "CORRUPT_PAPER_CIRCUIT_STATE",
                    true, guard.getPeakEquity(), guard.getLastEquity(),
                    guard.getMaxObservedDrawdownPct(), false);
        }

        // Cash losses realized after the most recent mark must never be
        // invisible to the circuit. An unrealized gain cannot automatically
        // unlock previous drawdown / increase available risk.
        if (account.getCashBalance() < guard.getLastEquity()) {
            guard.observe(account.getCashBalance(), false);
            guards.save(guard);
        }
        return new OpeningDecision(!guard.isHalted(),
                guard.isHalted() ? guard.getReason() : "PAPER_DRAWDOWN_GUARD_OPEN",
                guard.isHalted(), guard.getPeakEquity(), guard.getLastEquity(),
                guard.getMaxObservedDrawdownPct(), false);
    }

    /**
     * Independent commit of a marked-equity halt. This ensures invalid
     * pricing evidence can latch the guard even when the caller refuses to
     * persist a corresponding invalid equity snapshot. No auto-reset.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OpeningDecision observeMarkedEquity(PaperAccountEntity account, double equity) {
        if (!validAccount(account)) {
            throw new IllegalArgumentException("INVALID_PAPER_MARK_ACCOUNT");
        }
        PaperDrawdownGuardEntity guard = lockedOrInitialize(account);
        if (guard.invalidPersistedState()) {
            return new OpeningDecision(false, "CORRUPT_PAPER_CIRCUIT_STATE",
                    true, guard.getPeakEquity(), guard.getLastEquity(),
                    guard.getMaxObservedDrawdownPct(), false);
        }
        guard.observe(equity, true);
        guards.saveAndFlush(guard);
        return new OpeningDecision(!guard.isHalted(), guard.getReason(),
                guard.isHalted(), guard.getPeakEquity(), guard.getLastEquity(),
                guard.getMaxObservedDrawdownPct(), false);
    }

    private PaperDrawdownGuardEntity lockedOrInitialize(PaperAccountEntity account) {
        return guards.locked(PAPER_ACCOUNT).orElseGet(() -> {
            double baseline = Math.max(account.getStartingBalance(), account.getCashBalance());
            if (!Double.isFinite(baseline) || baseline <= 0) {
                throw new IllegalArgumentException("INVALID_PAPER_CIRCUIT_BASELINE");
            }
            PaperDrawdownGuardEntity created = new PaperDrawdownGuardEntity(PAPER_ACCOUNT, baseline);
            created.observe(account.getCashBalance(), false);
            return guards.saveAndFlush(created);
        });
    }

    private static boolean validAccount(PaperAccountEntity account) {
        return account != null && Double.isFinite(account.getStartingBalance())
                && account.getStartingBalance() > 0
                && Double.isFinite(account.getCashBalance())
                && account.getCashBalance() > 0
                && Double.isFinite(account.getDailyRealizedPnl());
    }
}
