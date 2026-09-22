from __future__ import annotations

from dataclasses import dataclass

from .models import Evidence, RiskLevel, TaskContext


@dataclass(frozen=True)
class ConfidenceAssessment:
    confidence: float
    requires_verification: bool
    reasons: tuple[str, ...]


class ConfidenceEngine:
    """Combines evidence quality, uncertainty and consequence into a verification decision."""

    _risk_penalty = {
        RiskLevel.LOW: 0.00,
        RiskLevel.MEDIUM: 0.08,
        RiskLevel.HIGH: 0.18,
        RiskLevel.CRITICAL: 0.30,
    }

    def assess(self, task: TaskContext) -> ConfidenceAssessment:
        evidence_score = self._evidence_score(task.evidence)
        base = 0.70 if not task.evidence else evidence_score
        confidence = base * (1.0 - 0.65 * task.uncertainty)
        confidence -= self._risk_penalty[task.risk]
        confidence = max(0.0, min(1.0, confidence))

        reasons: list[str] = []
        if task.uncertainty >= 0.35:
            reasons.append("uncertainty-above-threshold")
        if task.risk in {RiskLevel.HIGH, RiskLevel.CRITICAL}:
            reasons.append("high-consequence-task")
        if task.evidence and not any(e.verified for e in task.evidence):
            reasons.append("no-verified-evidence")
        if task.requires_fresh_information:
            reasons.append("freshness-sensitive")

        requires_verification = (
            confidence < 0.72
            or task.uncertainty >= 0.35
            or task.risk in {RiskLevel.HIGH, RiskLevel.CRITICAL}
            or task.requires_fresh_information
        )
        return ConfidenceAssessment(round(confidence, 4), requires_verification, tuple(reasons))

    @staticmethod
    def _evidence_score(evidence: tuple[Evidence, ...]) -> float:
        if not evidence:
            return 0.70
        weighted = []
        for item in evidence:
            verification_bonus = 0.12 if item.verified else 0.0
            weighted.append(min(1.0, item.confidence + verification_bonus))
        return sum(weighted) / len(weighted)
