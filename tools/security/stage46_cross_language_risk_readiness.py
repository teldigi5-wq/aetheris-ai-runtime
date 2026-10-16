#!/usr/bin/env python3
"""Stage46.29: read-only cross-language paper-risk/default drift evidence.

A successful result establishes synthetic reference-case parity and matching
CODE DEFAULTS only. No Java persisted settings, Quant runtime environment,
testnet margin state, exchange fills or continuous drawdown is certified.
NO live trading, account secrets, network I/O or state mutations.
"""
from __future__ import annotations

import ast
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "aetheris-quant"))
from app.paper_risk_contract import (  # noqa: E402
    load_contract, review_budget, review_drawdown,
)

DEFAULT_MAPPING = {
    "risk_per_trade": "default_risk_per_trade_fraction",
    "max_daily_loss": "default_max_daily_loss_fraction",
    "max_open_positions": "default_max_open_positions",
    "max_leverage": "default_max_leverage",
    "max_correlated_exposure": "default_max_correlated_exposure_fraction",
    "auto_risk_pct": "auto_risk_fraction",
    "auto_max_notional_pct": "auto_max_notional_fraction",
    "auto_leverage": "auto_leverage",
    "auto_max_positions": "auto_max_positions",
}
DEFAULT_OFF_KEYS = {
    "enable_testnet_execution": "ENABLE_TESTNET_EXECUTION",
    "enable_autonomous_testnet": "ENABLE_AUTONOMOUS_TESTNET",
    "auto_start_autonomous": "AUTO_START_AUTONOMOUS",
}


def settings_source_defaults(source: str) -> dict[str, object]:
    module = ast.parse(source)
    cls = next((node for node in module.body if isinstance(node, ast.ClassDef)
                and node.name == "Settings"), None)
    if cls is None:
        raise ValueError("MISSING_QUANT_SETTINGS")
    values = {}
    for node in cls.body:
        if not isinstance(node, ast.AnnAssign) or not isinstance(node.target, ast.Name):
            continue
        name = node.target.id
        if name in DEFAULT_MAPPING:
            call = node.value
            if (not isinstance(call, ast.Call)
                    or not isinstance(call.func, ast.Name)
                    or call.func.id not in ("float", "int")
                    or len(call.args) != 1
                    or not isinstance(call.args[0], ast.Call)):
                raise ValueError("UNREVIEWED_QUANT_RISK_DEFAULT_SYNTAX")
            getenv = call.args[0]
            if (not isinstance(getenv.func, ast.Attribute)
                    or not isinstance(getenv.func.value, ast.Name)
                    or getenv.func.value.id != "os"
                    or getenv.func.attr != "getenv"
                    or len(getenv.args) != 2
                    or not all(isinstance(arg, ast.Constant)
                               and isinstance(arg.value, str) for arg in getenv.args)):
                raise ValueError("NONSTATIC_QUANT_RISK_DEFAULT")
            values[name] = int(getenv.args[1].value) if call.func.id == "int" else float(getenv.args[1].value)
        if name in DEFAULT_OFF_KEYS:
            call = node.value
            if (not isinstance(call, ast.Call) or not isinstance(call.func, ast.Name)
                    or call.func.id != "b" or len(call.args) not in (1, 2)
                    or not isinstance(call.args[0], ast.Constant)
                    or call.args[0].value != DEFAULT_OFF_KEYS[name]
                    or (len(call.args) == 2 and (
                        not isinstance(call.args[1], ast.Constant)
                        or str(call.args[1].value).lower() != "false"))):
                raise ValueError("UNREVIEWED_TESTNET_ENABLE_DEFAULT")
            values[name] = False
    if set(values) != set(DEFAULT_MAPPING) | set(DEFAULT_OFF_KEYS):
        raise ValueError("MISSING_QUANT_RISK_DEFAULT")
    return values


def _approx(a: object, b: object) -> bool:
    return (type(a) in (int, float) and type(b) in (int, float)
            and abs(float(a) - float(b)) <= 1e-7)


