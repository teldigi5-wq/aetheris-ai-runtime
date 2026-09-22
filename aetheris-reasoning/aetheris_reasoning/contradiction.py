from __future__ import annotations

from dataclasses import dataclass

from .models import Claim


@dataclass(frozen=True)
class Contradiction:
    left: Claim
    right: Claim
    reason: str


class ContradictionDetector:
    """Surfaces incompatible claims instead of averaging them into false certainty."""

    def detect(self, claims: list[Claim]) -> list[Contradiction]:
        contradictions: list[Contradiction] = []
        for index, left in enumerate(claims):
            for right in claims[index + 1 :]:
                if (
                    self._norm(left.subject) == self._norm(right.subject)
                    and self._norm(left.predicate) == self._norm(right.predicate)
                    and self._norm(left.value) != self._norm(right.value)
                    and left.source_id != right.source_id
                ):
                    contradictions.append(
                        Contradiction(left, right, "same-subject-and-predicate-different-value")
                    )
        return contradictions

    @staticmethod
    def _norm(value: str) -> str:
        return " ".join(value.casefold().split())
