package io.aetheris.orchestrator;

import io.aetheris.orchestrator.trading.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class Stage10TradingIntegrationTest {
    @Autowired MultiSourceMarketDataBook market;
    @Autowired TradingIntelligenceService trading;
    @Autowired PaperRiskOfficerService paperRisk;
    @Autowired Stage10PaperOrderSimulatorService simulator;
    @Autowired Stage10TestnetReadinessService testnet;

    @Test
    void richerPaperOrdersStaySimulationOnlyAndRiskGated() {
        String symbol = "S10" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT) + "USDT";
        List<MarketBar> bars = bars(90, 120);
        market.ingest(new MarketDataSnapshot(symbol, "stage10-source-a", Instant.now(), bars));
        market.ingest(new MarketDataSnapshot(symbol, "stage10-source-b", Instant.now(), scale(bars, 1.0005)));
        trading.setPolicy(new TradingRiskPolicyRequest(.5, 2, 5, 1.2));
        paperRisk.update(new PaperRiskPolicyRequest(100, 100, 100, 100, 120));
        double current = bars.getLast().close();
        TradeSignalEntity signal = trading.analyze(new TradeAnalysisRequest(symbol, current, current * .98, current * 1.06, 95, 90, 1.0, 90d, 10000));

        var rejected = simulator.simulate(request(signal.getId(), symbol, "MARKET", 0, 0, 1, 10, 8, 2));
        assertThat(rejected.status()).isEqualTo("RISK_REJECTED");
        assertThat(rejected.exchangeOrderSent()).isFalse();
        assertThat(rejected.liveMoneyAllowed()).isFalse();
        assertThat(rejected.withdrawalsAllowed()).isFalse();

        trading.accept(signal.getId());
        var filled = simulator.simulate(request(signal.getId(), symbol, "MARKET", 0, 0, .4, 10, 8, 2));
        assertThat(filled.status()).isEqualTo("PARTIAL_FILL_SIMULATED");
        assertThat(filled.mode()).isEqualTo("PAPER_SIMULATION_ONLY");
        assertThat(filled.fillRatio()).isEqualTo(.4);
        assertThat(filled.simulatedCosts()).isPositive();
        assertThat(filled.marketSources()).containsExactlyInAnyOrder("stage10-source-a", "stage10-source-b");
        assertThat(filled.detail()).contains("no durable position").contains("no exchange order");

        double reference = filled.consensusPrice();
        double unreachableLimit = signal.getSide() == TradeSide.LONG ? reference * .5 : reference * 1.5;
        var pending = simulator.simulate(request(signal.getId(), symbol, "LIMIT", unreachableLimit, 0, 1, 10, 8, 2));
        assertThat(pending.status()).isEqualTo("PENDING_NO_FILL");
        assertThat(pending.exchangeOrderSent()).isFalse();
    }

    @Test
    void testnetReadinessNeverEnablesLiveMoneyOrWithdrawals() {
        var readiness = testnet.readiness();
        assertThat(readiness.status()).isIn("CREDENTIALS_MISSING", "CREDENTIALS_PRESENT_ADAPTER_DISABLED", "READY_FOR_TESTNET_CONNECTIVITY_VALIDATION");
        assertThat(readiness.liveMoneyAllowed()).isFalse();
        assertThat(readiness.withdrawalsAllowed()).isFalse();
        assertThat(readiness.transfersAllowed()).isFalse();
        assertThat(readiness.credentialPolicy()).isEqualTo("NEW_OWNER_CREDENTIALS_VIA_VAULT_ONLY");
        assertThat(readiness.credentials()).allSatisfy(c -> assertThat(c.alias()).isNotBlank());
    }

    private Stage10PaperOrderSimulatorService.Stage10PaperOrderRequest request(UUID signalId, String symbol, String type,
            double limit, double stop, double ratio, double feeBps, double slippageBps, double fundingBps) {
        return new Stage10PaperOrderSimulatorService.Stage10PaperOrderRequest(signalId, symbol, type, limit, stop, ratio,
                feeBps, slippageBps, fundingBps, 2, 60, 15, 1.0);
    }

    private List<MarketBar> bars(int count, double start) {
        List<MarketBar> out = new ArrayList<>();
        Instant base = Instant.now().minusSeconds(count * 60L);
        double price = start;
        for (int i = 0; i < count; i++) {
            price *= 1 + (i % 9 < 6 ? .0015 : -.0007);
            double open = price * .999, close = price,
                    high = Math.max(open, close) * 1.002,
                    low = Math.min(open, close) * .998;
            out.add(new MarketBar(base.plusSeconds(i * 60L), open, high, low, close, 1000 + i));
        }
        return List.copyOf(out);
    }

    private List<MarketBar> scale(List<MarketBar> bars, double factor) {
        return bars.stream().map(b -> new MarketBar(b.time(), b.open() * factor, b.high() * factor,
                b.low() * factor, b.close() * factor, b.volume())).toList();
    }
}
