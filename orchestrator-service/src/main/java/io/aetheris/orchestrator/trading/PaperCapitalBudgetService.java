package io.aetheris.orchestrator.trading;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Stage 46.27 — deterministic PAPER-ONLY money-management gate.
 *
 * No broker adapter, model input, order placement, persistence, live API or
 * owner authorization exists here. A price reaching a stop is NOT guaranteed
 * to execute there (gaps, volatility, fees, slippage, liquidation). Quoted
 * stop exposure is a scenario, not a maximum possible loss or profit promise.
 * No external caller may use this as a real-money authorization.
 */
public final class PaperCapitalBudgetService {
    private static final double EPS = 1e-8;
    private static final double MAX_STOP_FRACTION = .15;

    private PaperCapitalBudgetService() {}

    public record Quote(
            boolean allowed,
            List<String> denialReasons,
            double approvedNotional,
            double estimatedLossAtStop,
            double perTradeRiskBudget,
            double remainingDailyLossBudget,
            double existingOpenStopExposure,
            double projectedGrossExposure,
            double projectedCorrelatedExposure,
            String executionMode,
            boolean exchangeOrderAuthorized,
            boolean liveMoneyAuthorized) {
        public Quote {
            denialReasons = List.copyOf(denialReasons);
        }
    }

    public static Quote assess(
            PaperAccountEntity account,
            TradeSignalEntity signal,
            double fillPrice,
            TradingRiskPolicyEntity riskPolicy,
            PaperRiskPolicyEntity paperPolicy,
            List<PaperPositionEntity> openPositions) {
        List<String> reasons = new ArrayList<>();
        if (account == null || signal == null || riskPolicy == null ||
                paperPolicy == null || openPositions == null) {
            return denied("Incomplete paper-only risk evidence");
        }

        double cash = account.getCashBalance();
        double startingBalance = account.getStartingBalance();
        double realized = account.getDailyRealizedPnl();
        double stop = signal.getStopLoss();
        double recommended = signal.getRecommendedNotional();
        double signalEntry = signal.getEntryPrice();
        double maxRiskPct = riskPolicy.getRiskPerTradePct();
        double maxDailyPct = riskPolicy.getMaxDailyLossPct();

        if (!positive(cash) || !positive(startingBalance) || !finite(realized) ||
                !positive(fillPrice) || !positive(signalEntry) ||
                !positive(stop) || !positive(recommended) ||
                !positive(maxRiskPct) || !positive(maxDailyPct)) {
            return denied("Non-finite or non-positive account, price, risk or notional");
        }
        if (signal.getSide() != TradeSide.LONG && signal.getSide() != TradeSide.SHORT) {
            reasons.add("NO_TRADE cannot open a paper position");
        } else if (signal.getSide() == TradeSide.LONG && stop >= fillPrice ||
                signal.getSide() == TradeSide.SHORT && stop <= fillPrice) {
            reasons.add("Stop must be on the loss side of the simulated fill");
        }
        if (signal.getStatus() != TradeSignalStatus.ACCEPTED_ANALYSIS) {
            reasons.add("Signal analysis has not been explicitly accepted");
        }
        if (signal.getLeverage() < 1 || signal.getLeverage() > riskPolicy.getMaxLeverage()) {
            reasons.add("Leverage exceeds deterministic risk policy");
        }
        if (!finite(signal.getRewardRisk()) ||
                signal.getRewardRisk() < riskPolicy.getMinRewardRisk()) {
            reasons.add("Reward:risk is below policy minimum");
        }
        if (!positive(signal.getRiskPerTradePct())) {
            reasons.add("Signal risk percentage must be positive");
        }
        if (Math.abs(fillPrice - signalEntry) / signalEntry > .05) {
            reasons.add("Simulated fill deviates more than 5% from analyzed entry");
        }
        double stopFraction = Math.abs(fillPrice - stop) / fillPrice;
        if (!positive(stopFraction) || stopFraction > MAX_STOP_FRACTION) {
            reasons.add("Invalid or excessively distant protective stop");
        }

        if (paperPolicy.getMaxOpenPositions() < 1 ||
                !positive(paperPolicy.getMaxGrossExposureMultiple()) ||
                !positive(paperPolicy.getMaxSymbolExposurePct()) ||
                !positive(paperPolicy.getMaxCorrelatedExposurePct())) {
            reasons.add("Invalid portfolio exposure limits");
        }
        if (openPositions.size() >= paperPolicy.getMaxOpenPositions()) {
            reasons.add("Maximum open positions reached");
        }

        double gross = 0, correlated = 0, sameSymbol = 0, existingStopRisk = 0;
        String candidateGroup = group(signal.getSymbol());
        for (PaperPositionEntity position : openPositions) {
            if (position == null || position.getStatus() != PaperPositionStatus.OPEN ||
                    !positive(position.getEntryPrice()) ||
                    !positive(position.getQuantity()) || !positive(position.getNotional()) ||
                    !positive(position.getStopLoss()) ||
                    position.getSide() == null || position.getSide() == TradeSide.NO_TRADE ||
                    position.getSymbol() == null) {
                return denied("Unverifiable open paper position; no new risk permitted");
            }
            double exposure = position.getNotional();
            double stopLoss = position.getSide() == TradeSide.LONG
                    ? Math.max(0, position.getEntryPrice() - position.getStopLoss()) * position.getQuantity()
                    : Math.max(0, position.getStopLoss() - position.getEntryPrice()) * position.getQuantity();
            if (!finite(stopLoss)) {
                return denied("Unverifiable open-position stop exposure");
            }
            existingStopRisk += stopLoss;
            gross += exposure;
            if (position.getSymbol().equalsIgnoreCase(signal.getSymbol())) {
                sameSymbol += exposure;
            }
            if (group(position.getSymbol()).equals(candidateGroup)) {
                correlated += exposure;
            }
        }
        if (!finite(gross) || !finite(correlated) || !finite(sameSymbol) ||
                !finite(existingStopRisk)) {
            return denied("Non-finite aggregate portfolio exposure");
        }

        // The daily budget does NOT increase after a profitable trade. Using
        // only negative realized P&L is deliberately more conservative.
        double dailyLossCap = startingBalance * maxDailyPct / 100d;
        double realizedLoss = Math.max(0, -realized);
        double remainingDaily = dailyLossCap - realizedLoss - existingStopRisk;
        if (!finite(dailyLossCap) || !finite(remainingDaily) || remainingDaily <= EPS) {
            reasons.add("Daily loss budget exhausted by realized and open stop risk");
        }

        double signalPct = signal.getRiskPerTradePct();
        double perTradeBudget = cash * Math.min(maxRiskPct, signalPct) / 100d;
        double grossCapacity = cash * paperPolicy.getMaxGrossExposureMultiple() - gross;
        double symbolCapacity = cash * paperPolicy.getMaxSymbolExposurePct() / 100d - sameSymbol;
        double correlatedCapacity =
                cash * paperPolicy.getMaxGrossExposureMultiple() *
                        paperPolicy.getMaxCorrelatedExposurePct() / 100d - correlated;
        if (!finite(perTradeBudget) || !finite(grossCapacity) ||
                !finite(symbolCapacity) || !finite(correlatedCapacity) ||
                perTradeBudget <= EPS) {
            reasons.add("Invalid or exhausted cash risk and exposure budgets");
        }
        if (grossCapacity <= EPS) reasons.add("Gross exposure limit reached");
        if (symbolCapacity <= EPS) reasons.add("Per-symbol exposure limit reached");
        if (correlatedCapacity <= EPS) reasons.add("Correlated exposure limit reached");

        double cappedBudget = Math.max(0, Math.min(perTradeBudget, remainingDaily));
        double cappedNotional = Math.min(
                Math.min(recommended, cash * signal.getLeverage()),
                Math.min(cappedBudget / Math.max(stopFraction, EPS),
                        Math.min(grossCapacity, Math.min(symbolCapacity, correlatedCapacity))));
        if (!finite(cappedNotional) || cappedNotional <= EPS) {
            reasons.add("No positive safely sized paper position remains");
        }

        if (!reasons.isEmpty()) {
            return new Quote(false, reasons, 0, 0, Math.max(0, perTradeBudget),
                    Math.max(0, remainingDaily), existingStopRisk, gross, correlated,
                    "PAPER_ONLY", false, false);
        }

        // For stop-at-price scenarios only. Real execution can lose more than
        // this estimate. No automatic submission to any exchange is possible.
        double stopRisk = cappedNotional * stopFraction;
        return new Quote(true, List.of(), cappedNotional, stopRisk,
                perTradeBudget, Math.max(0, remainingDaily), existingStopRisk,
                gross + cappedNotional, correlated + cappedNotional,
                "PAPER_ONLY", false, false);
    }

    private static Quote denied(String reason) {
        return new Quote(false, List.of(reason), 0, 0, 0, 0, 0, 0, 0,
                "PAPER_ONLY", false, false);
    }

    private static String group(String symbol) {
        if (symbol == null || symbol.isBlank()) return "";
        String key = symbol.toUpperCase(Locale.ROOT);
        for (String quote : List.of("USDT", "USDC", "USD", "EUR", "BTC")) {
            if (key.endsWith(quote) && key.length() > quote.length()) {
                return key.substring(0, key.length() - quote.length());
            }
        }
        return key;
    }

    private static boolean positive(double value) {
        return Double.isFinite(value) && value > 0;
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }
}
