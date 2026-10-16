"""Stage 46.29: synthetic parity vectors + adversarial non-authorization."""
import copy
import json
import math
import sys
import tempfile
import unittest
from pathlib import Path

QUANT_ROOT = Path(__file__).resolve().parents[1]
PROJECT_ROOT = QUANT_ROOT.parent
sys.path.insert(0, str(QUANT_ROOT))
from app.paper_risk_contract import (  # noqa: E402
    load_contract, review_budget, review_drawdown,
)

CONTRACT_PATH = PROJECT_ROOT / "risk-contracts/stage46-paper-parity-v1.json"


class PaperRiskCrossLanguageParityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = load_contract(CONTRACT_PATH)
        cls.policy = cls.data["policy"]

    def test_fixed_budget_cases_match_java_reference_predictions(self):
        for vector in self.data["budget_cases"]:
            with self.subTest(vector=vector["id"]):
                outcome = review_budget(self.policy, vector["input"])
                expected = vector["expected"]
                self.assertEqual(outcome["allowed"], expected["allowed"])
                for field in ("approved_notional", "estimated_loss_at_stop",
                              "remaining_daily_loss_budget",
                              "projected_gross_exposure",
                              "projected_correlated_exposure"):
                    if field in expected:
                        self.assertAlmostEqual(outcome[field], expected[field], places=7)
                self.assertFalse(outcome["testnet_order_authorized"])
                self.assertFalse(outcome["live_order_authorized"])
                self.assertEqual(outcome["execution_scope"], "PAPER_PARITY_FIXTURE_ONLY")

    def test_fixed_drawdown_cases_match_java_reference_predictions(self):
        for case in self.data["drawdown_cases"]:
            with self.subTest(vector=case["id"]):
                values = {k: v for k, v in case.items()
                          if k not in ("id", "expected")}
                outcome = review_drawdown(self.policy, values)
                self.assertEqual(outcome["halted"], case["expected"]["halted"])
                self.assertAlmostEqual(outcome["peak_equity"],
                                       case["expected"]["peak_equity"], places=7)
                self.assertAlmostEqual(outcome["max_drawdown_pct"],
                                       case["expected"]["max_drawdown_pct"], places=7)
                self.assertFalse(outcome["testnet_order_authorized"])

    def test_contract_is_read_only_and_testnet_remains_unverified(self):
        self.assertEqual(self.data["scope"], "CROSS_LANGUAGE_PAPER_PARITY_TEST_ONLY")
        self.assertEqual(self.data["decision"], "NO_GO")
        self.assertFalse(any(self.data["release_authorization"].values()))
        review = self.data["quant_settings_review"]
        for flag in ("quant_testnet_peak_drawdown_latch_verified",
                     "java_python_state_store_shared",
                     "execution_policy_parity_verified"):
            self.assertIs(review[flag], False)

    def test_any_forged_release_authority_is_rejected(self):
        for key in self.data["release_authorization"]:
            with self.subTest(flag=key):
                doc = copy.deepcopy(self.data)
                doc["release_authorization"][key] = True
                self._reject_doc(doc)

    def test_drifted_risk_per_trade_or_daily_loss_is_rejected(self):
        for field, value in (("risk_per_trade_fraction", .01),
                             ("max_daily_loss_fraction", .10),
                             ("max_peak_to_trough_drawdown_fraction", .12)):
            doc = copy.deepcopy(self.data)
            doc["policy"][field] = value
            self._reject_doc(doc)

    def test_enabling_testnet_equivalence_claim_is_rejected(self):
        for key in ("quant_testnet_peak_drawdown_latch_verified",
                    "java_python_state_store_shared",
                    "execution_policy_parity_verified"):
            doc = copy.deepcopy(self.data)
            doc["quant_settings_review"][key] = True
            self._reject_doc(doc)

    def test_invalid_and_nonfinite_budget_inputs_are_never_allowed(self):
        example = copy.deepcopy(self.data["budget_cases"][0]["input"])
        for field in ("cash_balance", "fill_price", "entry_price",
                      "stop_loss", "recommended_notional", "daily_realized_pnl"):
            for invalid in (float("nan"), float("inf"), float("-inf")):
                data = copy.deepcopy(example)
                data[field] = invalid
                self.assertFalse(review_budget(self.policy, data)["allowed"])

    def test_boolean_numbers_are_never_accepted(self):
        example = copy.deepcopy(self.data["budget_cases"][0]["input"])
        for key in ("leverage", "cash_balance", "recommended_notional"):
            data = copy.deepcopy(example)
            data[key] = True
            self.assertFalse(review_budget(self.policy, data)["allowed"])

    def test_invalid_stops_and_wrong_side_rejected(self):
        for side, stop in (("LONG", 101), ("SHORT", 99),
                           ("NO_TRADE", 99)):
            data = copy.deepcopy(self.data["budget_cases"][0]["input"])
            data.update({"side": side, "stop_loss": stop})
            self.assertFalse(review_budget(self.policy, data)["allowed"])

    def test_high_leverage_cannot_bypass_limits(self):
        data = copy.deepcopy(self.data["budget_cases"][0]["input"])
        data["leverage"] = 20
        self.assertFalse(review_budget(self.policy, data)["allowed"])

    def test_unreviewed_fields_cannot_elevate_authority(self):
        data = copy.deepcopy(self.data["budget_cases"][0]["input"])
        data["force_live_order"] = True
        self.assertFalse(review_budget(self.policy, data)["allowed"])

    def test_missing_and_nonfinite_existing_open_exposures_deny(self):
        data = copy.deepcopy(self.data["budget_cases"][0]["input"])
        data["open_positions"] = [{
            "symbol": "BTCUSDT", "side": "LONG", "entry_price": 100,
            "stop_loss": 99, "quantity": float("nan"), "notional": 500,
        }]
        self.assertFalse(review_budget(self.policy, data)["allowed"])

    def test_historical_halt_can_never_reset_even_after_large_gain(self):
        case = copy.deepcopy(self.data["drawdown_cases"][2])
        value = {k: v for k, v in case.items() if k not in ("id", "expected")}
        value["observed_equity"] = 1000000
        decision = review_drawdown(self.policy, value)
        self.assertTrue(decision["halted"])
        self.assertFalse(decision["testnet_order_authorized"])

    def test_invalid_equity_is_a_halt_not_an_error_masked_as_success(self):
        case = copy.deepcopy(self.data["drawdown_cases"][0])
        value = {k: v for k, v in case.items() if k not in ("id", "expected")}
        for bad in (float("nan"), float("inf"), -1, 0):
            value["observed_equity"] = bad
            self.assertTrue(review_drawdown(self.policy, value)["halted"])

    def test_duplicate_json_keys_and_nan_token_fail_closed(self):
        for text in ('{"schema_version":1,"schema_version":1}',
                     '{"schema_version": NaN}'):
            with tempfile.TemporaryDirectory() as folder:
                path = Path(folder) / "contract.json"
                path.write_text(text, encoding="utf-8")
                with self.assertRaises(ValueError):
                    load_contract(path)

    def test_wrong_version_and_unknown_scope_rejected(self):
        for key, value in (("schema_version", 2), ("scope", "LIVE_MONEY"),
                           ("decision", "GO")):
            doc = copy.deepcopy(self.data)
            doc[key] = value
            self._reject_doc(doc)

    def _reject_doc(self, document):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "contract.json"
            path.write_text(json.dumps(document), encoding="utf-8")
            with self.assertRaises(ValueError):
                load_contract(path)


if __name__ == "__main__":
    unittest.main()
