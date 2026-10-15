#!/usr/bin/env python3
"""Stage 46.15: fail-closed test-lab evidence evaluator, NOT an authorization API.

The native Windows fixture emits observed markers. The evaluator enforces
bounded, consistent evidence, preserving the distinction between ACCESS_DENIED
and TIMEOUT. Checksums from the same CI run are tamper checks, NOT a
trusted developer signature. No result from this evaluator may authorize
actual speech, OS operations, PC pilots or production releases.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

HEX64 = re.compile(r"^[a-fA-F0-9]{64}$")
REQUIRED = frozenset({
    "STAGE46_14_KERNEL_JOB_LIMITS_READ_BACK",
    "STAGE46_14_REAL_APP_CONTAINER_TOKEN_VERIFIED",
    "STAGE46_14_PROTECTED_SYNTHETIC_FILE_ACCESS_DENIED",
    "STAGE46_14_APP_CONTAINER_STALL_JOB_KILLED",
    "STAGE46_15_LOOPBACK_POSITIVE_CONTROL_CONNECTED",
    "STAGE46_14_WINDOWS_APP_CONTAINER_SYNTHETIC_SECURITY_PASS",
})
TIMEOUT = "STAGE46_14_LOOPBACK_TIMEOUT_NETWORK_POLICY_INCONCLUSIVE"
EXPLICIT_DENY = "STAGE46_14_LOOPBACK_ACCESS_DENIED_BY_WINSOCK"
DENY_COMPLETION = "STAGE46_14_LOOPBACK_NOT_CONNECTED_NETWORK_EGRESS_NOT_CERTIFIED"
SUCCESS_COMPLETION = "STAGE46_14_WINDOWS_APP_CONTAINER_SYNTHETIC_SECURITY_PASS"


def review(evidence: object) -> dict[str, object]:
    """Only local lab evidence classification. Production is ALWAYS NO_GO."""
    output: dict[str, object] = {
        "schema_version": 1,
        "test_evidence_valid": False,
        "worker_integrity": "REJECTED",
        "worker_provenance": "UNVERIFIED",
        "network_probe": "INVALID",
        "network_egress_certified": False,
        "desktop_actions_authorized": False,
        "physical_owner_pc_pilot_authorized": False,
        "production_authorized": False,
        "decision": "NO_GO",
    }
    if not isinstance(evidence, dict):
        return output
    if set(evidence) != {
        "schema_version", "worker_sha256_before", "worker_sha256_after",
        "tampered_fixture_sha256", "authenticode_status",
        "appcontainer_lab_exit_code", "observed_markers",
    } or type(evidence.get("schema_version")) is not int or evidence["schema_version"] != 1:
        return output
    hashes = [evidence.get(key) for key in (
        "worker_sha256_before", "worker_sha256_after", "tampered_fixture_sha256")]
    if any(not isinstance(h, str) or not HEX64.fullmatch(h) for h in hashes):
        return output
    if not isinstance(evidence["authenticode_status"], str) or len(evidence["authenticode_status"]) > 64:
        return output
    if type(evidence["appcontainer_lab_exit_code"]) is not int:
        return output
    markers = evidence.get("observed_markers")
    if not isinstance(markers, list) or len(markers) > 100 or any(
            not isinstance(x, str) or len(x) > 160 for x in markers):
        return output
    observed = set(markers)
    if len(observed) != len(markers):  # duplicate evidence isn't stronger
        return output

    before, after, tampered = [h.lower() for h in hashes]
    digest_ok = before == after and before != tampered
    output["worker_integrity"] = (
        "BUILD_SESSION_SHA256_STABLE_TAMPER_DETECTED" if digest_ok
        else "REJECTED"
    )
    # A self-calculated hash and Authenticode status alone do not establish
    # a trusted signer, reviewed provenance, signed manifest or anti-TOCTOU.
    output["worker_provenance"] = (
        "UNSIGNED_TEST_FIXTURE" if evidence["authenticode_status"] == "NotSigned"
        else "PUBLISHER_AND_CHAIN_NOT_PINNED"
    )
    denial = EXPLICIT_DENY in observed
    timed_out = TIMEOUT in observed
    if denial and not timed_out:
        output["network_probe"] = "EXPLICIT_DENY_SINGLE_LOOPBACK"
    elif timed_out and not denial:
        output["network_probe"] = "TIMEOUT_INCONCLUSIVE"
    elif denial or timed_out:
        output["network_probe"] = "CONTRADICTORY"
    else:
        output["network_probe"] = "MISSING"

    evidence_valid = (
        digest_ok
        and evidence["appcontainer_lab_exit_code"] == 0
        and REQUIRED.issubset(observed)
        and SUCCESS_COMPLETION in observed
        and DENY_COMPLETION in observed
        and output["network_probe"] in (
            "TIMEOUT_INCONCLUSIVE", "EXPLICIT_DENY_SINGLE_LOOPBACK")
        and len(observed - REQUIRED - {
            SUCCESS_COMPLETION, DENY_COMPLETION, TIMEOUT, EXPLICIT_DENY,
            "STAGE46_14_FIXED_NATIVE_APP_CONTAINER_LAB_COMPILED",
            "STAGE46_14_WINDOWS_NATIVE_APP_CONTAINER_LAB_SUCCESS",
            "STAGE46_14_PHYSICAL_OWNER_PC_AND_PRODUCTION_NO_GO",
        }) == 0
    )
    output["test_evidence_valid"] = evidence_valid
    return output


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        raw = args.evidence.read_text(encoding="utf-8")
        if len(raw) > 16000:
            raise ValueError("oversized evidence")
        evidence = json.loads(raw)
    except (OSError, ValueError, UnicodeError):
        evidence = None
    report = review(evidence)
    result = json.dumps(report, sort_keys=True, indent=2) + "\n"
    if args.output:
        args.output.write_text(result, encoding="utf-8")
    print(result, end="")
    # A valid *synthetic experiment* is a passing CI job only while the
    # real-world trust and network release decisions stay NO_GO.
    return 0 if report["test_evidence_valid"] and report["decision"] == "NO_GO" else 1


if __name__ == "__main__":
    raise SystemExit(main())
