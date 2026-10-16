package io.aetheris.orchestrator.trading;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Stage46.33: supervised PAPER recovery EVIDENCE, NOT a recovery operation.
 *
 * It deliberately does not have a write API, authorization adapter, key
 * provider, account mutation, drawdown reset, model tool or broker client.
 * Checkpoint verification uses a separately supplied PUBLIC synthetic key
 * until a real external trust root exists. All outcomes remain NO_GO.
 */
@Service
public class PaperRecoveryReviewService {
    private static final String ACCOUNT = "paper-default";
    private static final long MAX_MARK_AGE_MS = 120_000L;
    private static final long MAX_MARK_GUARD_SKEW_MS = 120_000L;

    private final OfflinePaperRollbackCheckpointService checkpointReview;
    private final OfflinePaperReplayAnchorRepository anchors;
    private final PaperAccountRepository accounts;
    private final PaperPositionRepository positions;
    private final PaperEquitySnapshotRepository equity;
    private final PaperDrawdownGuardRepository guards;

    public PaperRecoveryReviewService(
            OfflinePaperRollbackCheckpointService checkpointReview,
            OfflinePaperReplayAnchorRepository anchors,
            PaperAccountRepository accounts,
            PaperPositionRepository positions,
            PaperEquitySnapshotRepository equity,
            PaperDrawdownGuardRepository guards) {
        this.checkpointReview = checkpointReview;
        this.anchors = anchors;
        this.accounts = accounts;
        this.positions = positions;
        this.equity = equity;
        this.guards = guards;
    }

    public record Review(
            String classification,
            boolean evidenceEligibleForIndependentHumanReview,
            List<String> blockers,
            long observedReplaySequence,
            long pinnedCheckpointSequence,
            int observedOpenPositions,
            boolean marketMarkFresh,
            boolean stateMutated,
            boolean drawdownCircuitReset,
            boolean ownerApprovedRecovery,
            boolean testnetOrdersAuthorized,
            boolean liveMoneyAuthorized,
            boolean productionAuthorized,
            String decision) {
        public Review {
            blockers = List.copyOf(blockers);
        }
    }

    /**
     * Read-only, point-in-time diagnostic. DOES NOT guarantee an atomic
     * distributed snapshot: checkpoint.compare() has its own transaction and
     * no shared custody of the signature. This cannot authorize recovery.
     *
     * @param fixtureClockMs synthetic checkpoint validation clock, not owner time
     * @param assessmentAt independently supplied time for actual paper marks
     */
    @Transactional(readOnly = true)
    public Review assess(JsonNode checkpoint, String tag, byte[] suppliedPublicTestKey,
                         long fixtureClockMs, Instant assessmentAt) {
        List<String> blockers = new ArrayList<>();
        if (assessmentAt == null || assessmentAt.toEpochMilli() <= 0) {
            return denied(List.of("UNTRUSTED_ASSESSMENT_CLOCK"), 0, 0, 0, false);
        }

        OfflinePaperRollbackCheckpointService.Review pinned =
                checkpointReview.compare(checkpoint, tag, suppliedPublicTestKey, fixtureClockMs);
        if (!pinned.checkpointAuthenticatedWithSuppliedTestKey() ||
                !pinned.databaseMatchesPresentedCheckpoint()) {
            blockers.add("CHECKPOINT_" + pinned.classification());
        }
        if (!pinned.independentTrustRootVerified()) {
            blockers.add("INDEPENDENT_CHECKPOINT_CUSTODY_UNVERIFIED");
        }

        var anchorOpt = anchors.findById(ACCOUNT);
        long sequence = 0;
        if (anchorOpt.isEmpty() || anchorOpt.get().invalid()) {
            blockers.add("DURABLE_REPLAY_ANCHOR_UNAVAILABLE");
        } else {
            sequence = anchorOpt.get().getLastAcceptedSequence();
            if (sequence != pinned.pinnedSequence()) {
                blockers.add("CHECKPOINT_ANCHOR_SEQUENCE_DRIFT");
            }
            if (checkpoint == null || !checkpoint.path("highest_digest").isTextual()
                    || !anchorOpt.get().getLastAcceptedDigest().equals(
                        checkpoint.path("highest_digest").textValue())) {
                blockers.add("CHECKPOINT_ANCHOR_DIGEST_DRIFT");
            }
        }

        var accountOpt = accounts.findById(ACCOUNT);
        PaperAccountEntity account = null;
        if (accountOpt.isEmpty()) {
            blockers.add("PAPER_ACCOUNT_MISSING");
        } else {
            account = accountOpt.get();
            if (!validCash(account)) blockers.add("INVALID_PAPER_CASH_OR_REALIZED_PNL");
        }

        var guardOpt = guards.findById(ACCOUNT);
        PaperDrawdownGuardEntity guard = null;
        if (guardOpt.isEmpty()) {
            blockers.add("PAPER_DRAWDOWN_GUARD_MISSING");
        } else {
            guard = guardOpt.get();
            if (guard.invalidPersistedState()) {
                blockers.add("CORRUPT_PAPER_DRAWDOWN_GUARD");
            }
            if (guard.isHalted()) blockers.add("PAPER_DRAWDOWN_CIRCUIT_LATCHED");
            if (guard.getLastMarketMarkAt() == null) {
                blockers.add("PAPER_MARK_NOT_CERTIFIED");
            }
        }

        List<PaperPositionEntity> open = positions.findByStatus(PaperPositionStatus.OPEN);
        double gross = 0;
        boolean validPositions = open.size() <= 16;
        for (PaperPositionEntity position : open) {
            if (!validOpenPosition(position)) {
                validPositions = false;
                break;
            }
            gross += position.getNotional();
            if (!Double.isFinite(gross)) {
                validPositions = false;
                break;
            }
        }
        if (!validPositions) blockers.add("OPEN_POSITION_INTEGRITY_UNKNOWN");

        var marks = equity.findTop200ByOrderByObservedAtDesc();
        PaperEquitySnapshotEntity last = marks.isEmpty() ? null : marks.get(0);
        boolean fresh = last != null && freshAt(last.getObservedAt(), assessmentAt,
                MAX_MARK_AGE_MS);
        if (!fresh) blockers.add("LATEST_PAPER_MARK_STALE_OR_MISSING");

        if (last != null) {
            if (!Double.isFinite(last.getCashBalance()) ||
                    !Double.isFinite(last.getEquity()) || last.getEquity() <= 0 ||
                    !Double.isFinite(last.getUnrealizedPnl()) ||
                    !Double.isFinite(last.getGrossExposure()) ||
                    last.getGrossExposure() < 0 ||
                    last.getOpenPositions() != open.size()) {
                blockers.add("PAPER_EQUITY_MARK_INCONSISTENT");
            } else {
                if (account != null && validCash(account) &&
                        !near(last.getCashBalance(), account.getCashBalance())) {
                    blockers.add("MARK_CASH_BALANCE_DISAGREES_WITH_ACCOUNT");
                }
                if (validPositions &&
                        !near(last.getGrossExposure(), gross)) {
                    blockers.add("MARK_GROSS_EXPOSURE_DISAGREES_WITH_POSITIONS");
                }
                if (!near(last.getEquity(),
                        last.getCashBalance() + last.getUnrealizedPnl())) {
                    blockers.add("MARK_EQUITY_ARITHMETIC_DISAGREES");
                }
            }
        }

        if (last != null && guard != null && guard.getLastMarketMarkAt() != null) {
            if (!freshAt(guard.getLastMarketMarkAt(), assessmentAt, MAX_MARK_AGE_MS)
                    || !closeInTime(guard.getLastMarketMarkAt(),
                            last.getObservedAt(), MAX_MARK_GUARD_SKEW_MS)
                    || !near(guard.getLastEquity(), last.getEquity())) {
                blockers.add("DRAWDOWN_GUARD_MARK_DISAGREES");
            }
        }

        // Intentionally NEVER return owner/release approval. A clean fixture
        // can at most be queued for independent human inspection; because the
        // public fixture has no trusted signer, that can never certify recovery.
        boolean structurallyConsistent = blockers.stream()
                .noneMatch(x -> !x.equals("INDEPENDENT_CHECKPOINT_CUSTODY_UNVERIFIED"));
        return new Review(
                structurallyConsistent
                        ? "SYNTHETIC_EVIDENCE_CONSISTENT_REQUIRES_OWNER_REVIEW"
                        : "SYNTHETIC_RECOVERY_BLOCKED",
                structurallyConsistent,
                blockers, sequence, pinned.pinnedSequence(), open.size(), fresh,
                false, false, false, false, false, false, "NO_GO");
    }

