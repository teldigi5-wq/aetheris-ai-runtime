"""Stage46.31 isolated SQLite durability lab. Test source only; never imported by the Quant app."""
from __future__ import annotations

import hmac
import sqlite3
from pathlib import Path

from app.offline_paper_state_boundary import InvalidSnapshot, canonical, digest, mac

GENESIS = "0" * 64


class DurableOfflinePaperReplayLab:
    def __init__(self, db_path, test_key):
        if not isinstance(test_key, bytes) or len(test_key) < 32:
            raise ValueError("ONLY_DISPOSABLE_TEST_KEY")
        self.path = Path(db_path)
        self.key = test_key

    @staticmethod
    def seed_disposable_db(path):
        # Explicit ONE-TIME setup only. Restarting a verifier must not reset cursor.
        with sqlite3.connect(str(path)) as db:
            db.execute(
                "create table stage4631_synthetic_cursor ("
                "account_id text primary key, sequence_number integer not null,"
                "last_digest text not null)"
            )
            db.execute(
                "insert into stage4631_synthetic_cursor values (?,?,?)",
                ("paper-default", 0, GENESIS),
            )

    def cursor(self):
        with sqlite3.connect(str(self.path), timeout=3) as db:
            row = db.execute(
                "select sequence_number,last_digest from stage4631_synthetic_cursor "
                "where account_id=?", ("paper-default",)
            ).fetchall()
            return row[0] if len(row) == 1 else (-1, "")

    def inspect(self, state, tag, now_ms, simulate_crash=False):
        result = {
            "accepted": False, "sequence": self.cursor()[0],
            "reason": "FAILED_CLOSED",
            "state_applied": False, "testnet_order_authorized": False,
            "live_order_authorized": False, "production_authorized": False,
            "decision": "NO_GO",
        }
        try:
            canonical(state)
            if (type(now_ms) is not int or now_ms < state["issued_ms"]
                    or now_ms > state["expires_ms"]
                    or not isinstance(tag, str) or len(tag) != 64
                    or any(c not in "0123456789abcdef" for c in tag)
                    or not hmac.compare_digest(tag, mac(state, self.key))):
                return result
            with sqlite3.connect(str(self.path), timeout=3, isolation_level=None) as db:
                try:
                    db.execute("BEGIN IMMEDIATE")
                    rows = db.execute(
                        "select sequence_number,last_digest from stage4631_synthetic_cursor "
                        "where account_id=?", ("paper-default",)
                    ).fetchall()
                    if len(rows) != 1:
                        db.rollback()
                        return result
                    old_seq, old_hash = rows[0]
                    if state["sequence"] != old_seq + 1 or state["previous_digest"] != old_hash:
                        db.rollback()
                        return result
                    updated = db.execute(
                        "update stage4631_synthetic_cursor set sequence_number=?,"
                        "last_digest=? where account_id=? and sequence_number=? and last_digest=?",
                        (state["sequence"], digest(state), "paper-default", old_seq, old_hash),
                    ).rowcount
                    if updated != 1 or simulate_crash:
                        db.rollback()
                        return result
                    db.commit()
                    return {**result, "accepted": True,
                            "sequence": state["sequence"], "reason": "DURABLE_SYNTHETIC_ONLY"}
                except sqlite3.Error:
                    db.rollback()
                    return result
        except (InvalidSnapshot, ValueError, TypeError, sqlite3.Error, OSError):
            return result
