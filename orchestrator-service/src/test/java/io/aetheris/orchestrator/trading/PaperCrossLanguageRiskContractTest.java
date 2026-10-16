package io.aetheris.orchestrator.trading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Stage 46.29 — actual Java PAPER capital guard and drawdown math against
 * the same reviewed synthetic fixture JSON used by Python Quant unit tests.
 *
 * Reading a fixture is not sharing an account state, enabling testnet
 * execution, binding a real exchange or certifying strategy profitability.
 */
class PaperCrossLanguageRiskContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode contract() throws IOException {
        Path file = Path.of("..", "risk-contracts", "stage46-paper-parity-v1.json");
        if (!Files.isRegularFile(file)) {
            file = Path.of("risk-contracts", "stage46-paper-parity-v1.json");
        }
        assertThat(file).as("Canonical single shared risk contract must be available")
                .isRegularFile();
        return mapper.readTree(Files.readString(file));
    }

    @Test
    void riskDefaultsAndNoReleaseAuthorizationMatchSharedContract() throws IOException {
        JsonNode root = contract();
        JsonNode p = root.path("policy");
        assertThat(root.path("schema_version").asInt()).isEqualTo(1);
        assertThat(root.path("scope").asText())
                .isEqualTo("CROSS_LANGUAGE_PAPER_PARITY_TEST_ONLY");
        assertThat(root.path("decision").asText()).isEqualTo("NO_GO");
        assertThat(root.path("release_authorization").properties())
                .isNotEmpty()
                .allSatisfy(entry -> assertThat(entry.getValue().booleanValue()).isFalse());

        var risk = new TradingRiskPolicyEntity("synthetic-contract");
        var paper = new PaperRiskPolicyEntity("synthetic-contract");
        assertThat(risk.getRiskPerTradePct() / 100d)
                .isCloseTo(p.path("risk_per_trade_fraction").asDouble(), within(1e-12));
        assertThat(risk.getMaxDailyLossPct() / 100d)
                .isCloseTo(p.path("max_daily_loss_fraction").asDouble(), within(1e-12));
        assertThat(risk.getMaxLeverage()).isEqualTo(p.path("max_leverage").asInt());
        assertThat(risk.getMinRewardRisk())
                .isCloseTo(p.path("min_reward_risk").asDouble(), within(1e-12));
        assertThat(paper.getMaxOpenPositions())
                .isEqualTo(p.path("max_open_positions").asInt());
        assertThat(paper.getMaxGrossExposureMultiple())
                .isCloseTo(p.path("max_gross_exposure_multiple").asDouble(), within(1e-12));
        assertThat(paper.getMaxSymbolExposurePct() / 100d)
                .isCloseTo(p.path("max_symbol_exposure_fraction").asDouble(), within(1e-12));
        assertThat(paper.getMaxCorrelatedExposurePct() / 100d)
                .isCloseTo(p.path("max_correlated_fraction_of_gross").asDouble(), within(1e-12));
        assertThat(PaperDrawdownPolicy.MAX_DRAWDOWN_PCT / 100d)
                .isCloseTo(p.path("max_peak_to_trough_drawdown_fraction").asDouble(), within(1e-12));
    }

    @Test
    void actualJavaPaperCapitalBudgetMatchesEverySharedSyntheticCase() throws IOException {
        JsonNode root = contract();
        int cases = 0;
        for (JsonNode v : root.path("budget_cases")) {
            cases++;
            JsonNode in = v.path("input");
            JsonNode expected = v.path("expected");
            PaperAccountEntity account = new PaperAccountEntity(
                    "cross-language-fixture", in.path("starting_balance").asDouble());
            double pnl = in.path("cash_balance").asDouble()
                    - in.path("starting_balance").asDouble();
            account.realize(pnl);
            assertThat(account.getDailyRealizedPnl())
                    .as(v.path("id").asText())
                    .isCloseTo(in.path("daily_realized_pnl").asDouble(), within(1e-8));

            TradeSide side = TradeSide.valueOf(in.path("side").asText());
            var candidate = signal(
                    in.path("symbol").asText(), side, in.path("entry_price").asDouble(),
                    in.path("stop_loss").asDouble(), in.path("recommended_notional").asDouble(),
                    in.path("leverage").asInt(), in.path("signal_risk_pct").asDouble(),
                    in.path("reward_risk").asDouble());
            if (in.path("accepted").asBoolean()) candidate.accept();

            List<PaperPositionEntity> positions = new ArrayList<>();
            for (JsonNode node : in.path("open_positions")) {
                double entry = node.path("entry_price").asDouble();
                double qty = node.path("quantity").asDouble();
                var positionSignal = signal(node.path("symbol").asText(),
                        TradeSide.valueOf(node.path("side").asText()),
                        entry, node.path("stop_loss").asDouble(),
                        node.path("notional").asDouble(), 1, .5, 2);
                positionSignal.accept();
                positions.add(new PaperPositionEntity(
                        UUID.randomUUID(), positionSignal, entry, qty));
            }
            var result = PaperCapitalBudgetService.assess(
                    account, candidate, in.path("fill_price").asDouble(),
                    new TradingRiskPolicyEntity("fixture"),
                    new PaperRiskPolicyEntity("fixture"), positions);
            assertThat(result.allowed()).as(v.path("id").asText())
                    .isEqualTo(expected.path("allowed").asBoolean());
            for (String field : List.of("approved_notional", "estimated_loss_at_stop",
                    "remaining_daily_loss_budget", "projected_gross_exposure",
                    "projected_correlated_exposure")) {
                if (expected.has(field)) {
                    double actual = switch (field) {
                        case "approved_notional" -> result.approvedNotional();
                        case "estimated_loss_at_stop" -> result.estimatedLossAtStop();
                        case "remaining_daily_loss_budget" -> result.remainingDailyLossBudget();
                        case "projected_gross_exposure" -> result.projectedGrossExposure();
                        default -> result.projectedCorrelatedExposure();
                    };
                    assertThat(actual).as(v.path("id").asText() + " " + field)
                            .isCloseTo(expected.path(field).asDouble(), within(1e-7));
                }
            }
            assertThat(result.executionMode()).isEqualTo("PAPER_ONLY");
            assertThat(result.exchangeOrderAuthorized()).isFalse();
            assertThat(result.liveMoneyAuthorized()).isFalse();
        }
        assertThat(cases).isGreaterThanOrEqualTo(5);
    }

    @Test
    void actualJavaPeakDrawdownRulesMatchSharedPythonReferenceCases() throws IOException {
        int count = 0;
        for (JsonNode c : contract().path("drawdown_cases")) {
            count++;
            var result = PaperDrawdownPolicy.review(
                    c.path("previous_peak").asDouble(),
                    c.path("previous_equity").asDouble(),
                    c.path("previous_max_drawdown_pct").asDouble(),
                    c.path("previous_halted").asBoolean(),
                    "REFERENCE_PREVIOUS_PAPER_CIRCUIT",
                    c.path("observed_equity").asDouble());
            assertThat(result.halted()).as(c.path("id").asText())
                    .isEqualTo(c.path("expected").path("halted").asBoolean());
            assertThat(result.peakEquity())
                    .isCloseTo(c.path("expected").path("peak_equity").asDouble(), within(1e-7));
            assertThat(result.maxObservedDrawdownPct())
                    .isCloseTo(c.path("expected").path("max_drawdown_pct").asDouble(), within(1e-7));
            assertThat(result.liveBrokerAuthorized()).isFalse();
        }
        assertThat(count).isGreaterThanOrEqualTo(3);
    }

    private static TradeSignalEntity signal(String symbol, TradeSide side, double entry,
                                             double stop, double notional, int leverage,
                                             double risk, double rr) {
        return new TradeSignalEntity(
                UUID.randomUUID(), symbol, side, entry, stop,
                entry * 1.02, entry * 1.04, entry * 1.06,
                leverage, risk, rr, 80, 85, false, notional,
                "Fixture generated without market or brokerage calls",
                TradeSignalStatus.PENDING_ACCEPTANCE);
    }
}
