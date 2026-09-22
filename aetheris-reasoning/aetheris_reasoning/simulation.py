from __future__ import annotations

from dataclasses import dataclass

from .models import RiskLevel, TaskContext


@dataclass(frozen=True)
class SimulationDecision:
    required: bool
    reason: str


class SimulationGate:
    def evaluate(self, task: TaskContext) -> SimulationDecision:
        if task.risk == RiskLevel.CRITICAL:
            return SimulationDecision(True, "critical-risk")
        if task.has_side_effects and task.risk == RiskLevel.HIGH:
            return SimulationDecision(True, "high-risk-side-effect")
        if task.has_side_effects and not task.reversible:
            return SimulationDecision(True, "irreversible-side-effect")
        return SimulationDecision(False, "safe-to-proceed-with-normal-verification")
