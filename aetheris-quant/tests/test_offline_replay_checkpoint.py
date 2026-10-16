"""Stage46.32 Python synthetic checkpoint integrity and stale backup classifier."""
import copy
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "aetheris-quant"))
from app.offline_replay_checkpoint import canonical, fixture_mac, compare  # noqa: E402

fixture = json.loads((ROOT / "risk-contracts/stage46-independent-replay-checkpoint-fixture.json").read_text())
receipt = fixture["checkpoint"]
key = fixture["public_test_key_utf8"].encode("ascii")
when = fixture["check_at_ms"]
anchor = {"sequence": 2, "digest": receipt["highest_digest"]}


class OfflineReplayCheckpointTests(unittest.TestCase):
    def verify_no_authority(self, result):
        self.assertEqual(result["decision"], "NO_GO")
        self.assertFalse(result["independent_trust_root_verified"])
        self.assertFalse(result["account_state_imported"])
        self.assertFalse(result["python_quant_testnet_authorized"])
        self.assertFalse(result["live_money_authorized"])
        self.assertFalse(result["production_authorized"])

    def test_fixed_mac_matches_java_reference(self):
        self.assertEqual(fixture_mac(receipt, key), fixture["tag"])
        self.assertIn(b"highest_sequence=2\n", canonical(receipt))

    def test_exact_match_still_is_not_release_authority(self):
        result = compare(receipt, fixture["tag"], key, when, anchor)
        self.assertTrue(result["database_matches_presented_checkpoint"])
        self.assertEqual(result["classification"], "FIXTURE_CHECKPOINT_MATCH")
        self.verify_no_authority(result)

    def test_empty_anchor_proves_restore_or_missing_db(self):
        result = compare(receipt, fixture["tag"], key, when, None)
        self.assertEqual(result["classification"], "DB_ANCHOR_MISSING_OR_RESTORED")
        self.verify_no_authority(result)

    def test_old_database_backup_is_rejected(self):
        result = compare(receipt, fixture["tag"], key, when,
                         {"sequence": 1, "digest": "7"*64})
        self.assertEqual(result["classification"], "STALE_DB_RESTORE_DETECTED")
        self.verify_no_authority(result)

    def test_db_ahead_of_checkpoint_cannot_be_declared_safe(self):
        result = compare(receipt, fixture["tag"], key, when,
                         {"sequence": 3, "digest": "7"*64})
        self.assertEqual(result["classification"], "UNANCHORED_DB_ADVANCE")
        self.verify_no_authority(result)

    def test_equal_sequence_different_digest_is_divergence(self):
        result = compare(receipt, fixture["tag"], key, when,
                         {"sequence": 2, "digest": "7"*64})
        self.assertEqual(result["classification"], "EQUAL_SEQUENCE_DIFFERENT_DIGEST")
        self.verify_no_authority(result)

    def test_altered_receipt_does_not_reuse_tag(self):
        forged = copy.deepcopy(receipt)
        forged["highest_digest"] = "f"*64
        result = compare(forged, fixture["tag"], key, when, anchor)
        self.assertEqual(result["classification"], "CHECKPOINT_TAG_MISMATCH")
        self.verify_no_authority(result)

    def test_bad_checkpoint_scope_fails_closed(self):
        forged = copy.deepcopy(receipt)
        forged["scope"] = "LIVE_MONEY"
        self.assertEqual(compare(forged, fixture["tag"], key, when, anchor)
                         ["decision"], "NO_GO")
        self.assertFalse(compare(forged, fixture["tag"], key, when, anchor)
                         ["database_matches_presented_checkpoint"])

    def test_expired_and_future_stamps_are_denied(self):
        for clock in (receipt["issued_ms"]-1, receipt["expires_ms"]+1):
            result = compare(receipt, fixture["tag"], key, clock, anchor)
            self.assertEqual(result["classification"], "INVALID_OR_EXPIRED_CHECKPOINT")
            self.verify_no_authority(result)

    def test_short_key_and_invalid_tag_are_denied(self):
        self.assertFalse(compare(receipt, fixture["tag"], b"X", when, anchor)
                         ["database_matches_presented_checkpoint"])
        self.assertFalse(compare(receipt, "z"*64, key, when, anchor)
                         ["database_matches_presented_checkpoint"])

    def test_fake_permission_field_is_rejected(self):
        forged = copy.deepcopy(receipt)
        forged["allow_orders"] = True
        self.assertFalse(compare(forged, fixture["tag"], key, when, anchor)
                         ["database_matches_presented_checkpoint"])

    def test_public_fixture_is_not_an_independent_trust_root(self):
        self.assertFalse(fixture["production_verified"])
        self.assertFalse(fixture["live_trading_authorized"])
        self.assertIn("PUBLIC CI HMAC KEY", fixture["key_warning"])


if __name__ == "__main__":
    unittest.main()
