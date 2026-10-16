"""Stage 46.29 — pure, read-only PAPER risk-parity evaluator.

This DOES NOT connect to the Python Quant testnet execution loop, grant order
authority, change its config, or provide any real-money loss guarantee.
It is a deterministic counterpart to the *existing* Java paper-policy model,
used only with bounded, fixed synthetic fixtures in cross-language CI.
"""
from __future__ import annotations

import json
import math
from pathlib import Path

_CONTRACT_KEYS = frozenset({
    "schema_version", "id", "scope", "decision", "release_authorization",
    "policy", "quant_settings_review", "budget_cases", "drawdown_cases",
})
_POLICY_KEYS = frozenset({
    "risk_per_trade_fraction", "max_daily_loss_fraction", "max_open_positions",
    "max_leverage", "min_reward_risk", "max_stop_fraction",
    "max_gross_exposure_multiple", "max_symbol_exposure_fraction",
    "max_correlated_fraction_of_gross", "max_peak_to_trough_drawdown_fraction",
})
_AUTH_KEYS = frozenset({
    "python_quant_testnet_orders_authorized",
    "java_paper_exchange_orders_authorized",
    "live_money_orders_authorized",
    "production_pilot_authorized",
    "protected_branch_merge_authorized",
})
_INPUT_KEYS = frozenset({
    "symbol", "side", "starting_balance", "cash_balance", "daily_realized_pnl",
    "entry_price", "fill_price", "stop_loss", "recommended_notional",
    "signal_risk_pct", "leverage", "reward_risk", "accepted", "open_positions",
})
_OPEN_KEYS = frozenset({
    "symbol", "side", "entry_price", "stop_loss", "quantity", "notional",
})


def _finite_number(value, *, positive=False):
    return (
        type(value) in (int, float)
        and math.isfinite(value)
        and (not positive or value > 0)
    )


def _fail():
    return {
        "allowed": False, "approved_notional": 0.0,
        "estimated_loss_at_stop": 0.0,
        "remaining_daily_loss_budget": 0.0,
        "projected_gross_exposure": 0.0,
        "projected_correlated_exposure": 0.0,
        "execution_scope": "PAPER_PARITY_FIXTURE_ONLY",
        "testnet_order_authorized": False, "live_order_authorized": False,
    }


def _symbol_group(symbol):
    if not isinstance(symbol, str) or not symbol:
        return ""
    symbol = symbol.upper()
    for suffix in ("USDT", "USDC", "USD", "EUR", "BTC"):
        if symbol.endswith(suffix) and len(symbol) > len(suffix):
            return symbol[:-len(suffix)]
    return symbol


def review_budget(policy, data):
    """Return a test-only sizing quote; never a broker execution decision."""
    out = _fail()
    if (not isinstance(policy, dict) or set(policy) != _POLICY_KEYS
            or not isinstance(data, dict) or set(data) != _INPUT_KEYS):
        return out
    if not all(_finite_number(policy[k], positive=True) for k in _POLICY_KEYS):
        return out
    if type(policy["max_open_positions"]) is not int or type(policy["max_leverage"]) is not int:
        return out
    if (policy["max_open_positions"] > 3 or policy["max_leverage"] > 3
            or policy["risk_per_trade_fraction"] > .005
            or policy["max_daily_loss_fraction"] > .02
            or policy["max_peak_to_trough_drawdown_fraction"] > .08):
        return out
    for name in ("starting_balance", "cash_balance", "entry_price", "fill_price",
                 "stop_loss", "recommended_notional", "signal_risk_pct",
                 "reward_risk"):
        if not _finite_number(data[name], positive=True):
            return out
    if not _finite_number(data["daily_realized_pnl"]):
        return out
    if (type(data["leverage"]) is not int or data["leverage"] < 1
            or data["leverage"] > policy["max_leverage"]
            or data["accepted"] is not True
            or data["side"] not in ("LONG", "SHORT")
            or not isinstance(data["symbol"], str) or not data["symbol"]
            or not isinstance(data["open_positions"], list)
            or len(data["open_positions"]) >= policy["max_open_positions"]
            or data["reward_risk"] < policy["min_reward_risk"]):
        return out

    fill, entry, stop = data["fill_price"], data["entry_price"], data["stop_loss"]
    if ((data["side"] == "LONG" and stop >= fill)
            or (data["side"] == "SHORT" and stop <= fill)
            or abs(fill - entry) / entry > .05):
        return out
    stop_fraction = abs(fill - stop) / fill
    if stop_fraction <= 0 or stop_fraction > min(.15, policy["max_stop_fraction"]):
        return out

    gross = correlated = same_symbol = existing_stop_loss = 0.0
    group = _symbol_group(data["symbol"])
    for p in data["open_positions"]:
        if not isinstance(p, dict) or set(p) != _OPEN_KEYS:
            return out
        if (p["side"] not in ("LONG", "SHORT")
                or not isinstance(p["symbol"], str) or not p["symbol"]
                or any(not _finite_number(p[k], positive=True) for k in
                       ("entry_price", "stop_loss", "quantity", "notional"))):
            return out
        gross += p["notional"]
        existing_stop_loss += max(
            0, p["entry_price"] - p["stop_loss"]
            if p["side"] == "LONG" else p["stop_loss"] - p["entry_price"]
        ) * p["quantity"]
        if p["symbol"].upper() == data["symbol"].upper():
            same_symbol += p["notional"]
        if _symbol_group(p["symbol"]) == group:
            correlated += p["notional"]
    if not all(math.isfinite(v) for v in
               (gross, correlated, same_symbol, existing_stop_loss)):
        return out

    cash = data["cash_balance"]
    remaining_daily = (
        data["starting_balance"] * policy["max_daily_loss_fraction"]
        - max(0.0, -data["daily_realized_pnl"]) - existing_stop_loss
    )
    risk_cash = cash * min(policy["risk_per_trade_fraction"],
                           data["signal_risk_pct"] / 100.0)
    gross_capacity = cash * policy["max_gross_exposure_multiple"] - gross
    symbol_capacity = cash * policy["max_symbol_exposure_fraction"] - same_symbol
    correlated_capacity = (
        cash * policy["max_gross_exposure_multiple"]
        * policy["max_correlated_fraction_of_gross"] - correlated
    )
    available = (remaining_daily, risk_cash, gross_capacity,
                 symbol_capacity, correlated_capacity)
    if not all(_finite_number(x, positive=True) for x in available):
        return out
    notional = min(
        data["recommended_notional"], cash * data["leverage"],
        min(risk_cash, remaining_daily) / stop_fraction,
        gross_capacity, symbol_capacity, correlated_capacity,
    )
    stop_loss_amount = notional * stop_fraction
    if not _finite_number(notional, positive=True) or not math.isfinite(stop_loss_amount):
        return out
    return {
        **out, "allowed": True, "approved_notional": notional,
        "estimated_loss_at_stop": stop_loss_amount,
        "remaining_daily_loss_budget": remaining_daily,
        "projected_gross_exposure": gross + notional,
        "projected_correlated_exposure": correlated + notional,
    }


