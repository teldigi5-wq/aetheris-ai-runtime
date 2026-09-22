from __future__ import annotations

from datetime import datetime, timezone

from .models import Claim


class TemporalIntelligence:
    @staticmethod
    def is_valid_at(claim: Claim, moment: datetime) -> bool:
        if moment.tzinfo is None:
            moment = moment.replace(tzinfo=timezone.utc)
        start = TemporalIntelligence._parse(claim.valid_from)
        end = TemporalIntelligence._parse(claim.valid_until)
        return (start is None or moment >= start) and (end is None or moment <= end)

    @staticmethod
    def changed_between(older: Claim, newer: Claim) -> bool:
        return (
            older.subject.casefold() == newer.subject.casefold()
            and older.predicate.casefold() == newer.predicate.casefold()
            and older.value != newer.value
        )

    @staticmethod
    def _parse(value: str | None) -> datetime | None:
        if value is None:
            return None
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=timezone.utc)
        return parsed
