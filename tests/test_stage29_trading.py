import importlib.util
import json
import sys
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "scripts" / "stage29" / "trading_intelligence.py"
POLICY_PATH = ROOT / "configs" / "stage29" / "trading-policy.json"
FIXTURES = ROOT / "tests" / "fixtures" / "stage29"

spec = importlib.util.spec_from_file_location("stage29_trading", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
sys.modules[spec.name] = module
spec.loader.exec_module(module)


class Stage29TradingTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.policy = module.load_json(POLICY_PATH)

    def fixture(self, name):
        return module.load_json(FIXTURES / name)

    def test_policy_is_fail_closed(self):
        module.validate_policy(self.policy)
        self.assertFalse(self.policy["liveExecutionEnabled"])
        self.assertNotIn("LIVE", self.policy["allowedModes"])
        self.assertTrue(self.policy["requireExplicitApproval"])
        self.assertFalse(self.policy["networkRequiredForCertification"])
        self.assertFalse(self.policy["secretsRequiredForCertification"])

    def test_policy_rejects_live_enablement(self):
        bad = deepcopy(self.policy)
        bad["liveExecutionEnabled"] = True
        with self.assertRaises(module.PolicyError):
            module.validate_policy(bad)

    def test_policy_rejects_unsafe_leverage_ceiling(self):
        bad = deepcopy(self.policy)
        bad["maxLeverage"] = 25
        with self.assertRaises(module.PolicyError):
            module.validate_policy(bad)

    def test_policy_rejects_live_in_allowed_modes(self):
        bad = deepcopy(self.policy)
        bad["allowedModes"] = ["PAPER", "LIVE"]
        with self.assertRaises(module.PolicyError):
            module.validate_policy(bad)

    def test_approved_paper_setup_is_proposal_only(self):
        result = module.plan(self.policy, self.fixture("approved-paper.json"))
        self.assertEqual(result["status"], "PROPOSAL_READY")
        self.assertFalse(result["executionPermitted"])
        self.assertFalse(result["orderSubmitted"])
        self.assertTrue(result["requiresExplicitApproval"])
        self.assertEqual(result["rejectionReasons"], [])
        self.assertEqual(result["cappedLeverage"], 3)
        self.assertEqual(result["rewardRiskRatio"], "2.00000000")

    def test_approved_short_geometry(self):
        result = module.plan(self.policy, self.fixture("short-approved.json"))
        self.assertEqual(result["status"], "PROPOSAL_READY")
        self.assertEqual(result["rewardRiskRatio"], "2.00000000")
        self.assertFalse(result["executionPermitted"])

    def test_boundary_values_remain_valid(self):
        for name in (
            "min-score-boundary.json",
            "max-risk-boundary.json",
            "max-leverage-boundary.json",
            "daily-loss-boundary.json",
            "max-position-boundary.json",
            "rr-boundary.json",
        ):
            with self.subTest(name=name):
                result = module.plan(self.policy, self.fixture(name))
                self.assertEqual(result["status"], "PROPOSAL_READY")
                self.assertFalse(result["orderSubmitted"])

    def test_low_score_rejected(self):
        result = module.plan(self.policy, self.fixture("low-score.json"))
        self.assertIn("SETUP_SCORE_BELOW_MINIMUM", result["rejectionReasons"])
        self.assertEqual(result["proposedNotional"], "0.00000000")

    def test_invalid_long_and_short_geometry_rejected(self):
        for name in ("invalid-geometry.json", "short-invalid-geometry.json"):
            with self.subTest(name=name):
                result = module.plan(self.policy, self.fixture(name))
                self.assertIn("INVALID_TP_SL_GEOMETRY", result["rejectionReasons"])

    def test_daily_loss_lockout(self):
        result = module.plan(self.policy, self.fixture("daily-loss-lockout.json"))
        self.assertIn("DAILY_LOSS_LOCKOUT", result["rejectionReasons"])

    def test_open_position_lockout(self):
        result = module.plan(self.policy, self.fixture("open-position-lockout.json"))
        self.assertIn("OPEN_POSITION_LOCKOUT", result["rejectionReasons"])

    def test_live_mode_is_always_rejected(self):
        result = module.plan(self.policy, self.fixture("live-mode.json"))
        self.assertIn("LIVE_EXECUTION_BLOCKED_STAGE_29", result["rejectionReasons"])
        self.assertFalse(result["executionPermitted"])
        self.assertFalse(result["orderSubmitted"])

    def test_testnet_is_disabled_by_default(self):
        result = module.plan(self.policy, self.fixture("testnet-disabled.json"))
        self.assertIn("TESTNET_EXECUTION_DISABLED", result["rejectionReasons"])
        self.assertFalse(result["executionPermitted"])

    def test_unknown_mode_is_rejected(self):
        result = module.plan(self.policy, self.fixture("unknown-mode.json"))
        self.assertIn("MODE_NOT_ALLOWED", result["rejectionReasons"])

    def test_risk_reward_and_stop_limits(self):
        cases = {
            "risk-limit.json": "RISK_PER_TRADE_EXCEEDS_POLICY",
            "reward-risk.json": "REWARD_RISK_BELOW_MINIMUM",
            "stop-distance.json": "STOP_DISTANCE_EXCEEDS_POLICY",
            "bad-risk-zero.json": "RISK_PER_TRADE_EXCEEDS_POLICY",
        }
        for name, reason in cases.items():
            with self.subTest(name=name):
                result = module.plan(self.policy, self.fixture(name))
                self.assertIn(reason, result["rejectionReasons"])
                self.assertEqual(result["proposedNotional"], "0.00000000")

    def test_requested_leverage_is_capped_for_reporting(self):
        result = module.plan(self.policy, self.fixture("leverage-cap.json"))
        self.assertEqual(result["status"], "PROPOSAL_READY")
        self.assertEqual(result["requestedLeverage"], 10)
        self.assertEqual(result["cappedLeverage"], 3)
        self.assertFalse(result["executionPermitted"])

    def test_invalid_leverage_rejected(self):
        result = module.plan(self.policy, self.fixture("invalid-leverage.json"))
        self.assertIn("INVALID_LEVERAGE", result["rejectionReasons"])

    def test_impossible_account_inputs_fail_closed(self):
        cases = {
            "negative-loss.json": "DAILY_LOSS_FRACTION_INVALID",
            "negative-open-positions.json": "OPEN_POSITION_COUNT_INVALID",
            "score-over-100.json": "SETUP_SCORE_OUT_OF_RANGE",
            "score-negative.json": "SETUP_SCORE_OUT_OF_RANGE",
            "negative-balance.json": "NON_POSITIVE_PRICE_OR_BALANCE",
            "zero-balance.json": "NON_POSITIVE_PRICE_OR_BALANCE",
            "zero-entry.json": "NON_POSITIVE_PRICE_OR_BALANCE",
        }
        for name, reason in cases.items():
            with self.subTest(name=name):
                result = module.plan(self.policy, self.fixture(name))
                self.assertEqual(result["status"], "REJECTED")
                self.assertIn(reason, result["rejectionReasons"])
                self.assertEqual(result["proposedNotional"], "0.00000000")
                self.assertFalse(result["executionPermitted"])
                self.assertFalse(result["orderSubmitted"])

    def test_non_integral_counts_fail_closed(self):
        candidate = self.fixture("approved-paper.json")
        candidate["openPositions"] = 1.5
        with self.assertRaises(module.PolicyError):
            module.plan(self.policy, candidate)

    def test_non_finite_numbers_fail_closed(self):
        candidate = self.fixture("approved-paper.json")
        candidate["accountBalance"] = "NaN"
        with self.assertRaises(module.PolicyError):
            module.plan(self.policy, candidate)

    def test_deterministic_serialization(self):
        candidate = self.fixture("approved-paper.json")
        first = module.canonical_json(module.plan(self.policy, candidate))
        second = module.canonical_json(module.plan(self.policy, candidate))
        self.assertEqual(first.encode("utf-8"), second.encode("utf-8"))

    def test_cli_writes_deterministic_json(self):
        result = module.plan(self.policy, self.fixture("approved-paper.json"))
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "out.json"
            path.write_text(module.canonical_json(result), encoding="utf-8")
            parsed = json.loads(path.read_text(encoding="utf-8"))
            self.assertEqual(parsed["stage"], 29)
            self.assertFalse(parsed["orderSubmitted"])


if __name__ == "__main__":
    unittest.main()
