#!/usr/bin/env python3
"""Stage 46.19 — separate hosted Windows runner, same-vendor build hash audit.

Two isolated GitHub Actions jobs compile a fixed inert C++ fixture. Bytewise
equality is evidence of *two runner outputs* only. The job tokens, images,
toolchains, source checkout and workflow are all controlled by the same
GitHub/vendor trust domain: no independent attestor, signer, hardened worker
distribution or release approval is implied.
"""
from __future__ import annotations

import argparse
import json
import os
import re

_SHA64 = re.compile(r"^[0-9a-f]{64}$")
_COMMIT = re.compile(r"^[0-9a-f]{40}$")
_FIELDS = frozenset({
    "schema_version", "fixture_kind", "build_a", "build_b", "expected_head_sha",
    "signed_release_approved", "owner_pc_pilot_authorized", "production_authorized",
})
_BUILDER_FIELDS = frozenset({
    "runner_role", "artifact_sha256", "source_sha256", "authenticode_status",
    "checkout_head_sha", "builder_status",
})


def review(receipt: object) -> dict[str, object]:
    result: dict[str, object] = {
        "schema_version": 1,
        "cross_runner_hashes_verified": False,
        "runner_count": 0,
        "same_source_revision_verified": False,
        "separate_trust_domains_verified": False,
        "reproducible_build_independent_attestation": False,
        "trusted_signing_certificate_approved": False,
        "release_binary_authorized": False,
        "owner_pc_pilot_authorized": False,
        "production_authorized": False,
        "decision": "NO_GO",
    }
    if not isinstance(receipt, dict) or set(receipt) != _FIELDS:
        return result
    if (type(receipt["schema_version"]) is not int or receipt["schema_version"] != 1
        or receipt["fixture_kind"] != "INERT_UNSIGNED_TWO_HOSTED_RUNNERS"
        or receipt["signed_release_approved"] is not False
        or receipt["owner_pc_pilot_authorized"] is not False
        or receipt["production_authorized"] is not False
        or not isinstance(receipt["expected_head_sha"], str)
        or _COMMIT.fullmatch(receipt["expected_head_sha"]) is None):
        return result
    a, b = receipt["build_a"], receipt["build_b"]
    if not isinstance(a, dict) or not isinstance(b, dict):
        return result
    if set(a) != _BUILDER_FIELDS or set(b) != _BUILDER_FIELDS:
        return result
    for builder, role in ((a, "A"), (b, "B")):
        if (builder["runner_role"] != role
            or builder["builder_status"] != "PASSED_FIXED_SELFTEST"
            or builder["authenticode_status"] != "NotSigned"
            or any(not isinstance(builder[k], str) or _SHA64.fullmatch(builder[k]) is None
                   for k in ("artifact_sha256", "source_sha256"))
            or builder["checkout_head_sha"] != receipt["expected_head_sha"]):
            return result
    if a["source_sha256"] != b["source_sha256"]:
        return result
    result["same_source_revision_verified"] = True
    result["runner_count"] = 2
    result["cross_runner_hashes_verified"] = a["artifact_sha256"] == b["artifact_sha256"]
    # A pair of GitHub-hosted Windows runners is not independent supplier
    # provenance or secure signing, regardless of identical bytes.
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--from-github-actions-env", action="store_true", required=True)
    args = parser.parse_args()
    if not args.from_github_actions_env:
        return 1
    e = os.environ
    receipt = {
        "schema_version": 1,
        "fixture_kind": "INERT_UNSIGNED_TWO_HOSTED_RUNNERS",
        "expected_head_sha": e.get("EXACT_PR_SHA", ""),
        "signed_release_approved": False,
        "owner_pc_pilot_authorized": False,
        "production_authorized": False,
        "build_a": {
            "runner_role": "A",
            "artifact_sha256": e.get("WIN_A_SHA256", ""),
            "source_sha256": e.get("WIN_A_SOURCE_SHA256", ""),
            "authenticode_status": e.get("WIN_A_SIGNATURE", ""),
            "checkout_head_sha": e.get("EXACT_PR_SHA", ""),
            "builder_status": "PASSED_FIXED_SELFTEST",
        },
        "build_b": {
            "runner_role": "B",
            "artifact_sha256": e.get("WIN_B_SHA256", ""),
            "source_sha256": e.get("WIN_B_SOURCE_SHA256", ""),
            "authenticode_status": e.get("WIN_B_SIGNATURE", ""),
            "checkout_head_sha": e.get("EXACT_PR_SHA", ""),
            "builder_status": "PASSED_FIXED_SELFTEST",
        },
    }
    result = review(receipt)
    print(json.dumps(result, sort_keys=True, indent=2))
    return 0 if result["cross_runner_hashes_verified"] and result["decision"] == "NO_GO" else 1


if __name__ == "__main__":
    raise SystemExit(main())
