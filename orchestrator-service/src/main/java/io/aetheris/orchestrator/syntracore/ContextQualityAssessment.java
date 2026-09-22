package io.aetheris.orchestrator.syntracore;

public record ContextQualityAssessment(
        int candidateCount,
        int uniqueCandidateCount,
        int selectedCount,
        int duplicateCount,
        int budgetOmittedCount,
        double averageSelectedScore,
        double minimumSelectedScore,
        double maximumSelectedScore) {

    public ContextQualityAssessment {
        if (candidateCount < 0
                || uniqueCandidateCount < 0
                || selectedCount < 0
                || duplicateCount < 0
                || budgetOmittedCount < 0) {
            throw new IllegalArgumentException("context quality counts must not be negative");
        }
        if (uniqueCandidateCount > candidateCount) {
            throw new IllegalArgumentException("uniqueCandidateCount must not exceed candidateCount");
        }
        if (selectedCount > uniqueCandidateCount) {
            throw new IllegalArgumentException("selectedCount must not exceed uniqueCandidateCount");
        }
        if (duplicateCount != candidateCount - uniqueCandidateCount) {
            throw new IllegalArgumentException("duplicateCount must equal candidateCount - uniqueCandidateCount");
        }
        if (budgetOmittedCount != uniqueCandidateCount - selectedCount) {
            throw new IllegalArgumentException("budgetOmittedCount must equal uniqueCandidateCount - selectedCount");
        }
        validateScore(averageSelectedScore, "averageSelectedScore");
        validateScore(minimumSelectedScore, "minimumSelectedScore");
        validateScore(maximumSelectedScore, "maximumSelectedScore");
        if (selectedCount == 0) {
            if (averageSelectedScore != 0d || minimumSelectedScore != 0d || maximumSelectedScore != 0d) {
                throw new IllegalArgumentException("empty selection must report zero scores");
            }
        } else if (minimumSelectedScore > maximumSelectedScore) {
            throw new IllegalArgumentException("minimumSelectedScore must not exceed maximumSelectedScore");
        }
    }

    public double selectionCoverage() {
        if (uniqueCandidateCount == 0) {
            return 1d;
        }
        return (double) selectedCount / uniqueCandidateCount;
    }

    private static void validateScore(double score, String label) {
        if (!Double.isFinite(score) || score < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}
