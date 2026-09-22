from __future__ import annotations

from dataclasses import dataclass

from .models import RiskLevel, TaskContext


@dataclass(frozen=True)
class VerificationFinding:
    severity: str
    code: str
    message: str


class VerifierCritic:
    """Independent deterministic critic for plans before execution."""

    def review(self, task: TaskContext, proposed_steps: list[str]) -> tuple[VerificationFinding, ...]:
        findings: list[VerificationFinding] = []

        if not proposed_steps:
            findings.append(VerificationFinding("error", "EMPTY_PLAN", "Plan has no executable steps."))

        if task.has_side_effects and task.risk in {RiskLevel.HIGH, RiskLevel.CRITICAL}:
            if not any("verify" in step.casefold() or "test" in step.casefold() for step in proposed_steps):
                findings.append(
                    VerificationFinding(
                        "error",
                        "MISSING_VERIFICATION",
                        "High-consequence plan needs an explicit verification step.",
                    )
                )

        if task.has_side_effects and not task.reversible:
            if not any(
                token in step.casefold()
                for step in proposed_steps
                for token in ("backup", "checkpoint", "rollback")
            ):
                findings.append(
                    VerificationFinding(
                        "error",
                        "MISSING_RECOVERY",
                        "Irreversible side effect needs a checkpoint, backup or rollback plan.",
                    )
                )

        if task.private_data and any("cloud" in step.casefold() for step in proposed_steps):
            findings.append(
                VerificationFinding(
                    "warning",
                    "PRIVATE_DATA_CLOUD_ROUTE",
                    "Private-data task contains a cloud step and needs explicit policy review.",
                )
            )

        return tuple(findings)
