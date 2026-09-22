package io.aetheris.orchestrator.trading;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class Stage10PaperOrderSimulatorService {
    private final MultiSourceMarketDataBook market;
    private final TradingIntelligenceService intelligence;
    private final PaperTradingService paper;
    private final PaperRiskOfficerService risk;

    public Stage10PaperOrderSimulatorService(MultiSourceMarketDataBook market, TradingIntelligenceService intelligence,
                                             PaperTradingService paper, PaperRiskOfficerService risk) {
        this.market = market;
        this.intelligence = intelligence;
        this.paper = paper;
        this.risk = risk;
    }

    public Stage10PaperOrderSimulation simulate(Stage10PaperOrderRequest request) {
        validate(request);
        TradeSignalEntity signal = intelligence.required(request.signalId());
        String symbol = request.symbol().trim().toUpperCase(Locale.ROOT);
        if (!signal.getSymbol().equalsIgnoreCase(symbol)) throw new IllegalArgumentException("Order symbol does not match the trade signal");
        MarketConsensus consensus = market.consensus(symbol, request.minSources(), request.maxAgeSeconds(),
                request.maxClockSkewSeconds(), request.maxDivergencePct());
        double reference = consensus.medianPrice();
        String orderType = request.orderType().trim().toUpperCase(Locale.ROOT);
        TriggerDecision trigger = trigger(signal.getSide(), orderType, reference, request.limitPrice(), request.stopPrice());
        if (!trigger.eligible()) {
            return result("PENDING_NO_FILL", signal, consensus, orderType, 0, 0, 0, 0, 0,
                    trigger.detail() + "; no paper position and no exchange order were created", null);
        }

        double slippageRate = request.slippageBps() / 10_000d;
        double direction = signal.getSide() == TradeSide.LONG ? 1d : -1d;
        double simulatedFill = Math.max(0.00000001d, trigger.basePrice() * (1d + direction * slippageRate));
        PaperRiskDecision decision = risk.evaluate(paper.account(), signal, simulatedFill);
        if (!decision.allowed()) {
            return result("RISK_REJECTED", signal, consensus, orderType, simulatedFill, 0, 0, 0, 0,
                    "Deterministic Paper Risk Officer rejected the simulated order", decision);
        }

        double fillRatio = request.requestedFillRatio();
        if (fillRatio == 0d) {
            return result("PENDING_NO_FILL", signal, consensus, orderType, simulatedFill, 0, 0, 0, 0,
                    "Requested simulation fill ratio is zero; order remains unfilled", decision);
        }
        double notional = Math.min(signal.getRecommendedNotional(), paper.account().getCashBalance() * signal.getLeverage());
        double filledNotional = notional * fillRatio;
        double quantity = filledNotional / simulatedFill;
        double fees = filledNotional * request.feeBps() / 10_000d;
        double slippageCost = Math.abs(simulatedFill - trigger.basePrice()) * quantity;
        double funding = filledNotional * request.fundingBps() / 10_000d;
        double totalCost = fees + slippageCost + funding;
        String status = fillRatio < 1d ? "PARTIAL_FILL_SIMULATED" : "FULL_FILL_SIMULATED";
        return result(status, signal, consensus, orderType, simulatedFill, fillRatio, quantity, filledNotional, totalCost,
                "PAPER_SIMULATION_ONLY: richer order behavior was simulated; no durable position and no exchange order were created", decision);
    }

    private TriggerDecision trigger(TradeSide side, String type, double reference, double limit, double stop) {
        return switch (type) {
            case "MARKET" -> new TriggerDecision(true, reference, "Market simulation is immediately eligible at the consensus reference");
            case "LIMIT" -> {
                requirePrice(limit, "limitPrice");
                boolean fill = side == TradeSide.LONG ? reference <= limit : reference >= limit;
                yield new TriggerDecision(fill, fill ? Math.min(reference, limit) : reference,
                        fill ? "Limit condition reached" : "Limit price has not been reached");
            }
            case "STOP" -> {
                requirePrice(stop, "stopPrice");
                boolean fill = side == TradeSide.LONG ? reference >= stop : reference <= stop;
                yield new TriggerDecision(fill, reference, fill ? "Stop trigger reached" : "Stop trigger has not been reached");
            }
            case "STOP_LIMIT" -> {
                requirePrice(stop, "stopPrice"); requirePrice(limit, "limitPrice");
                boolean triggered = side == TradeSide.LONG ? reference >= stop : reference <= stop;
                boolean limitReached = side == TradeSide.LONG ? reference <= limit : reference >= limit;
                boolean fill = triggered && limitReached;
                yield new TriggerDecision(fill, fill ? Math.min(reference, Math.max(limit, 0.00000001d)) : reference,
                        fill ? "Stop-limit trigger and limit conditions reached" : "Stop-limit conditions are not both satisfied");
            }
            default -> throw new IllegalArgumentException("Unsupported Stage 10 paper order type: " + type);
        };
    }

    private Stage10PaperOrderSimulation result(String status, TradeSignalEntity signal, MarketConsensus consensus, String orderType,
                                                double fillPrice, double ratio, double quantity, double notional, double cost,
                                                String detail, PaperRiskDecision decision) {
        return new Stage10PaperOrderSimulation(status, "PAPER_SIMULATION_ONLY", signal.getId(), signal.getSymbol(), signal.getSide(), orderType,
                consensus.medianPrice(), fillPrice, ratio, quantity, notional, cost, consensus.sources(),
                consensus.maxPriceDivergencePct(), consensus.clockSkewSeconds(), decision, detail,
                false, false, false);
    }

    private void validate(Stage10PaperOrderRequest r) {
        if (r == null || r.signalId() == null) throw new IllegalArgumentException("signalId is required");
        if (r.symbol() == null || r.symbol().isBlank()) throw new IllegalArgumentException("symbol is required");
        if (r.orderType() == null || r.orderType().isBlank()) throw new IllegalArgumentException("orderType is required");
        if (!Double.isFinite(r.requestedFillRatio()) || r.requestedFillRatio() < 0 || r.requestedFillRatio() > 1) throw new IllegalArgumentException("requestedFillRatio must be between 0 and 1");
        if (!Double.isFinite(r.feeBps()) || r.feeBps() < 0 || r.feeBps() > 100) throw new IllegalArgumentException("feeBps must be between 0 and 100");
        if (!Double.isFinite(r.slippageBps()) || r.slippageBps() < 0 || r.slippageBps() > 200) throw new IllegalArgumentException("slippageBps must be between 0 and 200");
        if (!Double.isFinite(r.fundingBps()) || r.fundingBps() < -100 || r.fundingBps() > 100) throw new IllegalArgumentException("fundingBps must be between -100 and 100");
        if (r.minSources() < 2) throw new IllegalArgumentException("Stage 10 paper simulation requires at least two independent market sources");
        if (r.maxAgeSeconds() < 1 || r.maxClockSkewSeconds() < 1) throw new IllegalArgumentException("market freshness/skew limits must be positive");
        if (!Double.isFinite(r.maxDivergencePct()) || r.maxDivergencePct() <= 0 || r.maxDivergencePct() > 10) throw new IllegalArgumentException("maxDivergencePct must be between 0 and 10");
    }

    private void requirePrice(double value, String field) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(field + " must be positive for this order type");
    }

    private record TriggerDecision(boolean eligible, double basePrice, String detail) {}

    public record Stage10PaperOrderRequest(UUID signalId, String symbol, String orderType, double limitPrice, double stopPrice,
                                           double requestedFillRatio, double feeBps, double slippageBps, double fundingBps,
                                           int minSources, long maxAgeSeconds, long maxClockSkewSeconds, double maxDivergencePct) {}

    public record Stage10PaperOrderSimulation(String status, String mode, UUID signalId, String symbol, TradeSide side, String orderType,
                                              double consensusPrice, double simulatedFillPrice, double fillRatio, double quantity,
                                              double filledNotional, double simulatedCosts, List<String> marketSources,
                                              double maxPriceDivergencePct, long clockSkewSeconds, PaperRiskDecision riskDecision,
                                              String detail, boolean exchangeOrderSent, boolean liveMoneyAllowed,
                                              boolean withdrawalsAllowed) {}
}
