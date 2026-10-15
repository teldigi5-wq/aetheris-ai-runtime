#!/usr/bin/env python3
"""Stage 46.18 — rigorous correlation on *synthetic fixture* WFP records only.

NEVER accepts WFP kernel events as authenticated, never subscribes to events,
modifies firewall settings or authorizes network access. A matching synthetic
record is a parser/contract test, not evidence that Windows blocked anything.
"""
from __future__ import annotations

import argparse
import ipaddress
import json
import re
from pathlib import Path

_SHA256 = re.compile(r"^[a-f0-9]{64}$")
_ATTEMPT_FIELDS = frozenset({
    "app_id_sha256", "destination_ip", "destination_port", "ip_version",
    "protocol", "direction", "start_ms", "end_ms",
    "expected_filter_id", "expected_layer_id",
})
_EVENT_FIELDS = frozenset({
    "event_id", "app_id_sha256", "destination_ip", "destination_port",
    "ip_version", "protocol", "direction", "timestamp_ms",
    "filter_id", "layer_id", "event_type", "event_source",
})
_RESULT = {
    "schema_version": 1,
    "contract_valid": False,
    "synthetic_match_count": 0,
    "synthetic_correlation": "REJECTED",
    "real_kernel_event_provenance_verified": False,
    "real_app_specific_wfp_drop_verified": False,
    "network_egress_certified": False,
    "worker_install_authorized": False,
    "owner_pc_pilot_authorized": False,
    "production_authorized": False,
    "decision": "NO_GO",
}


def _int_in(value: object, low: int, high: int) -> bool:
    return type(value) is int and low <= value <= high


def _valid_network_record(record: object, *, event: bool) -> bool:
    keys = _EVENT_FIELDS if event else _ATTEMPT_FIELDS
    if not isinstance(record, dict) or set(record) != keys:
        return False
    digest = record["app_id_sha256"]
    if not isinstance(digest, str) or _SHA256.fullmatch(digest) is None:
        return False
    version = record["ip_version"]
    if not _int_in(version, 4, 6) or version not in (4, 6):
        return False
    address = record["destination_ip"]
    if not isinstance(address, str) or len(address) > 45:
        return False
    try:
        ip = ipaddress.ip_address(address)
        if ip.version != version or not ip.is_loopback or str(ip) != address:
            return False
    except ValueError:
        return False
    if (not _int_in(record["destination_port"], 1, 65535)
        or record["protocol"] != "TCP" or record["direction"] != "OUTBOUND"):
        return False
    if event:
        return (
            isinstance(record["event_id"], str)
            and re.fullmatch(r"fixture-[a-z0-9]{1,24}", record["event_id"]) is not None
            and _int_in(record["timestamp_ms"], 0, 3_600_000)
            and _int_in(record["filter_id"], 1, (1 << 53) - 1)
            and _int_in(record["layer_id"], 1, 65535)
            and record["event_type"] == "CLASSIFY_DROP"
            and record["event_source"] == "SYNTHETIC_FIXTURE_ONLY"
        )
    return (
        _int_in(record["start_ms"], 0, 3_600_000)
        and _int_in(record["end_ms"], record["start_ms"], 3_600_000)
        and 1 <= record["end_ms"] - record["start_ms"] <= 5_000
        and _int_in(record["expected_filter_id"], 1, (1 << 53) - 1)
        and _int_in(record["expected_layer_id"], 1, 65535)
    )


def review(receipt: object) -> dict[str, object]:
    result = dict(_RESULT)
    if not isinstance(receipt, dict) or set(receipt) != {
        "schema_version", "fixture_kind", "clock_kind",
        "positive_control_connected", "kernel_event_attested",
        "attempt", "candidate_events",
    }:
        return result
    if (type(receipt["schema_version"]) is not int
        or receipt["schema_version"] != 1
        or receipt["fixture_kind"] != "SYNTHETIC_NO_REAL_WFP_DATA"
        or receipt["clock_kind"] != "SYNTHETIC_RELATIVE_MS"
        or receipt["positive_control_connected"] is not True
        or receipt["kernel_event_attested"] is not False):
        return result
    attempt = receipt["attempt"]
    events = receipt["candidate_events"]
    if not _valid_network_record(attempt, event=False):
        return result
    if not isinstance(events, list) or len(events) > 16:
        return result
    if any(not _valid_network_record(item, event=True) for item in events):
        return result
    ids = [item["event_id"] for item in events]
    if len(ids) != len(set(ids)):
        return result

    matched = sum(
        event["app_id_sha256"] == attempt["app_id_sha256"]
        and event["destination_ip"] == attempt["destination_ip"]
        and event["destination_port"] == attempt["destination_port"]
        and event["ip_version"] == attempt["ip_version"]
        and event["protocol"] == attempt["protocol"]
        and event["direction"] == attempt["direction"]
        and attempt["start_ms"] <= event["timestamp_ms"] <= attempt["end_ms"]
        and event["filter_id"] == attempt["expected_filter_id"]
        and event["layer_id"] == attempt["expected_layer_id"]
        for event in events
    )
    result["contract_valid"] = True
    result["synthetic_match_count"] = matched
    result["synthetic_correlation"] = (
        "EXACTLY_ONE_SYNTHETIC_MATCH" if matched == 1
        else "NO_MATCH" if matched == 0 else "AMBIGUOUS_MULTIPLE_MATCHES"
    )
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--receipt", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        if args.receipt.stat().st_size > 20_000:
            raise ValueError("OVERSIZED_SYNTHETIC_RECEIPT")
        receipt = json.loads(args.receipt.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, ValueError):
        receipt = None
    result = review(receipt)
    serialized = json.dumps(result, sort_keys=True, indent=2) + "\n"
    print(serialized, end="")
    if args.output:
        args.output.write_text(serialized, encoding="utf-8")
    return 0 if result["contract_valid"] and result["decision"] == "NO_GO" else 1


if __name__ == "__main__":
    raise SystemExit(main())