def review_drawdown(policy, data):
    out = {"halted": True, "peak_equity": 0.0,
           "max_drawdown_pct": 0.0,
           "execution_scope": "PAPER_PARITY_FIXTURE_ONLY",
           "testnet_order_authorized": False,
           "live_order_authorized": False}
    if (not isinstance(policy, dict)
            or set(policy) != _POLICY_KEYS
            or not isinstance(data, dict)
            or set(data) != {
                "previous_peak", "previous_equity", "previous_max_drawdown_pct",
                "previous_halted", "observed_equity",
            }):
        return out
    if not all(_finite_number(data[k], positive=True) for k in
               ("previous_peak", "previous_equity", "observed_equity")):
        return out
    old_max = data["previous_max_drawdown_pct"]
    if (not _finite_number(old_max) or not 0 <= old_max <= 100
            or type(data["previous_halted"]) is not bool
            or data["previous_equity"] > data["previous_peak"] + 1e-7):
        return out
    peak = max(data["previous_peak"], data["observed_equity"])
    current = 100.0 * (peak - data["observed_equity"]) / peak
    if not math.isfinite(current):
        return out
    return {
        **out, "halted": data["previous_halted"] or current >= 8.0 - 1e-10,
        "peak_equity": peak, "max_drawdown_pct": max(old_max, current),
    }


def _no_duplicate_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("DUPLICATE_RISK_CONTRACT_KEY")
        result[key] = value
    return result


def load_contract(path):
    path = Path(path)
    if path.is_symlink() or not path.is_file() or path.stat().st_size > 50_000:
        raise ValueError("MISSING_OR_OVERSIZED_RISK_CONTRACT")
    contract = json.loads(
        path.read_text(encoding="utf-8"),
        object_pairs_hook=_no_duplicate_keys,
        parse_constant=lambda value: (_ for _ in ()).throw(
            ValueError("NON_FINITE_CONTRACT_VALUE")),
    )
    if not isinstance(contract, dict) or set(contract) != _CONTRACT_KEYS:
        raise ValueError("UNREVIEWED_RISK_CONTRACT_SCHEMA")
    if (type(contract["schema_version"]) is not int
            or contract["schema_version"] != 1
            or contract["id"] != "syntra-paper-risk-contract-v1"
            or contract["scope"] != "CROSS_LANGUAGE_PAPER_PARITY_TEST_ONLY"
            or contract["decision"] != "NO_GO"
            or not isinstance(contract["release_authorization"], dict)
            or set(contract["release_authorization"]) != _AUTH_KEYS
            or any(value is not False for value in
                   contract["release_authorization"].values())
            or not isinstance(contract["quant_settings_review"], dict)
            or contract["quant_settings_review"].get(
                "execution_policy_parity_verified") is not False
            or contract["quant_settings_review"].get(
                "quant_testnet_peak_drawdown_latch_verified") is not False
            or contract["quant_settings_review"].get(
                "java_python_state_store_shared") is not False
            or not isinstance(contract["budget_cases"], list)
            or len(contract["budget_cases"]) < 5
            or not isinstance(contract["drawdown_cases"], list)
            or len(contract["drawdown_cases"]) < 3):
        raise ValueError("UNAUTHORIZED_OR_UNVERIFIED_RISK_CONTRACT")
    p = contract["policy"]
    if (not isinstance(p, dict) or set(p) != _POLICY_KEYS
            or type(p["max_open_positions"]) is not int or p["max_open_positions"] != 3
            or type(p["max_leverage"]) is not int or p["max_leverage"] != 3
            or any(not _finite_number(v, positive=True) for v in p.values())
            or abs(p["risk_per_trade_fraction"] - .005) > 1e-12
            or abs(p["max_daily_loss_fraction"] - .02) > 1e-12
            or abs(p["max_peak_to_trough_drawdown_fraction"] - .08) > 1e-12):
        raise ValueError("UNEXPECTED_POLICY_INCREASE")
    return contract
