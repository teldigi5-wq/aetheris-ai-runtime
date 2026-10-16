"""Stage46.31 SQLite transaction/restart/replay proof on inert public CI vectors."""
from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
import copy
import json
from pathlib import Path
import sys
import tempfile
import threading
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "aetheris-quant"))
from durable_offline_paper_replay_lab import DurableOfflinePaperReplayLab
from app.offline_paper_state_boundary import mac

DATA = json.loads((ROOT / "risk-contracts/stage46-offline-paper-state-chain-fixtures.json").read_text())
KEY = DATA["test_key_utf8"].encode("ascii")
ROWS = DATA["snapshots"]


class DurablePaperReplayTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / "inert-test-cursor.sqlite"
        DurableOfflinePaperReplayLab.seed_disposable_db(self.path)
        self.lab = DurableOfflinePaperReplayLab(self.path, KEY)

    def inspect(self, lab, idx):
        row = ROWS[idx]
        return lab.inspect(row["state"], row["tag"], row["verify_at_ms"])

    def deny_all_execution(self, result):
        self.assertEqual(result["decision"], "NO_GO")
        for key in ("state_applied", "testnet_order_authorized",
                    "live_order_authorized", "production_authorized"):
            self.assertFalse(result[key])

    def test_first_signed_state_commits_to_disk_only(self):
        r = self.inspect(self.lab, 0)
        self.assertTrue(r["accepted"], r)
        self.assertEqual(self.lab.cursor(), (1, ROWS[0]["sha256"]))
        self.deny_all_execution(r)

    def test_restart_retains_cursor_and_refuses_replay(self):
        self.assertTrue(self.inspect(self.lab, 0)["accepted"])
        restarted = DurableOfflinePaperReplayLab(self.path, KEY)
        self.assertEqual(restarted.cursor(), (1, ROWS[0]["sha256"]))
        self.assertFalse(self.inspect(restarted, 0)["accepted"])
        second = self.inspect(restarted, 1)
        self.assertTrue(second["accepted"], second)
        self.assertEqual(restarted.cursor(), (2, ROWS[1]["sha256"]))
        self.deny_all_execution(second)

    def test_second_before_first_is_refused(self):
        self.assertFalse(self.inspect(self.lab, 1)["accepted"])
        self.assertEqual(self.lab.cursor()[0], 0)

    def test_wrong_chain_digest_after_first_commit_refused(self):
        self.assertTrue(self.inspect(self.lab, 0)["accepted"])
        s = copy.deepcopy(ROWS[1]["state"])
        s["previous_digest"] = "0" * 64
        r = self.lab.inspect(s, mac(s, KEY), ROWS[1]["verify_at_ms"])
        self.assertFalse(r["accepted"])
        self.assertEqual(self.lab.cursor(), (1, ROWS[0]["sha256"]))

    def test_changed_cash_with_old_tag_cannot_move_state(self):
        s = copy.deepcopy(ROWS[0]["state"])
        s["cash_minor"] += 1
        self.assertFalse(self.lab.inspect(s, ROWS[0]["tag"],
                                          ROWS[0]["verify_at_ms"])["accepted"])
        self.assertEqual(self.lab.cursor()[0], 0)

    def test_simulated_crash_before_commit_rolls_back_entire_update(self):
        r = ROWS[0]
        denied = self.lab.inspect(r["state"], r["tag"], r["verify_at_ms"],
                                  simulate_crash=True)
        self.assertFalse(denied["accepted"])
        self.assertEqual(self.lab.cursor(), (0, "0" * 64))
        self.assertTrue(self.inspect(self.lab, 0)["accepted"])

    def test_concurrent_consumers_cannot_commit_same_sequence_twice(self):
        start = threading.Barrier(2)
        def task():
            start.wait(timeout=5)
            instance = DurableOfflinePaperReplayLab(self.path, KEY)
            return self.inspect(instance, 0)
        with ThreadPoolExecutor(max_workers=2) as ex:
            f1 = ex.submit(task)
            f2 = ex.submit(task)
            one, two = f1.result(timeout=10), f2.result(timeout=10)
        self.assertEqual(sum(bool(r["accepted"]) for r in (one, two)), 1)
        self.assertEqual(self.lab.cursor(), (1, ROWS[0]["sha256"]))
        for result in (one, two):
            self.deny_all_execution(result)

    def test_expired_snapshot_never_advances_cursor(self):
        row = ROWS[0]
        out = self.lab.inspect(row["state"], row["tag"],
                               row["state"]["expires_ms"] + 1)
        self.assertFalse(out["accepted"])
        self.assertEqual(self.lab.cursor()[0], 0)

    def test_unknown_order_authority_field_rejected(self):
        state = copy.deepcopy(ROWS[0]["state"])
        state["place_order"] = True
        out = self.lab.inspect(state, ROWS[0]["tag"], ROWS[0]["verify_at_ms"])
        self.assertFalse(out["accepted"])
        self.deny_all_execution(out)

    def test_missing_cursor_does_not_reinitialize_from_genesis(self):
        import sqlite3
        with sqlite3.connect(self.path) as db:
            db.execute("delete from stage4631_synthetic_cursor")
        self.assertFalse(self.inspect(self.lab, 0)["accepted"])
        self.assertEqual(self.lab.cursor()[0], -1)

    def test_no_production_durable_interface_or_exchange_authority(self):
        self.assertFalse(any(DATA["runtime_authority"].values()))
        self.assertEqual(DATA["decision"], "NO_GO")
        self.assertNotIn("enable_autonomous_testnet",
                         Path(__file__).read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
