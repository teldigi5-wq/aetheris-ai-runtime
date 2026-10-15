#!/usr/bin/env python3
"""Stage 46.17: offline, fail-closed WFP snapshot + signed-release spec gate.

This never authorizes real network egress claims or production. A historic WFP
CLASSIFY_DROP count cannot be attributed to the synthetic AppContainer without
actual matching app ID, port, timestamp, protocol, filter/layer evidence and a
trusted capture path. Neither snapshot nor trust spec can grant authority.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
from stage46_wfp_provenance_gate import review as review_prior

SNAPSHOT_MARKERS = frozenset({
    "STAGE46_17_WFP_EVENT_LOGGING_DISABLED",
    "STAGE46_17_WFP_SNAPSHOT_UNAVAILABLE",
    "STAGE46_17_WFP_SNAPSHOT_EMPTY",
    "STAGE46_17_WFP_UNCORRELATED_DROP_SNAPSHOT_PRESENT",
})
REQUIRED = frozenset({
    "STAGE46_17_WFP_PROCESS_CORRELATION_NOT_VERIFIED",
    "STAGE46_17_NO_WFP_FILTER_OR_AUDIT_CONFIGURATION_CHANGED",
})
REQUIRED_FALSE_RELEASE = (
    "independently_pinned_trust_chain", "code_signing_eku_verified",
    "authenticode_valid", "certificate_revocation_verified",
    "non_writable_staged_artifact", "launch_from_verified_open_handle",
    "signed_dependency_manifest", "voice_model_distribution_licenses_approved",
)
REQUIRED_FALSE_NETWORK = (
    "per_process_app_identifier_verified",
    "exact_ephemeral_destination_and_protocol_correlated",
    "monotonic_time_window_correlated",
    "actual_kernel_classify_drop_filter_and_layer_correlated",
    "explicit_ipv4_and_ipv6_test_coverage",
    "external_egress_and_dns_policy_verified",
    "logging_available_and_complete",
    "rollback_of_any_transient_wfp_configuration_verified",
)


def review(receipt: object, release_spec: object) -> dict[str, object]:
    result: dict[str, object] = {
        "schema_version": 1,
        "synthetic_read_only_audit_valid": False,
        "wfp_snapshot_status": "INVALID",
        "app_specific_drop_verified": False,
        "network_egress_certified": False,
        "binary_signature_approved": False,
        "release_provenance_approved": False,
        "worker_install_authorized": False,
        "owner_pc_pilot_authorized": False,
        "microphone_authorized": False,
        "desktop_actions_authorized": False,
        "production_authorized": False,
        "decision": "NO_GO",
    }
    if not isinstance(receipt, dict) or set(receipt) != {
        "schema_version", "stage46_16_evidence", "wfp_drop_probe_exit_code",
        "wfp_drop_observed_markers",
    } or type(receipt["schema_version"]) is not int or receipt["schema_version"] != 1:
        return result
    if not isinstance(release_spec, dict) or set(release_spec) != {
        "schema_version", "stage", "artifact_class", "release_decision",
        "candidate_worker", "required_network_evidence", "enforcement_scope", "notes",
    } or release_spec["schema_version"] != 1 or type(release_spec["schema_version"]) is not int:
        return result
    if (release_spec["stage"] != "46.17"
        or release_spec["artifact_class"] != "SYNTHETIC_TEST_ONLY"
        or release_spec["release_decision"] != "NO_GO"):
        return result
    worker = release_spec["candidate_worker"]
    network = release_spec["required_network_evidence"]
    enforcement = release_spec["enforcement_scope"]
    if not all(isinstance(x, dict) for x in (worker, network, enforcement)):
        return result
    if (worker.get("canonical_source_revision") is not None
        or worker.get("independently_approved_reproducible_artifact_sha256") is not None
        or worker.get("approved_code_signing_leaf_certificate_sha256") is not None
        or any(worker.get(key) is not False for key in REQUIRED_FALSE_RELEASE)
        or network.get("test_listener_positive_control") is not True
        or any(network.get(key) is not False for key in REQUIRED_FALSE_NETWORK)
        or any(enforcement.get(key) is not False for key in (
            "installed_firewall_rules", "subscribed_to_wfp_events",
            "real_microphone_enabled", "real_speech_models_loaded",
            "owner_desktop_actions_authorized", "physical_owner_pc_pilot_authorized",
            "production_authorized"
        ))):
        return result
    notes = release_spec["notes"]
    if not isinstance(notes, list) or len(notes) > 8 or any(
        not isinstance(note, str) or len(note) > 300 for note in notes
    ):
        return result

    previous = review_prior(receipt["stage46_16_evidence"])
    if not previous["synthetic_diagnostics_valid"] or type(receipt["wfp_drop_probe_exit_code"]) is not int:
        return result
    markers = receipt["wfp_drop_observed_markers"]
    if (not isinstance(markers, list) or len(markers) > 5
        or any(not isinstance(m, str) or len(m) > 100 for m in markers)
        or len(set(markers)) != len(markers)):
        return result
    seen = set(markers)
    matches = seen & SNAPSHOT_MARKERS
    if (len(matches) != 1 or seen != (REQUIRED | matches)
        or receipt["wfp_drop_probe_exit_code"] != 0):
        return result
    status = next(iter(matches))
    result["wfp_snapshot_status"] = status.removeprefix("STAGE46_17_WFP_")
    result["synthetic_read_only_audit_valid"] = True
    # This is intentionally NOT a signed source of kernel audit events.
    # Even if a future report contains WFP drops, no process-specific
    # attribution or publisher pin has been verified by this gate.
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence", type=Path, required=True)
    parser.add_argument("--release-spec", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        if args.evidence.stat().st_size > 24000 or args.release_spec.stat().st_size > 12000:
            raise ValueError("OVERSIZED_AUDIT_INPUT")
        evidence = json.loads(args.evidence.read_text(encoding="utf-8"))
        spec = json.loads(args.release_spec.read_text(encoding="utf-8"))
    except (OSError, ValueError, UnicodeError):
        evidence, spec = None, None
    result = review(evidence, spec)
    rendered = json.dumps(result, sort_keys=True, indent=2) + "\n"
    if args.output:
        args.output.write_text(rendered, encoding="utf-8")
    print(rendered, end="")
    return 0 if result["synthetic_read_only_audit_valid"] and result["decision"] == "NO_GO" else 1


if __name__ == "__main__":
    raise SystemExit(main())
