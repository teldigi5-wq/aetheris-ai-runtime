from __future__ import annotations

from dataclasses import dataclass
from pathlib import PurePosixPath


@dataclass(frozen=True)
class ImpactReport:
    services: tuple[str, ...]
    required_checks: tuple[str, ...]
    rollback_required: bool
    reasons: tuple[str, ...]


class ChangeImpactAnalyzer:
    def analyze(self, changed_paths: list[str]) -> ImpactReport:
        services: set[str] = set()
        checks: set[str] = set()
        reasons: list[str] = []
        rollback_required = False

        for raw in changed_paths:
            path = PurePosixPath(raw)
            first = path.parts[0] if path.parts else ""

            if first in {"gateway", "identity-service", "user-service", "audit-service"}:
                services.add(first)
                checks.update({"java-tests", "java-build"})
            if first == "dashboard":
                services.add("dashboard")
                checks.add("dashboard-build")
            if first == "aetheris-quant":
                services.add("aetheris-quant")
                checks.add("quant-tests")
            if first == "aetheris-reasoning":
                services.add("aetheris-reasoning")
                checks.add("reasoning-tests")
            if first in {"deploy", "observability"} or raw == "docker-compose.yml":
                checks.add("deployment-validation")
                rollback_required = True
                reasons.append(f"infrastructure-change:{raw}")
            if "security" in raw.casefold() or first == "identity-service":
                checks.add("security-review")
            if raw.startswith(".github/workflows/"):
                checks.add("workflow-review")
                rollback_required = True

        return ImpactReport(
            services=tuple(sorted(services)),
            required_checks=tuple(sorted(checks)),
            rollback_required=rollback_required,
            reasons=tuple(reasons),
        )
