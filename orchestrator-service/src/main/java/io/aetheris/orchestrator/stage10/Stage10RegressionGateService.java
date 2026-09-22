package io.aetheris.orchestrator.stage10;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class Stage10RegressionGateService {
    public RegressionGateResult evaluate(RegressionGateRequest request) {
        if (request == null || request.pack() == null || request.pack().isBlank()) throw new IllegalArgumentException("Benchmark pack is required");
        if (!Double.isFinite(request.baselineScore()) || !Double.isFinite(request.candidateScore())) throw new IllegalArgumentException("Benchmark scores must be finite");
        if (request.baselineScore() < 0 || request.baselineScore() > 100 || request.candidateScore() < 0 || request.candidateScore() > 100) throw new IllegalArgumentException("Benchmark scores must be between 0 and 100");
        if (request.allowedRegressionPoints() < 0 || request.allowedRegressionPoints() > 20) throw new IllegalArgumentException("allowedRegressionPoints must be between 0 and 20");
        List<String> blockers = new ArrayList<>();
        if (!request.functionalTestsPassed()) blockers.add("functional/integration tests did not pass");
        if (request.candidateScore() < 65) blockers.add("candidate benchmark is below Stage 9 PASS threshold (65)");
        double regression = request.baselineScore() - request.candidateScore();
        if (regression > request.allowedRegressionPoints()) blockers.add("candidate regressed by " + round(regression) + " points, above the allowed " + round(request.allowedRegressionPoints()));
        if (request.hallucinationPenalty() > 20) blockers.add("hallucination penalty exceeds promotion policy");
        if (request.criticalSafetyFailures() > 0) blockers.add("critical safety failures are non-zero");
        String decision = blockers.isEmpty() ? "PROMOTE_ALLOWED" : "PROMOTION_BLOCKED";
        return new RegressionGateResult(decision, request.pack().trim(), request.baselineScore(), request.candidateScore(), round(regression),
                List.copyOf(blockers), "A benchmark gate never self-promotes runtime/model changes; normal owner/policy approvals still apply");
    }

    private double round(double value) { return Math.round(value * 100.0) / 100.0; }

    public record RegressionGateRequest(String pack, double baselineScore, double candidateScore, double allowedRegressionPoints,
                                        boolean functionalTestsPassed, double hallucinationPenalty, int criticalSafetyFailures) {}
    public record RegressionGateResult(String decision, String pack, double baselineScore, double candidateScore,
                                       double regressionPoints, List<String> blockers, String detail) {}
}
