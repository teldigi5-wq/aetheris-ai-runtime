import copy
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "aetheris-quant"))
from app.offline_paper_state_boundary import (
    OfflinePaperReconciler, InvalidSnapshot, canonical, digest, mac
)

doc = json.loads((ROOT / "risk-contracts/stage46-offline-paper-state-chain-fixtures.json").read_text())
rows = doc["snapshots"]
test_secret = doc["test_key_utf8"].encode("ascii")


class OfflinePaperStateTest(unittest.TestCase):
    def start(self):
        return OfflinePaperReconciler(test_secret)

    def no_runtime_authority(self, result):
        self.assertEqual(result["decision"], "NO_GO")
        self.assertIs(result["snapshot_state_applied"], False)
        self.assertIs(result["python_quant_testnet_orders_authorized"], False)
        self.assertIs(result["live_money_orders_authorized"], False)

    def test_reference_digests_match(self):
        for r in rows:
            self.assertEqual(digest(r["state"]), r["sha256"])
            self.assertEqual(mac(r["state"], test_secret), r["tag"])

    def test_valid_sequence_is_reviewed_without_applying_it(self):
        obj = self.start()
        for i, r in enumerate(rows, start=1):
            outcome = obj.inspect(r["state"], r["tag"], r["verify_at_ms"])
            self.assertIs(outcome["accepted"], True)
            self.assertEqual(outcome["sequence"], i)
            self.no_runtime_authority(outcome)

    def test_repeated_sequence_rejected(self):
        obj = self.start()
        a = rows[0]
        self.assertTrue(obj.inspect(a["state"], a["tag"], a["verify_at_ms"])["accepted"])
        result = obj.inspect(a["state"], a["tag"], a["verify_at_ms"])
        self.assertFalse(result["accepted"])
        self.no_runtime_authority(result)

    def test_second_without_first_rejected(self):
        b = rows[1]
        self.assertFalse(self.start().inspect(b["state"], b["tag"], b["verify_at_ms"])["accepted"])

    def test_changed_value_does_not_pass_integrity_check(self):
        a = rows[0]
        state = copy.deepcopy(a["state"])
        state["cash_minor"] += 1
        self.assertFalse(self.start().inspect(state, a["tag"], a["verify_at_ms"])["accepted"])

    def test_outdated_or_premature_state_rejected(self):
        a = rows[0]
        for t in (a["state"]["issued_ms"]-1, a["state"]["expires_ms"]+1):
            self.assertFalse(self.start().inspect(a["state"], a["tag"], t)["accepted"])

    def test_incorrect_predecessor_rejected(self):
        obj = self.start()
        a, b = rows
        self.assertTrue(obj.inspect(a["state"], a["tag"], a["verify_at_ms"])["accepted"])
        s = copy.deepcopy(b["state"])
        s["previous_digest"] = "0"*64
        self.assertFalse(obj.inspect(s, mac(s, test_secret), b["verify_at_ms"])["accepted"])

    def test_wrong_position_amount_rejected(self):
        s = copy.deepcopy(rows[1]["state"])
        s["positions"][0]["quantity_microunits"] += 1
        with self.assertRaises(InvalidSnapshot):
            canonical(s)

    def test_duplicate_position_rejected(self):
        s = copy.deepcopy(rows[1]["state"])
        s["positions"].append(copy.deepcopy(s["positions"][0]))
        with self.assertRaises(InvalidSnapshot):
            canonical(s)

    def test_invalid_stop_rejected(self):
        s = copy.deepcopy(rows[1]["state"])
        s["positions"][0]["stop_minor"] = 12000
        with self.assertRaises(InvalidSnapshot):
            canonical(s)

    def test_extra_authorization_field_rejected(self):
        s = copy.deepcopy(rows[0]["state"])
        s["allow_order"] = True
        with self.assertRaises(InvalidSnapshot):
            canonical(s)

    def test_non_integer_currency_values_rejected(self):
        s = copy.deepcopy(rows[0]["state"])
        for invalid in (True, 100.25, float("nan"), 0):
            s["cash_minor"] = invalid
            with self.assertRaises(InvalidSnapshot):
                canonical(s)

    def test_excessive_time_window_rejected(self):
        s = copy.deepcopy(rows[0]["state"])
        s["expires_ms"] = s["issued_ms"]+120001
        with self.assertRaises(InvalidSnapshot):
            canonical(s)

    def test_bad_secret_or_bad_tag_not_accepted(self):
        with self.assertRaises(InvalidSnapshot):
            OfflinePaperReconciler(b"x")
        a = rows[0]
        for tag in ("x", "f"*64):
            self.assertFalse(self.start().inspect(a["state"], tag, a["verify_at_ms"])["accepted"])

    def test_reference_is_not_a_live_capability(self):
        self.assertEqual(doc["decision"], "NO_GO")
        self.assertFalse(any(doc["runtime_authority"].values()))
        self.assertIn("NEVER USE FOR PRODUCTION", doc["key_warning"])


if __name__ == "__main__":
    unittest.main()
