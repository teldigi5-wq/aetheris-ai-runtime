from __future__ import annotations

from dataclasses import dataclass

from .confidence import ConfidenceAssessment, ConfidenceEngine
from .models import ActionRoute, RiskLevel, TaskContext


@dataclass(frozen=True)
class RoutingDecision:
    route: ActionRoute
    confidence: float
    verification_required: bool
    reasons: tuple[str, ...]


class MetaReasoningEngine:
    """Deterministic first-pass router. Models are workers, never the policy authority."""

    def __init__(self, confidence_engine: ConfidenceEngine | None = None) -> None:
        self._confidence = confidence_engine or ConfidenceEngine()

    def route(self, task: TaskContext) -> RoutingDecision:
        assessment: ConfidenceAssessment = self._confidence.assess(task)
        reasons = list(assessment.reasons)

        if task.risk == RiskLevel.CRITICAL or (
            task.has_side_effects and not task.reversible and task.risk == RiskLevel.HIGH
        ):
            reasons.append("owner-authority-required")
            route = ActionRoute.OWNER_APPROVAL
        elif task.risk == RiskLevel.HIGH and task.has_side_effects:
            reasons.append("simulate-before-side-effect")
            route = ActionRoute.SIMULATION
        elif assessment.requires_verification and task.complexity >= 7:
            reasons.append("multi-perspective-review")
            route = ActionRoute.COUNCIL
        elif task.requires_fresh_information:
            reasons.append("external-evidence-needed")
            route = ActionRoute.RESEARCH
        elif task.complexity >= 5:
            reasons.append("specialist-capability-needed")
            route = ActionRoute.SPECIALIST_AGENT
        elif task.complexity >= 3:
            reasons.append("reasoning-model-useful")
            route = ActionRoute.LOCAL_MODEL
        else:
            reasons.append("deterministic-path-sufficient")
            route = ActionRoute.DIRECT

        return RoutingDecision(
            route=route,
            confidence=assessment.confidence,
            verification_required=assessment.requires_verification,
            reasons=tuple(reasons),
        )
