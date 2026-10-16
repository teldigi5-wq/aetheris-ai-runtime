package io.aetheris.orchestrator.trading;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Real JPA integration: verify latched circuit survives independent service
 * transactions and blocks the actual existing paper risk officer.
 * Removes only this synthetic guard row after each test. No broker/PC calls.
 */
@SpringBootTest
class PaperDrawdownGuardIntegrationTest {
    @Autowired PaperDrawdownGuardRepository guards;
    @Autowired PaperDrawdownGuardService circuit;
    @Autowired PaperRiskOfficerService paperRisk;

    private PaperAccountEntity account;

    @BeforeEach void initialize() {
        guards.deleteById("paper-default");
        account = new PaperAccountEntity("paper-default",10000);
    }

    @AfterEach void cleanup() {
        guards.deleteById("paper-default");
    }

    @Test void persistedPeakToTroughHaltSurvivesSeparateTransactions() {
        assertThat(circuit.forNewPaperExposure(account).allowed()).isTrue();
        assertThat(circuit.observeMarkedEquity(account,11000).allowed()).isTrue();
        assertThat(circuit.observeMarkedEquity(account,10000).allowed()).isFalse();
        var persisted=guards.findById("paper-default").orElseThrow();
        assertThat(persisted.isHalted()).isTrue();
        assertThat(persisted.getPeakEquity()).isEqualTo(11000);
        assertThat(persisted.getMaxObservedDrawdownPct()).isGreaterThan(8);
        assertThat(persisted.getHaltedAt()).isNotNull();
        assertThat(circuit.forNewPaperExposure(account).allowed()).isFalse();
    }

    @Test void recoveryAfterHaltNeverResetsItAutomatically() {
        circuit.observeMarkedEquity(account,9000);
        var first=guards.findById("paper-default").orElseThrow().getHaltedAt();
        circuit.observeMarkedEquity(account,16000);
        var result=circuit.forNewPaperExposure(account);
        assertThat(result.allowed()).isFalse();
        assertThat(result.paperHalted()).isTrue();
        assertThat(guards.findById("paper-default").orElseThrow().getHaltedAt())
                .isEqualTo(first);
        assertThat(result.exchangeAuthorized()).isFalse();
    }

    @Test void actualRiskOfficerBlocksAnOtherwiseAcceptedPaperSignalAfterHalt() {
        circuit.observeMarkedEquity(account,8500);
        var signal=new TradeSignalEntity(UUID.randomUUID(),"RISK"+UUID.randomUUID().toString()
                .substring(0,8).toUpperCase()+"USDT",TradeSide.LONG,
                100,99,102,104,106,1,.5,2.5,82,92,false,1000,
                "Paper synthetic only",TradeSignalStatus.PENDING_ACCEPTANCE);
        signal.accept();
        var decision=paperRisk.evaluate(account,signal,100);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.approvedNotional()).isZero();
        assertThat(decision.reasons()).anyMatch(r -> r.contains("drawdown circuit"));
    }

    @Test void malformedMarketMarkPersistsHaltAndKeepsValidCashEvidence() {
        var decision=circuit.observeMarkedEquity(account,Double.NaN);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("INVALID_PAPER_MARK_OR_CASH_EVIDENCE");
        var persisted=guards.findById("paper-default").orElseThrow();
        assertThat(persisted.isHalted()).isTrue();
        assertThat(persisted.getLastEquity()).isEqualTo(10000);
        assertThat(persisted.getLastMarketMarkAt()).isNull();
        assertThat(persisted.invalidPersistedState()).isFalse();
    }

    @Test void newlyRealizedCashLossCannotBypassMostRecentPaperMark() {
        circuit.observeMarkedEquity(account,10000);
        account.realize(-900);
        var result=circuit.forNewPaperExposure(account);
        assertThat(result.allowed()).isFalse();
        assertThat(result.paperHalted()).isTrue();
        assertThat(guards.findById("paper-default").orElseThrow().getLastEquity()).isEqualTo(9100);
    }

    @Test void minorValidDrawdownDoesNotHaltPaperAccount() {
        circuit.observeMarkedEquity(account,10300);
        circuit.observeMarkedEquity(account,10100);
        var result=circuit.forNewPaperExposure(account);
        assertThat(result.allowed()).isTrue();
        assertThat(result.maxDrawdownPct()).isLessThan(8);
        assertThat(result.exchangeAuthorized()).isFalse();
    }

    @Test void emptyOrInvalidAccountEvidenceIsDenied() {
        assertThat(circuit.forNewPaperExposure(null).allowed()).isFalse();
        var invalid=new PaperAccountEntity("paper-default",10000);
        invalid.realize(Double.NaN);
        assertThat(circuit.forNewPaperExposure(invalid).allowed()).isFalse();
    }
}
