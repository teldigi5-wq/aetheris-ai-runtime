#!/usr/bin/env python3
"""Stage 46.16: fail-closed release-trust and WFP diagnostic evaluator.

WARNING: NEVER used as an owner-PC authorization service. It validates the
bounded test-lab receipt only. Native WFP ALE layer discovery, an in-run hash
and an Authenticode status are NOT correlated block evidence or trusted
publisher provenance. No native audio engine or OS control is enabled.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from stage46_worker_trust_evidence import review as review_previous

MAX_RECEIPT_BYTES = 20_000
# No approved distribution artifacts or publisher certificates exist yet.
# Trust anchors must be independently reviewed and approved; DO NOT inject
# them from a PR payload, GitHub runner environment or generated CI receipt.
APPROVED_RELEASE_ARTIFACT_SHA256 = frozenset()
APPROVED_PUBLISHER_CERT_SHA256 = frozenset()

EXPECTED_WFP_MARKERS = frozenset({
    "STAGE46_16_WFP_BFE_READ_ONLY_SESSION_OPENED",
    "STAGE46_16_WFP_ALE_AUTH_CONNECT_V4_READ_ONLY_LAYER_PRESENT",
    "STAGE46_16_WFP_ALE_AUTH_CONNECT_V6_READ_ONLY_LAYER_PRESENT",
    "STAGE46_16_WFP_NO_FILTER_OR_FIREWALL_RULES_MODIFIED",
    "STAGE46_16_NO_CORRELATED_BLOCK_EVENT_EGRESS_UNVERIFIED",
})


def review(evidence: object) -> dict[str, object]:
    report: dict[str, object] = {
        "schema_version": 1,
        "synthetic_diagnostics_valid": False,
        "wfp_observation": "UNVERIFIED",
        "correlated_wfp_denial": False,
        "network_egress_certified": False,
        "worker_build_integrity": "UNVERIFIED",
        "trusted_worker_publisher": False,
        "trusted_worker_artifact": False,
        "worker_provenance": "UNVERIFIED",
        "signing_key_or_release_artifact_approved": False,
        "owner_pc_pilot_authorized": False,
        "microphone_authorized": False,
        "desktop_actions_authorized": False,
        "production_authorized": False,
        "decision": "NO_GO",
    }
    if not isinstance(evidence, dict) or set(evidence) != {
        "schema_version", "stage46_15_evidence", "wfp_probe_exit_code",
        "wfp_observed_markers", "publisher_certificate_sha256",
    } or type(evidence["schema_version"]) is not int or evidence["schema_version"] != 1:
        return report

    previous = review_previous(evidence["stage46_15_evidence"])
    report["worker_build_integrity"] = previous["worker_integrity"]
    report["worker_provenance"] = previous["worker_provenance"]
    lines = evidence["wfp_observed_markers"]
    if type(evidence["wfp_probe_exit_code"]) is not int or not isinstance(lines, list):
        return report
    if not (0 < len(lines) <= len(EXPECTED_WFP_MARKERS)):
        return report
    if not all(isinstance(s, str) and len(s) <= 140 for s in lines):
        return report
    observed = set(lines)
    if len(observed) != len(lines):
        return report
    if evidence["wfp_probe_exit_code"] == 0 and observed == EXPECTED_WFP_MARKERS:
        report["wfp_observation"] = "READ_ONLY_ALE_V4_V6_LAYERS_PRESENT"
    else:
        report["wfp_observation"] = "INVALID_OR_INCOMPLETE"

    cert = evidence["publisher_certificate_sha256"]
    if cert is not None and (
        not isinstance(cert, str) or len(cert) != 64 or
        any(c not in "0123456789abcdefABCDEF" for c in cert)
    ):
        return report
    # An attacker-controlled receipt cannot add trust anchors.
    # A future APPROVED sign-off would also need independently verified
    # chain, expected signer key usage, anti-TOCTOU and release artifact.
    worker = evidence["stage46_15_evidence"]
    if isinstance(worker, dict):
        sha = worker.get("worker_sha256_after")
        if isinstance(sha, str) and sha.lower() in APPROVED_RELEASE_ARTIFACT_SHA256:
            report["trusted_worker_artifact"] = True
        if isinstance(cert, str) and cert.lower() in APPROVED_PUBLISHER_CERT_SHA256:
            report["trusted_worker_publisher"] = (
                worker.get("authenticode_status") == "Valid"
            )

    report["synthetic_diagnostics_valid"] = bool(
        previous["test_evidence_valid"]
        and report["wfp_observation"] == "READ_ONLY_ALE_V4_V6_LAYERS_PRESENT"
    )
    # Deliberately NO claim about a WFP block event, app-specific filter,
    # network firewall, pinned binary publisher, release or real PC access.
    return report


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        if args.evidence.stat().st_size > MAX_RECEIPT_BYTES:
            raise ValueError("OVERSIZED_SYNTHETIC_EVIDENCE")
        evidence = json.loads(args.evidence.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, ValueError):
        evidence = None
    verdict = review(evidence)
    result = json.dumps(verdict, sort_keys=True, indent=2) + "\n"
    print(result, end="")
    if args.output is not None:
        args.output.write_text(result, encoding="utf-8")
    return 0 if verdict["synthetic_diagnostics_valid"] and verdict["decision"] == "NO_GO" else 1


if __name__ == "__main__":
    raise SystemExit(main())
