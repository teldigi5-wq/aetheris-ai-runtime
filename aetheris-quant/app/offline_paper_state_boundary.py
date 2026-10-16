"""Stage 46.30: offline, test-only state reconciliation boundary.

No network, environment/secret loading, broker, file writes or activation path.
HMAC keys MUST come from an external trusted runtime in a future approved
integration; a key shared by synthetic tests is never production identity.
Replay protection here is IN MEMORY ONLY and not safe across process restart.
"""
from __future__ import annotations

import hashlib
import hmac
import re

DOMAIN = b"AETHERIS_STAGE46_30_INERT_PAPER_MAC\x00"
PREFIX = "AETHERIS_STAGE46_30_PAPER_STATE_V1\n"
HEX = re.compile(r"^[0-9a-f]{64}$")
IDENT = re.compile(r"^[a-z][a-z0-9-]{0,39}$")
SYMBOL = re.compile(r"^[A-Z0-9]{3,20}$")
FIELDS = frozenset({
    "schema_version", "origin", "account_id", "currency", "sequence",
    "issued_ms", "expires_ms", "previous_digest", "cash_minor",
    "equity_minor", "daily_realized_minor", "halted", "positions",
})
POSITION_FIELDS = frozenset({
    "id", "symbol", "side", "entry_minor", "stop_minor",
    "quantity_microunits", "notional_minor",
})
GENESIS_DIGEST = "0" * 64


class InvalidSnapshot(ValueError):
    pass


def _int(value, *, positive=False):
    return type(value) is int and (value > 0 if positive else value >= 0) and value <= 10**15


def canonical(snapshot):
    """One strictly validated cross-language ASCII wire representation."""
    if not isinstance(snapshot, dict) or set(snapshot) != FIELDS:
        raise InvalidSnapshot("BAD_STATE_SCHEMA")
    if (snapshot["schema_version"] != 1 or type(snapshot["schema_version"]) is not int
            or snapshot["origin"] != "JAVA_PAPER_SYNTHETIC_ONLY"
            or snapshot["account_id"] != "paper-default"
            or snapshot["currency"] != "USD"
            or not _int(snapshot["sequence"], positive=True)
            or not _int(snapshot["issued_ms"], positive=True)
            or not _int(snapshot["expires_ms"], positive=True)
            or not isinstance(snapshot["previous_digest"], str)
            or not HEX.fullmatch(snapshot["previous_digest"])
            or not _int(snapshot["cash_minor"], positive=True)
            or not _int(snapshot["equity_minor"], positive=True)
            or type(snapshot["daily_realized_minor"]) is not int
            or abs(snapshot["daily_realized_minor"]) > 10**15
            or type(snapshot["halted"]) is not bool
            or not isinstance(snapshot["positions"], list)
            or len(snapshot["positions"]) > 16):
        raise InvalidSnapshot("BAD_STATE_VALUES")
    if (snapshot["expires_ms"] <= snapshot["issued_ms"]
            or snapshot["expires_ms"] - snapshot["issued_ms"] > 120000):
        raise InvalidSnapshot("BAD_STATE_VALIDITY_WINDOW")

    lines = [PREFIX]
    for field in ("schema_version", "origin", "account_id", "currency",
                  "sequence", "issued_ms", "expires_ms", "previous_digest",
                  "cash_minor", "equity_minor", "daily_realized_minor", "halted"):
        value = snapshot[field]
        if type(value) is bool:
            value = "true" if value else "false"
        lines.append(f"{field}={value}\n")
    lines.append(f"positions={len(snapshot['positions'])}\n")
    previous_id = ""
    for position in snapshot["positions"]:
        if not isinstance(position, dict) or set(position) != POSITION_FIELDS:
            raise InvalidSnapshot("BAD_POSITION_SCHEMA")
        ident, symbol, side = position["id"], position["symbol"], position["side"]
        if (not isinstance(ident, str) or not IDENT.fullmatch(ident)
                or ident <= previous_id
                or not isinstance(symbol, str) or not SYMBOL.fullmatch(symbol)
                or side not in ("LONG", "SHORT")):
            raise InvalidSnapshot("BAD_POSITION_IDENTITY_OR_ORDER")
        previous_id = ident
        for key in ("entry_minor", "stop_minor", "quantity_microunits", "notional_minor"):
            if not _int(position[key], positive=True):
                raise InvalidSnapshot("BAD_POSITION_AMOUNTS")
        entry, stop, qty = (position[k] for k in
                            ("entry_minor", "stop_minor", "quantity_microunits"))
        if (side == "LONG" and stop >= entry
                or side == "SHORT" and stop <= entry
                or entry * qty != position["notional_minor"] * 1000000):
            raise InvalidSnapshot("INVALID_STOP_OR_NOTIONAL")
        lines.append(
            f"position={ident}|{symbol}|{side}|{entry}|{stop}|{qty}|{position['notional_minor']}\n"
        )
    encoded = "".join(lines).encode("ascii")
    if len(encoded) > 4096:
        raise InvalidSnapshot("STATE_TOO_LARGE")
    return encoded


def digest(snapshot):
    return hashlib.sha256(canonical(snapshot)).hexdigest()


def mac(snapshot, secret):
    if not isinstance(secret, bytes) or len(secret) < 32:
        raise InvalidSnapshot("MISSING_AUTHENTICATION_KEY")
    return hmac.new(secret, DOMAIN + canonical(snapshot), hashlib.sha256).hexdigest()


class OfflinePaperReconciler:
    """Memory-only check; NOT an owner authentication or actual state import."""

    def __init__(self, secret):
        if not isinstance(secret, bytes) or len(secret) < 32:
            raise InvalidSnapshot("MISSING_AUTHENTICATION_KEY")
        self.__secret = secret
        self.__sequence = 0
        self.__digest = GENESIS_DIGEST

    def inspect(self, snapshot, tag, now_ms):
        result = {
            "accepted": False, "reason": "UNVERIFIED",
            "sequence": self.__sequence,
            "test_only": True, "snapshot_state_applied": False,
            "python_quant_testnet_orders_authorized": False,
            "live_money_orders_authorized": False,
            "production_authorized": False, "decision": "NO_GO",
        }
        try:
            body = canonical(snapshot)
            if type(now_ms) is not int or now_ms < 0 or now_ms > 10**15:
                raise InvalidSnapshot("BAD_VERIFICATION_CLOCK")
            if now_ms < snapshot["issued_ms"] or now_ms > snapshot["expires_ms"]:
                raise InvalidSnapshot("EXPIRED_OR_FUTURE_STATE")
            if not isinstance(tag, str) or not HEX.fullmatch(tag):
                raise InvalidSnapshot("INVALID_STATE_MAC_FORMAT")
            expected = hmac.new(self.__secret, DOMAIN + body, hashlib.sha256).hexdigest()
            if not hmac.compare_digest(tag, expected):
                raise InvalidSnapshot("UNAUTHENTICATED_STATE")
            if snapshot["sequence"] != self.__sequence + 1:
                raise InvalidSnapshot("REPLAY_OR_MISSING_SEQUENCE")
            if snapshot["previous_digest"] != self.__digest:
                raise InvalidSnapshot("STATE_CHAIN_DISCONTINUITY")
            # Only an in-memory proof, never mutate an actual account or broker.
            self.__digest = hashlib.sha256(body).hexdigest()
            self.__sequence = snapshot["sequence"]
            return {**result, "accepted": True, "reason": "SYNTHETIC_STATE_ONLY",
                    "sequence": self.__sequence}
        except (InvalidSnapshot, UnicodeError, OverflowError, TypeError) as error:
            return {**result, "reason": str(error)}
