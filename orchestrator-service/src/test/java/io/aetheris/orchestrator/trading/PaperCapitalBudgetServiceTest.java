package io.aetheris.orchestrator.trading;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Stage 46.27 deterministic PAPER ONLY capital management contract.
 *
 * No exchange client, network connection, testnet account, DB, secrets,
 * models, market forecasting or real user funds are involved.
 */
class PaperCapitalBudgetServiceTest {
    private PaperAccountEntity account() {
        return new PaperAccountEntity("unit-paper-account", 10_000);
    }

    private TradingRiskPolicyEntity policy() {
        return new TradingRiskPolicyEntity("unit-risk-policy");
    }

    private PaperRiskPolicyEntity portfolioPolicy() {
        var p = new PaperRiskPolicyEntity("unit-paper-policy");
        p.update(new PaperRiskPolicyRequest(3, 3, 100, 100, 120));
        return p;
    }

    private TradeSignalEntity signal(TradeSide side, double entry, double stop, double notional) {
        var s = new TradeSignalEntity(UUID.randomUUID(), "BTCUSDT", side,
                entry, stop, entry * 1.02, entry * 1.04, entry * 1.06, 1, .5,
                2.0, 80, 85, false, notional, "Synthetic unit test only",
                TradeSignalStatus.PENDING_ACCEPTANCE);
        s.accept();
        return s;
    }

    private PaperCapitalBudgetService.Quote check(PaperAccountEntity a, TradeSignalEntity s,
                                                   double fill, List<PaperPositionEntity> open) {
        return PaperCapitalBudgetService.assess(a, s, fill, policy(), portfolioPolicy(), open);
    }

    private PaperPositionEntity openPosition(String symbol, double entry, double stop, double qty) {
        var s = signal(TradeSide.LONG, entry, stop, entry * qty);
        var replacement = new TradeSignalEntity(s.getId(), symbol, TradeSide.LONG,
                entry, stop, entry * 1.02, entry * 1.04, entry * 1.06, 1, .5, 2,
                80, 85, false, entry * qty, "Inert existing exposure",
                TradeSignalStatus.ACCEPTED_ANALYSIS);
        return new PaperPositionEntity(UUID.randomUUID(), replacement, entry, qty);
    }

    private void neverLive(PaperCapitalBudgetService.Quote quote) {
        assertThat(quote.executionMode()).isEqualTo("PAPER_ONLY");
        assertThat(quote.exchangeOrderAuthorized()).isFalse();
        assertThat(quote.liveMoneyAuthorized()).isFalse();
    }

    @Test
    void stopBasedPositionSizingAutomaticallyReducesOversizedSignal() {
        var r = check(account(), signal(TradeSide.LONG, 100, 99, 10_000), 100, List.of());
        assertThat(r.allowed()).isTrue();
        assertThat(r.approvedNotional()).isEqualTo(5000);
        assertThat(r.estimatedLossAtStop()).isEqualTo(50);
        assertThat(r.perTradeRiskBudget()).isEqualTo(50);
        assertThat(r.remainingDailyLossBudget()).isEqualTo(200);
        neverLive(r);
    }

    @Test
    void perSymbolLimitCanFurtherReduceNotionalWithoutOverridingRisk() {
        var p = new PaperRiskPolicyEntity("conservative");
        var r = PaperCapitalBudgetService.assess(
                account(), signal(TradeSide.LONG, 100, 99, 10_000), 100,
                policy(), p, List.of());
        assertThat(r.allowed()).isTrue();
        assertThat(r.approvedNotional()).isEqualTo(4000);
        assertThat(r.estimatedLossAtStop()).isEqualTo(40);
    }

    @Test
    void dailyLossRemainingIncludesRealizedLossAndResizesPaperExposure() {
        var a = account();
        a.realize(-195);
        var r = check(a, signal(TradeSide.LONG, 100, 99, 10_000), 100, List.of());
        assertThat(r.allowed()).isTrue();
        assertThat(r.remainingDailyLossBudget()).isEqualTo(5);
        assertThat(r.approvedNotional()).isEqualTo(500);
        assertThat(r.estimatedLossAtStop()).isEqualTo(5);
        neverLive(r);
    }

    @Test
    void dailyRealizedLossAtMaximumBlocksAllNewExposure() {
        var a = account();
        a.realize(-200);
        var r = check(a, signal(TradeSide.LONG, 100, 99, 10_000), 100, List.of());
        assertThat(r.allowed()).isFalse();
        assertThat(r.denialReasons()).anyMatch(s -> s.contains("Daily loss budget exhausted"));
        assertThat(r.approvedNotional()).isZero();
        neverLive(r);
    }

    @Test
    void openPositionsUseConservativeStopExposureAgainstDailyLimit() {
        var a = account();
        a.realize(-185);
        var existing = openPosition("ETHUSD", 100, 99, 10);
        var target = new TradeSignalEntity(UUID.randomUUID(), "ETHUSDT", TradeSide.LONG,
                100, 99, 102, 104, 106, 1, .5, 2, 80, 85, false,
                10_000, "Synthetic", TradeSignalStatus.ACCEPTED_ANALYSIS);
        var r = check(a, target, 100, List.of(existing));
        assertThat(r.allowed()).isTrue();
        assertThat(r.existingOpenStopExposure()).isEqualTo(10);
        assertThat(r.remainingDailyLossBudget()).isEqualTo(5);
        assertThat(r.approvedNotional()).isEqualTo(500);
        assertThat(r.projectedCorrelatedExposure()).isEqualTo(1500);
    }

