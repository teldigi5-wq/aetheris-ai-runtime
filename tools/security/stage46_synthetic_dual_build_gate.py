#!/usr/bin/env python3
"""Stage 46.18 — same-run double-build consistency of an UNSIGNED lab fixture.

Equal digests in one Windows runner are NOT independently reproducible builds,
reviewed source provenance, trusted publisher signing or anti-TOCTOU runtime
identity. This script can NEVER authorize an installed or production worker.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

HEX = re.compile(r"^[0-9a-f]{64}$")
FIELDS = frozenset({
    "schema_version", "fixture_kind",
    "build_a_sha256", "build_b_sha256", "mutated_copy_sha256",
    "build_a_exit_code", "build_b_exit_code", "self_test_exit_code",
    "authenticode_status", "approved_publisher_certificate_sha256",
    "independently_approved_artifact_sha256", "signed_release_manifest",
})
BASE = {
    "schema_version": 1,
    "same_runner_dual_build_verified": False,
    "tamper_copy_rejected": False,
    "fixture_artifact_type": "UNVERIFIED",
    "publisher_signature_approved": False,
    "independent_build_attestation_verified": False,
    "immutable_verified_launch_verified": False,
    "worker_install_authorized": False,
    "microphone_authorized": False,
    "production_authorized": False,
    "owner_pc_pilot_authorized": False,
    "decision": "NO_GO",
}


def review(receipt: object) -> dict[str, object]:
    result = dict(BASE)
    if not isinstance(receipt, dict) or set(receipt) != FIELDS:
        return result
    if (type(receipt["schema_version"]) is not int
        or receipt["schema_version"] != 1
        or receipt["fixture_kind"] != "FIXED_UNSIGNED_SYNTHETIC_CI_ONLY"
        or any(type(receipt[key]) is not int or receipt[key] != 0 for key in (
            "build_a_exit_code", "build_b_exit_code", "self_test_exit_code"
        ))
        or any(not isinstance(receipt[key], str) or HEX.fullmatch(receipt[key]) is None
               for key in ("build_a_sha256", "build_b_sha256", "mutated_copy_sha256"))
        or receipt["authenticode_status"] != "NotSigned"
        or receipt["approved_publisher_certificate_sha256"] is not None
        or receipt["independently_approved_artifact_sha256"] is not None
        or receipt["signed_release_manifest"] is not False):
        return result
    first, second, changed = (
        receipt["build_a_sha256"], receipt["build_b_sha256"],
        receipt["mutated_copy_sha256"],
    )
    result["tamper_copy_rejected"] = changed != first
    result["same_runner_dual_build_verified"] = first == second and changed != first
    if result["same_runner_dual_build_verified"]:
        result["fixture_artifact_type"] = "UNSIGNED_SAME_RUN_IDENTICAL_HASHES_ONLY"
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--receipt", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        if args.receipt.stat().st_size > 4_000:
            raise ValueError("OVERSIZED_RECEIPT")
        data = json.loads(args.receipt.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, ValueError):
        data = None
    result = review(data)
    rendered = json.dumps(result, sort_keys=True, indent=2) + "\n"
    print(rendered, end="")
    if args.output:
        args.output.write_text(rendered, encoding="utf-8")
    return 0 if result["same_runner_dual_build_verified"] and result["decision"] == "NO_GO" else 1


if __name__ == "__main__":
    raise SystemExit(main())