    private static Review denied(List<String> reasons, long seq, long pinned,
                                 int open, boolean markFresh) {
        return new Review("SYNTHETIC_RECOVERY_BLOCKED",
                false, reasons, seq, pinned, open, markFresh,
                false, false, false, false, false, false, "NO_GO");
    }

    private static boolean validCash(PaperAccountEntity account) {
        return ACCOUNT.equals(account.getAccountKey()) &&
                Double.isFinite(account.getStartingBalance()) &&
                account.getStartingBalance() > 0 &&
                Double.isFinite(account.getCashBalance()) &&
                account.getCashBalance() > 0 &&
                Double.isFinite(account.getDailyRealizedPnl()) &&
                account.getUpdatedAt() != null;
    }

    private static boolean validOpenPosition(PaperPositionEntity position) {
        if (position == null || position.getStatus() != PaperPositionStatus.OPEN ||
                position.getSymbol() == null || position.getSymbol().isBlank() ||
                position.getId() == null || position.getSignalId() == null ||
                position.getSide() == null ||
                position.getSide() == TradeSide.NO_TRADE ||
                !Double.isFinite(position.getEntryPrice()) ||
                position.getEntryPrice() <= 0 ||
                !Double.isFinite(position.getQuantity()) ||
                position.getQuantity() <= 0 ||
                !Double.isFinite(position.getNotional()) ||
                position.getNotional() <= 0 ||
                !Double.isFinite(position.getStopLoss()) ||
                position.getStopLoss() <= 0 ||
                position.getOpenedAt() == null) return false;
        if (position.getSide() == TradeSide.LONG &&
                position.getStopLoss() >= position.getEntryPrice()) return false;
        if (position.getSide() == TradeSide.SHORT &&
                position.getStopLoss() <= position.getEntryPrice()) return false;
        return near(position.getEntryPrice() * position.getQuantity(),
                position.getNotional());
    }

    private static boolean near(double left, double right) {
        return Double.isFinite(left) && Double.isFinite(right) &&
                Math.abs(left - right) <= 1e-7 *
                        Math.max(1, Math.max(Math.abs(left), Math.abs(right)));
    }

    private static boolean freshAt(Instant observed, Instant assessed, long maximumAge) {
        if (observed == null) return false;
        long difference = assessed.toEpochMilli() - observed.toEpochMilli();
        return difference >= 0 && difference <= maximumAge;
    }

    private static boolean closeInTime(Instant a, Instant b, long maximumSkew) {
        if (a == null || b == null) return false;
        long delta = a.toEpochMilli() - b.toEpochMilli();
        return delta >= -maximumSkew && delta <= maximumSkew;
    }
}
