package io.aetheris.orchestrator.trading;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Stage46.34: in-transaction, read-only fingerprint of a local PAPER portfolio.
 * A digest is NOT an authentication tag, owner approval, commit token, lock
 * carried into future operations, or proof of a trusted market quotation.
 * No write / recovery / broker endpoint is provided.
 */
@Service
public class PaperRecoverySnapshotFingerprintService {
    private static final String ACCOUNT = "paper-default";
    private static final long MAX_MARK_AGE_MS = 120_000L;

    private final PaperAccountRepository accounts;
    private final PaperPositionRepository positions;
    private final PaperEquitySnapshotRepository marks;
    private final PaperDrawdownGuardRepository guards;
    private final OfflinePaperReplayAnchorRepository anchors;

    public PaperRecoverySnapshotFingerprintService(
            PaperAccountRepository accounts,
            PaperPositionRepository positions,
            PaperEquitySnapshotRepository marks,
            PaperDrawdownGuardRepository guards,
            OfflinePaperReplayAnchorRepository anchors) {
        this.accounts = accounts;
        this.positions = positions;
        this.marks = marks;
        this.guards = guards;
        this.anchors = anchors;
    }

    public record Assessment(
            boolean evidenceConsistent,
            boolean expectedFingerprintMatched,
            String snapshotFingerprint,
            List<String> blockers,
            boolean readOnly,
            boolean ownerAuthorized,
            boolean recoveryApplied,
            boolean drawdownReset,
            boolean paperOrTestnetOrdersAuthorized,
            boolean liveMoneyAuthorized,
            boolean productionAuthorized,
            String decision) {
        public Assessment { blockers = List.copyOf(blockers); }
    }

