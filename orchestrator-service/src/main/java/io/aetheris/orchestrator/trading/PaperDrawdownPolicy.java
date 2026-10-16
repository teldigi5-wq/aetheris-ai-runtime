package io.aetheris.orchestrator.trading;

/**
 * Deterministic equity peak-to-trough policy for PAPER trades only.
 * This is NOT a trading strategy, broker kill-switch or maximum-loss guarantee.
 */
public final class PaperDrawdownPolicy {
    public static final double MAX_DRAWDOWN_PCT = 8.0;

    private PaperDrawdownPolicy() {}

    public record Decision(boolean halted, String reason, double peakEquity,
                           double observedEquity, double maxObservedDrawdownPct,
                           boolean liveBrokerAuthorized) {}

    public static Decision review(double previousPeak, double previousEquity,
                                  double previousMaxDrawdown, boolean previouslyHalted,
                                  String previousReason, double observedEquity) {
        if (!valid(previousPeak) || !valid(previousEquity)
                || !Double.isFinite(previousMaxDrawdown) || previousMaxDrawdown < 0
                || previousMaxDrawdown > 100
                || previousEquity > previousPeak + 0.0000001) {
            return new Decision(true, "INVALID_PERSISTED_PAPER_EQUITY_EVIDENCE",
                    safe(previousPeak), safe(previousEquity), safe(previousMaxDrawdown), false);
        }
        if (!valid(observedEquity)) {
            return new Decision(true, "INVALID_PAPER_MARK_OR_CASH_EVIDENCE",
                    previousPeak, previousEquity, previousMaxDrawdown, false);
        }
        double peak = Math.max(previousPeak, observedEquity);
        double drawdown = 100.0 * (peak - observedEquity) / peak;
        double maxDrawdown = Math.max(previousMaxDrawdown, drawdown);
        if (!Double.isFinite(drawdown) || !Double.isFinite(maxDrawdown)) {
            return new Decision(true, "INVALID_DRAWDOWN_ARITHMETIC",
                    previousPeak, previousEquity, previousMaxDrawdown, false);
        }
        boolean trip = drawdown >= MAX_DRAWDOWN_PCT - 1e-10;
        boolean halted = previouslyHalted || trip;
        String reason = previouslyHalted
                ? (previousReason == null || previousReason.isBlank()
                    ? "PREVIOUS_PAPER_CIRCUIT_LATCHED" : previousReason)
                : trip ? "MAX_PAPER_EQUITY_DRAWDOWN_EXCEEDED"
                : "WITHIN_PAPER_DRAWDOWN_LIMIT";
        return new Decision(halted, reason, peak, observedEquity,
                maxDrawdown, false);
    }

    private static boolean valid(double value) {
        return Double.isFinite(value) && value > 0;
    }

    private static double safe(double value) {
        return Double.isFinite(value) && value >= 0 ? value : 0;
    }
}