    @Test
    void aggregateDailyStopExposureExhaustionBlocksNewPaperPositions() {
        var a = account();
        a.realize(-195);
        var r = check(a, signal(TradeSide.LONG, 100, 99, 10_000),
                100, List.of(openPosition("ETHUSD", 100, 99, 6)));
        assertThat(r.allowed()).isFalse();
        assertThat(r.approvedNotional()).isZero();
    }

    @Test
    void invalidLongStopSideAlwaysBlocks() {
        var r = check(account(), signal(TradeSide.LONG, 100, 101, 3000), 100, List.of());
        assertThat(r.allowed()).isFalse();
        assertThat(r.denialReasons()).anyMatch(x -> x.contains("loss side"));
    }

    @Test
    void invalidShortStopSideAlwaysBlocks() {
        var r = check(account(), signal(TradeSide.SHORT, 100, 99, 3000), 100, List.of());
        assertThat(r.allowed()).isFalse();
        assertThat(r.denialReasons()).anyMatch(x -> x.contains("loss side"));
    }

    @Test
    void validShortStopIsSizedFromActualSimulatedFill() {
        var r = check(account(), signal(TradeSide.SHORT, 100, 101, 10_000),
                100, List.of());
        assertThat(r.allowed()).isTrue();
        assertThat(r.approvedNotional()).isEqualTo(5000);
        neverLive(r);
    }

    @Test
    void nonFiniteFillsAreDenied() {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, 0, -50}) {
            var r = check(account(), signal(TradeSide.LONG, 100, 99, 10_000),
                    invalid, List.of());
            assertThat(r.allowed()).isFalse();
            assertThat(r.approvedNotional()).isZero();
        }
    }

    @Test
    void nonFiniteOrZeroRecommendedNotionalsCannotCreatePaperPositions() {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, 0, -1}) {
            var r = check(account(), signal(TradeSide.LONG, 100, 99, invalid),
                    100, List.of());
            assertThat(r.allowed()).isFalse();
        }
    }

    @Test
    void invalidCashEvidenceFailsClosedWithoutNaNOutput() {
        var account = account();
        account.realize(Double.NaN);
        var r = check(account, signal(TradeSide.LONG, 100, 99, 3000), 100, List.of());
        assertThat(r.allowed()).isFalse();
        assertThat(r.approvedNotional()).isZero();
        neverLive(r);
    }

    @Test
    void unacceptedAnalysisIsNotAnExecutablePaperSignal() {
        var s = new TradeSignalEntity(UUID.randomUUID(), "BTCUSDT", TradeSide.LONG,
                100, 99, 102, 104, 106, 1, .5, 2, 80, 85, false, 1000,
                "Not owner accepted", TradeSignalStatus.PENDING_ACCEPTANCE);
        var r = check(account(), s, 100, List.of());
        assertThat(r.allowed()).isFalse();
    }

    @Test
    void noTradeSideBlocksRegardlessOfScore() {
        var r = check(account(), signal(TradeSide.NO_TRADE, 100, 99, 2000),
                100, List.of());
        assertThat(r.allowed()).isFalse();
        assertThat(r.approvedNotional()).isZero();
    }

    @Test
    void extremeEntryToFillDriftBlocksStaleSignals() {
        var r = check(account(), signal(TradeSide.LONG, 100, 99, 1000),
                110, List.of());
        assertThat(r.allowed()).isFalse();
        assertThat(r.denialReasons()).anyMatch(x -> x.contains("deviates"));
    }

    @Test
    void unrealisticStopDistanceBlocksInsteadOfTinyPosition() {
        var r = check(account(), signal(TradeSide.LONG, 100, 70, 1000),
                100, List.of());
        assertThat(r.allowed()).isFalse();
    }

    @Test
    void maximumOpenPositionCountIsEnforced() {
        var open = List.of(
                openPosition("AAAUSD", 100, 99, 1),
                openPosition("BBBUSD", 100, 99, 1),
                openPosition("CCCUSD", 100, 99, 1));
        var r = check(account(), signal(TradeSide.LONG, 100, 99, 1000),
                100, open);
        assertThat(r.allowed()).isFalse();
        assertThat(r.denialReasons()).anyMatch(x -> x.contains("Maximum open positions"));
    }

    @Test
    void invalidExistingPortfolioInputMustNotBeIgnored() {
        var r = check(account(), signal(TradeSide.LONG, 100, 99, 1000),
                100, List.of(openPosition("ETHUSD", 100, 99, Double.POSITIVE_INFINITY)));
        assertThat(r.allowed()).isFalse();
        assertThat(r.denialReasons()).anyMatch(x -> x.contains("Unverifiable"));
    }

    @Test
    void pastPaperProfitsNeverIncreaseTheDailyLossAllowance() {
        var a = account();
        a.realize(5000);
        var r = check(a, signal(TradeSide.LONG, 100, 99, 1000), 100, List.of());
        assertThat(r.allowed()).isTrue();
        assertThat(r.remainingDailyLossBudget()).isEqualTo(200);
    }

    @Test
    void paperRiskIsNotPermissionToMakeExchangeOrdersEvenWhenAllowed() {
        var r = check(account(), signal(TradeSide.LONG, 100, 99, 1000), 100, List.of());
        assertThat(r.allowed()).isTrue();
        neverLive(r);
    }

    @Test
    void noCredentialsNoModelNoNetworkNeededForRiskSizing() {
        var r = check(account(), signal(TradeSide.LONG, 100, 99, 1000), 100, List.of());
        assertThat(r.allowed()).isTrue();
        assertThat(r.approvedNotional()).isLessThanOrEqualTo(1000);
        neverLive(r);
    }
}