def review(root: Path = ROOT) -> dict:
    result = {
        "schema_version": 1,
        "paper_fixture_reference_parity": False,
        "quant_code_default_alignment": False,
        "budget_vectors_verified": 0,
        "drawdown_vectors_verified": 0,
        "python_quant_testnet_runtime_policy_parity": False,
        "python_quant_testnet_persistent_drawdown_latch_verified": False,
        "python_java_trading_account_state_shared": False,
        "python_quant_live_money_authorized": False,
        "python_quant_testnet_order_authorized_by_this_contract": False,
        "java_paper_exchange_order_authorized": False,
        "production_authorized": False,
        "decision": "NO_GO",
        "known_unresolved_divergence": [
            "Python Quant auto_risk_pct and auto_max_notional_pct have distinct testnet semantics",
            "Python Quant has no verified durable Java-equivalent peak drawdown latch",
            "Java paper DB and Python Quant account/position state are not synchronized",
            "Config and policy may be overridden; defaults are not runtime reconciliation",
        ],
    }
    try:
        contract = load_contract(root / "risk-contracts/stage46-paper-parity-v1.json")
        if not all(v is False for v in contract["release_authorization"].values()):
            return result
        defaults = settings_source_defaults(
            (root / "aetheris-quant/app/config.py").read_text(encoding="utf-8"))
        reviewed = contract["quant_settings_review"]
        for key, reference in DEFAULT_MAPPING.items():
            if not _approx(defaults[key], reviewed[reference]):
                return result
        if any(defaults[key] is not False for key in DEFAULT_OFF_KEYS):
            return result
        # Only SOURCE DEFAULTS agree. Existing runtime/testnet logic is
        # independent and not silently wired to these fixtures.
        auto_src = (root / "aetheris-quant/app/autonomous.py").read_text(encoding="utf-8")
        paper_src = (root / "orchestrator-service/src/main/java/io/aetheris/orchestrator/trading/PaperCapitalBudgetService.java").read_text(encoding="utf-8")
        if ("market_order(" not in auto_src or "auto_risk_pct" not in auto_src
                or "PaperCapitalBudgetService.assess" not in
                    (root / "orchestrator-service/src/main/java/io/aetheris/orchestrator/trading/PaperRiskOfficerService.java").read_text(encoding="utf-8")
                or "PAPER_ONLY" not in paper_src
                or "paper_risk_contract" in auto_src):
            return result
        result["quant_code_default_alignment"] = True
        for case in contract["budget_cases"]:
            outcome = review_budget(contract["policy"], case["input"])
            expected = case["expected"]
            if outcome["allowed"] is not expected["allowed"]:
                return result
            if not all(_approx(outcome[k], v) for k, v in expected.items()
                       if k != "allowed"):
                return result
            if outcome["testnet_order_authorized"] or outcome["live_order_authorized"]:
                return result
            result["budget_vectors_verified"] += 1
        for case in contract["drawdown_cases"]:
            data = {k: v for k, v in case.items() if k not in ("id", "expected")}
            outcome = review_drawdown(contract["policy"], data)
            expected = case["expected"]
            if (outcome["halted"] is not expected["halted"] or
                    not all(_approx(outcome[k], v) for k, v in expected.items()
                            if k != "halted")):
                return result
            result["drawdown_vectors_verified"] += 1
        result["paper_fixture_reference_parity"] = (
            result["budget_vectors_verified"] == len(contract["budget_cases"])
            and result["drawdown_vectors_verified"] == len(contract["drawdown_cases"]))
        return result
    except (OSError, ValueError, TypeError, KeyError):
        return result


def main():
    verdict = review()
    print(json.dumps(verdict, sort_keys=True, indent=2))
    if not (verdict["paper_fixture_reference_parity"]
            and verdict["quant_code_default_alignment"]
            and verdict["decision"] == "NO_GO"
            and verdict["python_quant_testnet_runtime_policy_parity"] is False):
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