    /**
     * SERIALIZABLE isolation captures a single database-transaction view on
     * the disposable Spring/JPA test database. This does NOT guarantee a
     * persistent read lock for later operations or cross-process atomicity.
     * Current trade writers and external feeds do not share this approval gate.
     */
    @Transactional(readOnly = true, isolation = Isolation.SERIALIZABLE)
    public Assessment review(Instant now, String expectedFingerprint) {
        var blockers = new ArrayList<String>();
        if (now == null || now.toEpochMilli() <= 0) {
            return refusal(List.of("INVALID_REVIEW_CLOCK"), "");
        }
        if (expectedFingerprint != null &&
                !expectedFingerprint.matches("[0-9a-f]{64}")) {
            return refusal(List.of("MALFORMED_REFERENCE_FINGERPRINT"), "");
        }

        var a = accounts.findById(ACCOUNT);
        var g = guards.findById(ACCOUNT);
        var r = anchors.findById(ACCOUNT);
        var m = marks.findTop200ByOrderByObservedAtDesc();
        var open = positions.findByStatus(PaperPositionStatus.OPEN).stream()
                .sorted(Comparator.comparing(p -> p.getId() == null ? "" : p.getId().toString()))
                .toList();
        if (a.isEmpty()) blockers.add("PAPER_ACCOUNT_MISSING");
        if (g.isEmpty()) blockers.add("DRAWDOWN_GUARD_MISSING");
        if (r.isEmpty()) blockers.add("REPLAY_ANCHOR_MISSING");
        if (m.isEmpty()) blockers.add("MARK_MISSING");
        if (open.size() > 16) blockers.add("TOO_MANY_OPEN_POSITIONS");
        StringBuilder canonical = new StringBuilder("STAGE46_34_LOCAL_PAPER_SNAPSHOT_V1\n");
        if (a.isPresent()) {
            var x = a.get();
            canonical.append("account=").append(x.getAccountKey()).append('|')
                    .append(num(x.getStartingBalance())).append('|')
                    .append(num(x.getCashBalance())).append('|')
                    .append(num(x.getDailyRealizedPnl())).append('|')
                    .append(x.getDayKey()).append('|')
                    .append(time(x.getUpdatedAt())).append('\n');
            if (!ACCOUNT.equals(x.getAccountKey()) || !validMoney(x.getStartingBalance())
                    || !validMoney(x.getCashBalance())
                    || !Double.isFinite(x.getDailyRealizedPnl())
                    || x.getUpdatedAt() == null) {
                blockers.add("INVALID_ACCOUNT_STATE");
            }
        }
        if (g.isPresent()) {
            var x = g.get();
            canonical.append("guard=").append(x.getAccountKey()).append('|')
                    .append(x.getVersion()).append('|')
                    .append(num(x.getPeakEquity())).append('|')
                    .append(num(x.getLastEquity())).append('|')
                    .append(num(x.getMaxObservedDrawdownPct())).append('|')
                    .append(x.isHalted()).append('|')
                    .append(x.getReason()).append('|')
                    .append(time(x.getUpdatedAt())).append('|')
                    .append(time(x.getLastMarketMarkAt())).append('\n');
            if (x.invalidPersistedState()) blockers.add("INVALID_DRAWDOWN_STATE");
            if (x.isHalted()) blockers.add("DRAWDOWN_LATCHED");
        }
        if (r.isPresent()) {
            var x = r.get();
            canonical.append("replay=").append(x.getAccountKey()).append('|')
                    .append(x.getVersion()).append('|')
                    .append(x.getLastAcceptedSequence()).append('|')
                    .append(x.getLastAcceptedDigest()).append('|')
                    .append(time(x.getUpdatedAt())).append('\n');
            if (x.invalid()) blockers.add("INVALID_REPLAY_STATE");
        }
        double gross = 0;
        for (var p : open) {
            if (p == null) {
                blockers.add("INVALID_OPEN_POSITION");
                continue;
            }
            canonical.append("position=").append(p.getId()).append('|')
                    .append(p.getSignalId()).append('|').append(p.getSymbol()).append('|')
                    .append(p.getSide()).append('|').append(num(p.getEntryPrice()))
                    .append('|').append(num(p.getStopLoss()))
                    .append('|').append(num(p.getQuantity()))
                    .append('|').append(num(p.getNotional()))
                    .append('|').append(time(p.getOpenedAt())).append('\n');
            if (p.getId() == null || p.getSignalId() == null || p.getSymbol() == null
                    || p.getSymbol().isBlank() || p.getSide() == null
                    || p.getSide() == TradeSide.NO_TRADE
                    || !validMoney(p.getEntryPrice()) || !validMoney(p.getStopLoss())
                    || !validMoney(p.getQuantity()) || !validMoney(p.getNotional())
                    || p.getOpenedAt() == null
                    || (p.getSide() == TradeSide.LONG && p.getStopLoss() >= p.getEntryPrice())
                    || (p.getSide() == TradeSide.SHORT && p.getStopLoss() <= p.getEntryPrice())
                    || !near(p.getEntryPrice() * p.getQuantity(), p.getNotional())) {
                blockers.add("INVALID_OPEN_POSITION");
            }
            gross += p.getNotional();
        }
        if (!Double.isFinite(gross)) blockers.add("NONFINITE_AGGREGATE_EXPOSURE");
        if (!m.isEmpty()) {
            var x = m.get(0);
            canonical.append("latest-mark=").append(x.getId()).append('|')
                    .append(num(x.getCashBalance())).append('|')
                    .append(num(x.getEquity())).append('|')
                    .append(num(x.getUnrealizedPnl())).append('|')
                    .append(num(x.getGrossExposure())).append('|')
                    .append(x.getOpenPositions()).append('|')
                    .append(time(x.getObservedAt())).append('\n');
            if (x.getObservedAt() == null || x.getObservedAt().isAfter(now)
                    || x.getObservedAt().isBefore(now.minusMillis(MAX_MARK_AGE_MS))) {
                blockers.add("STALE_OR_FUTURE_MARK");
            }
            if (!validMoney(x.getCashBalance()) || !validMoney(x.getEquity())
                    || !Double.isFinite(x.getUnrealizedPnl())
                    || !Double.isFinite(x.getGrossExposure()) || x.getGrossExposure() < 0
                    || x.getOpenPositions() != open.size()
                    || !near(x.getEquity(), x.getCashBalance() + x.getUnrealizedPnl())
                    || !near(gross, x.getGrossExposure())) {
                blockers.add("INCONSISTENT_MARK_OR_POSITIONS");
            }
            if (a.isPresent() && !near(a.get().getCashBalance(), x.getCashBalance())) {
                blockers.add("ACCOUNT_MARK_CASH_DRIFT");
            }
            if (g.isPresent() && (g.get().getLastMarketMarkAt() == null
                    || g.get().getLastMarketMarkAt().isAfter(now)
                    || g.get().getLastMarketMarkAt().isBefore(now.minusMillis(MAX_MARK_AGE_MS))
                    || !near(g.get().getLastEquity(), x.getEquity()))) {
                blockers.add("DRAWDOWN_MARK_DRIFT");
            }
        }
        // Even a mathematically consistent snapshot cannot be authenticated
        // by SHA256; no trust root or live producer is implemented.
        if (!blockers.isEmpty()) return refusal(blockers, "");
        String fingerprint = sha256(canonical.toString());
        if (expectedFingerprint != null && !fingerprint.equals(expectedFingerprint)) {
            return refusal(List.of("SNAPSHOT_FINGERPRINT_CHANGED"), fingerprint);
        }
        return new Assessment(true, expectedFingerprint != null, fingerprint,
                List.of("OWNER_IDENTITY_AND_EXTERNAL_CUSTODY_NOT_VERIFIED"),
                true, false, false, false, false, false, false, "NO_GO");
    }

    private static Assessment refusal(List<String> blockers, String fingerprint) {
        return new Assessment(false, false, fingerprint, blockers,
                true, false, false, false, false, false, false, "NO_GO");
    }

    private static String num(double value) { return Double.toHexString(value); }
    private static String time(Instant value) { return value == null ? "missing" : value.toString(); }
    private static boolean validMoney(double value) { return Double.isFinite(value) && value > 0; }
    private static boolean near(double a, double b) {
        return Double.isFinite(a) && Double.isFinite(b) &&
                Math.abs(a - b) <= 1e-7 * Math.max(1, Math.max(Math.abs(a), Math.abs(b)));
    }
    private static String sha256(String source) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("SHA256_UNAVAILABLE", ex); }
    }
}
