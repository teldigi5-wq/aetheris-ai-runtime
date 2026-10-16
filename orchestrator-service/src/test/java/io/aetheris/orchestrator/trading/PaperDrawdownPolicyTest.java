package io.aetheris.orchestrator.trading;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Stage46.28 deterministic paper-only drawdown and latch adversarial tests. */
class PaperDrawdownPolicyTest {
    private PaperDrawdownPolicy.Decision check(double peak,double last,double max,
                                               boolean halted,String reason,double equity) {
        return PaperDrawdownPolicy.review(peak,last,max,halted,reason,equity);
    }

    @Test void flatEquityIsNotHalted() {
        var r=check(10000,10000,0,false,"BASELINE",10000);
        assertThat(r.halted()).isFalse();
        assertThat(r.maxObservedDrawdownPct()).isZero();
        assertThat(r.liveBrokerAuthorized()).isFalse();
    }

    @Test void healthyEquityNewPeakIsRecorded() {
        var r=check(10000,10000,0,false,"BASELINE",11000);
        assertThat(r.halted()).isFalse();
        assertThat(r.peakEquity()).isEqualTo(11000);
        assertThat(r.observedEquity()).isEqualTo(11000);
    }

    @Test void peakToTroughIsNotMeasuredFromStartingBalanceOnly() {
        var r=check(12000,12000,0,false,"BASELINE",11000);
        assertThat(r.halted()).isTrue();
        assertThat(r.reason()).isEqualTo("MAX_PAPER_EQUITY_DRAWDOWN_EXCEEDED");
        assertThat(r.maxObservedDrawdownPct()).isGreaterThan(8);
    }

    @Test void belowThresholdStaysOpen() {
        var r=check(10000,10000,0,false,"BASELINE",9210);
        assertThat(r.halted()).isFalse();
        assertThat(r.maxObservedDrawdownPct()).isBetween(7.8,8.0);
    }

    @Test void exactEightPercentTripsCircuit() {
        var r=check(10000,10000,0,false,"BASELINE",9200);
        assertThat(r.halted()).isTrue();
        assertThat(r.maxObservedDrawdownPct()).isCloseTo(8,within(1e-9));
    }

    @Test void haltIsPersistentEvenAfterEquityFullyRecovers() {
        var r=check(10000,9200,8,true,"MAX_PAPER_EQUITY_DRAWDOWN_EXCEEDED",15000);
        assertThat(r.halted()).isTrue();
        assertThat(r.reason()).isEqualTo("MAX_PAPER_EQUITY_DRAWDOWN_EXCEEDED");
        assertThat(r.peakEquity()).isEqualTo(15000);
        assertThat(r.maxObservedDrawdownPct()).isEqualTo(8);
    }

    @Test void previouslyHaltedWithoutReasonFailsClosed() {
        var r=check(10000,9200,8,true,null,10000);
        assertThat(r.halted()).isTrue();
        assertThat(r.reason()).isEqualTo("PREVIOUS_PAPER_CIRCUIT_LATCHED");
    }

    @Test void maxHistoricalDrawdownNeverDecreasesOnRecovery() {
        var r=check(10000,9500,7.7,false,"BASELINE",9900);
        assertThat(r.halted()).isFalse();
        assertThat(r.maxObservedDrawdownPct()).isEqualTo(7.7);
    }

    @Test void zeroEquityTriggersFailClosed() {
        var r=check(10000,10000,0,false,"BASELINE",0);
        assertThat(r.halted()).isTrue();
        assertThat(r.reason()).isEqualTo("INVALID_PAPER_MARK_OR_CASH_EVIDENCE");
        assertThat(r.observedEquity()).isEqualTo(10000);
    }

    @Test void negativeEquityTriggersFailClosed() {
        assertThat(check(10000,10000,0,false,"BASELINE",-10).halted()).isTrue();
    }

    @Test void nanAndInfiniteMarksTriggerFailClosed() {
        for(double x:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}){
            var r=check(10000,10000,0,false,"BASELINE",x);
            assertThat(r.halted()).isTrue();
            assertThat(r.observedEquity()).isEqualTo(10000);
        }
    }

    @Test void malformedPriorPeakHalts() {
        for(double x:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}){
            assertThat(check(x,10000,0,false,"BASELINE",12000).halted()).isTrue();
        }
    }

    @Test void inconsistentLastEquityGreaterThanPeakHalts() {
        var r=check(10000,15000,0,false,"BASELINE",16000);
        assertThat(r.halted()).isTrue();
        assertThat(r.reason()).isEqualTo("INVALID_PERSISTED_PAPER_EQUITY_EVIDENCE");
    }

    @Test void malformedHistoricalDrawdownHalts() {
        for(double x:new double[]{-1,101,Double.NaN,Double.POSITIVE_INFINITY}){
            assertThat(check(10000,10000,x,false,"BASELINE",10000).halted()).isTrue();
        }
    }

    @Test void noPossiblePaperResultAuthorizesLiveBroker() {
        for(double x:new double[]{8500,9200,9500,10000,15000,Double.NaN}){
            assertThat(check(10000,10000,0,false,"BASELINE",x)
                    .liveBrokerAuthorized()).isFalse();
        }
    }

    @Test void entityDefaultsToNonHaltedCashBaseline() {
        var g=new PaperDrawdownGuardEntity("paper-default",10000);
        assertThat(g.isHalted()).isFalse();
        assertThat(g.getReason()).contains("NOT_MARKET_CERTIFIED");
        assertThat(g.getLastMarketMarkAt()).isNull();
        assertThat(g.invalidPersistedState()).isFalse();
    }

    @Test void entityHaltCannotResetThroughNextMark() {
        var g=new PaperDrawdownGuardEntity("paper-default",10000);
        g.observe(9000,true);
        assertThat(g.isHalted()).isTrue();
        assertThat(g.getHaltedAt()).isNotNull();
        var initialHalt=g.getHaltedAt();
        g.observe(15000,true);
        assertThat(g.isHalted()).isTrue();
        assertThat(g.getHaltedAt()).isEqualTo(initialHalt);
    }

    @Test void entityInvalidMarkLatchesAndDoesNotStoreNan() {
        var g=new PaperDrawdownGuardEntity("paper-default",10000);
        g.observe(Double.NaN,false);
        assertThat(g.isHalted()).isTrue();
        assertThat(g.getLastEquity()).isEqualTo(10000);
        assertThat(g.invalidPersistedState()).isFalse();
    }

    @Test void entityBadSeedCannotBeAccepted() {
        assertThatThrownBy(()->new PaperDrawdownGuardEntity("paper-default",Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new PaperDrawdownGuardEntity("paper-default",0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new PaperDrawdownGuardEntity("",10000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
