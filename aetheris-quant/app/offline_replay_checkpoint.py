"""Stage46.32 synthetic ONLY public checkpoint parity checker.

It cannot read or write the Java DB, load credentials, authenticate a person,
recover a database or place testnet/live orders. CI fixture key is PUBLIC and
cannot establish an independent operational trust root.
"""
from __future__ import annotations

import hashlib
import hmac
import re

DOMAIN = b"AETHERIS_STAGE46_32_PUBLIC_CHECKPOINT_TEST\x00"
ORDER = (
    "schema_version", "scope", "account_id", "highest_sequence",
    "highest_digest", "issued_ms", "expires_ms", "decision",
)
FIELDS = frozenset(ORDER)
HEX = re.compile(r"[0-9a-f]{64}\Z")
MAX_NUMBER = 10**15


def _int(value, positive=False):
    return type(value) is int and (
        value > 0 if positive else value >= 0
    ) and value <= MAX_NUMBER


def canonical(checkpoint):
    if not isinstance(checkpoint, dict) or set(checkpoint) != FIELDS:
        raise ValueError("INVALID_CHECKPOINT_SCHEMA")
    if (
        not _int(checkpoint["schema_version"], True)
        or checkpoint["schema_version"] != 1
        or checkpoint["scope"] != "OFFLINE_SYNTHETIC_ROLLBACK_CHECKPOINT_ONLY"
        or checkpoint["account_id"] != "paper-default"
        or not _int(checkpoint["highest_sequence"], True)
        or not isinstance(checkpoint["highest_digest"], str)
        or not HEX.fullmatch(checkpoint["highest_digest"])
        or not _int(checkpoint["issued_ms"], True)
        or not _int(checkpoint["expires_ms"], True)
        or checkpoint["expires_ms"] <= checkpoint["issued_ms"]
        or checkpoint["expires_ms"] - checkpoint["issued_ms"] > 86_400_000
        or checkpoint["decision"] != "NO_GO"
    ):
        raise ValueError("INVALID_CHECKPOINT_VALUES")
    body = "".join(f"{key}={checkpoint[key]}\n" for key in ORDER).encode("ascii")
    if len(body) > 1024:
        raise ValueError("OVERSIZED_CHECKPOINT")
    return body


def fixture_mac(checkpoint, test_key):
    if not isinstance(test_key, bytes) or len(test_key) < 32:
        raise ValueError("MISSING_PUBLIC_TEST_KEY")
    return hmac.new(test_key, DOMAIN + canonical(checkpoint),
                    hashlib.sha256).hexdigest()


def compare(checkpoint, tag, test_key, now_ms, db_anchor):
    """Classify an inert supplied DB anchor; always refuse runtime authority."""
    result = {
        "classification": "ROLLBACK_REVIEW_FAILED_CLOSED",
        "public_fixture_tag_verified": False,
        "database_matches_presented_checkpoint": False,
        "independent_trust_root_verified": False,
        "account_state_imported": False,
        "python_quant_testnet_authorized": False,
        "live_money_authorized": False,
        "production_authorized": False,
        "decision": "NO_GO",
    }
    try:
        body = canonical(checkpoint)
        if (
            not _int(now_ms)
            or now_ms < checkpoint["issued_ms"]
            or now_ms > checkpoint["expires_ms"]
            or not isinstance(tag, str)
            or not HEX.fullmatch(tag)
            or len(body) > 1024
        ):
            return {**result, "classification": "INVALID_OR_EXPIRED_CHECKPOINT"}
        if not hmac.compare_digest(tag, fixture_mac(checkpoint, test_key)):
            return {**result, "classification": "CHECKPOINT_TAG_MISMATCH"}
        result = {**result, "public_fixture_tag_verified": True}
        if db_anchor is None:
            return {**result, "classification": "DB_ANCHOR_MISSING_OR_RESTORED"}
        if (
            not isinstance(db_anchor, dict)
            or set(db_anchor) != {"sequence", "digest"}
            or not _int(db_anchor["sequence"], True)
            or not isinstance(db_anchor["digest"], str)
            or not HEX.fullmatch(db_anchor["digest"])
        ):
            return {**result, "classification": "DB_ANCHOR_CORRUPTED"}
        a = db_anchor["sequence"]
        b = checkpoint["highest_sequence"]
        if a < b:
            return {**result, "classification": "STALE_DB_RESTORE_DETECTED"}
        if a > b:
            return {**result, "classification": "UNANCHORED_DB_ADVANCE"}
        if not hmac.compare_digest(db_anchor["digest"], checkpoint["highest_digest"]):
            return {**result, "classification": "EQUAL_SEQUENCE_DIFFERENT_DIGEST"}
        return {
            **result, "classification": "FIXTURE_CHECKPOINT_MATCH",
            "database_matches_presented_checkpoint": True,
        }
    except (ValueError, KeyError, TypeError, UnicodeError):
        return result
