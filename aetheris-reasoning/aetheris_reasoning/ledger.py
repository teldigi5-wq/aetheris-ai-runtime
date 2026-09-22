from __future__ import annotations

from dataclasses import asdict
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
from typing import Any

from .models import DecisionRecord


class DecisionLedger:
    """Append-only JSONL ledger with a SHA-256 hash chain for tamper evidence."""

    def __init__(self, path: str | Path) -> None:
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)

    def append(self, record: DecisionRecord) -> dict[str, Any]:
        previous_hash = self._last_hash()
        payload = asdict(record)
        payload["route"] = record.route.value
        entry = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "previous_hash": previous_hash,
            "record": payload,
        }
        entry["record_hash"] = self._hash_payload(entry)
        with self.path.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(entry, sort_keys=True, separators=(",", ":")) + "\n")
        return entry

    def verify(self) -> bool:
        previous_hash = "GENESIS"
        if not self.path.exists():
            return True

        for line in self.path.read_text(encoding="utf-8").splitlines():
            entry = json.loads(line)
            claimed_hash = entry.pop("record_hash", None)
            if entry.get("previous_hash") != previous_hash:
                return False
            actual_hash = self._hash_payload(entry)
            if claimed_hash != actual_hash:
                return False
            previous_hash = claimed_hash
        return True

    def _last_hash(self) -> str:
        if not self.path.exists():
            return "GENESIS"
        lines = [line for line in self.path.read_text(encoding="utf-8").splitlines() if line]
        if not lines:
            return "GENESIS"
        return json.loads(lines[-1])["record_hash"]

    @staticmethod
    def _hash_payload(payload: dict[str, Any]) -> str:
        encoded = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode("utf-8")
        return hashlib.sha256(encoded).hexdigest()
