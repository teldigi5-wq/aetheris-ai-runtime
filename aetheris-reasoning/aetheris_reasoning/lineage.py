from __future__ import annotations

from dataclasses import dataclass

from .models import SourceKind


@dataclass(frozen=True)
class SourceAssessment:
    trust: float
    requires_cross_check: bool
    rationale: str


class SourceTrustEngine:
    _base = {
        SourceKind.HUMAN: 0.78,
        SourceKind.OFFICIAL: 0.95,
        SourceKind.TOOL: 0.90,
        SourceKind.FILE: 0.84,
        SourceKind.MEMORY: 0.64,
        SourceKind.WEB: 0.70,
        SourceKind.MODEL_INFERENCE: 0.45,
    }

    def assess(
        self,
        source_kind: SourceKind,
        *,
        verified: bool = False,
        stale: bool = False,
        conflicting: bool = False,
    ) -> SourceAssessment:
        trust = self._base[source_kind]
        if verified:
            trust += 0.05
        if stale:
            trust -= 0.20
        if conflicting:
            trust -= 0.25
        trust = max(0.0, min(1.0, trust))
        requires = trust < 0.75 or source_kind == SourceKind.MODEL_INFERENCE or conflicting
        rationale = f"{source_kind.value}; verified={verified}; stale={stale}; conflicting={conflicting}"
        return SourceAssessment(round(trust, 4), requires, rationale)
