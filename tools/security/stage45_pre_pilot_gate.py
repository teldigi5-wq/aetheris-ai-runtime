#!/usr/bin/env python3
"""Stage 45.5.19 offline, NON-AUTHORIZING release assessment.

This program deliberately has NO production-approval input or GO pathway.
It checks pinned code-review evidence exists in the checked-out tree, then
prints the unresolved blockers. File existence/text markers are not claims
that code has been deployed, externally audited, or tested on user hardware.

GitHub CI must confirm exact PR HEAD separately. Passing this script
only means that this NO-GO report and its basic reviewed sources remain.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
SCHEMA = "aetheris:stage45:prepilot-review:v1"

# Positive evidence is limited to checked-in REVIEW fixtures; this is NOT
# evidence of real execution or hardware. Keep reasons detailed and stable.
LAB_EVIDENCE: tuple[tuple[str, str, tuple[str, ...]], ...] = (
    (
        "stop_and_owner_quotas",
        "orchestrator-service/src/main/java/io/aetheris/orchestrator/execution/SyntheticMultiAgentAuthorityLab.java",
        ("NON-EXECUTING", "MULTIAGENT_EMERGENCY_STOP", "MULTIAGENT_CLAIM_MISSING"),
    ),
    (
        "signed_stop_rollback",
        "orchestrator-service/src/main/java/io/aetheris/orchestrator/execution/SyntheticStopAuditWitnessLab.java",
        ("NON-EXECUTING", "DATABASE_ROLLBACK_OR_REVOCATION_LOST", "STOP_CONFIRMED"),
    ),
    (
        "voice_consent_and_stop",
        "orchestrator-service/src/main/java/io/aetheris/orchestrator/voice/SyntheticVoiceOwnerConsentLab.java",
        ("NON-EXECUTING", "SIMULATED_ONLY", "localStopLatched=true;"),
    ),
    (
        "windows_read_only_review",
        "workstation-agent/packaging/stage45/OfflineReadinessPolicy.psm1",
        ("REVIEW_ONLY_NO_ACTIVATION", "activationAllowed", "UNTRUSTED_JAR_DIGEST_MISMATCH"),
    ),
    (
        "fixed_host_termination_broker",
        "tests/sandbox/stage45_fixed_kill_broker.go",
        ("SYNTHETIC_BROKER_TERMINATION_UNVERIFIED", "SO_PEERCRED", "AETHERIS_STAGE45_BROKER_CI"),
    ),
    (
        "unmodified_legacy_workstation_installer_requires_review",
        "workstation-agent/packaging/install.ps1",
        ("Startup", "java" ),
    ),
)

# Immutable blockers under this policy version. None are inferred as cleared
# from a developer-controlled JSON manifest or self-signed test fixture.
RELEASE_BLOCKERS: tuple[tuple[str, str], ...] = (
    ("UNMERGED_SECURITY_STACK", "Review, reconcile and merge stacked draft security PRs using protected-branch checks."),
    ("OWNER_AUTH_NOT_INTEGRATED", "Bind agent/voice approvals to independently authenticated, durable owner identity."),
    ("STOP_NOT_END_TO_END", "Connect actual TaskControl STOP to OS-enforced process-tree termination; test crash/daemon loss."),
    ("HOST_BROKER_NOT_ISOLATED", "Protect broker under independent OS/VM trust boundary, away from privileged shared Docker host."),
    ("KEYS_NOT_PRODUCTION_PROTECTED", "Secure signing keys in OS/hardware-backed custody and verify rotation/revocation."),
    ("AUDIT_NOT_EXTERNAL_IMMUTABLE", "Deploy independently governed append-only audit and rollback-resistant STOP anchors."),
    ("DATABASE_RESTORE_UNVERIFIED", "Run actual PostgreSQL backup/PITR restore, recovery and failover tests."),
    ("OS_RESOURCE_LIMITS_UNVERIFIED", "Certify actual OS quotas, constrained agent worker profiles and secure cleanup."),
    ("WINDOWS_WSL_DEVICE_NOT_CERTIFIED", "Run approved physical Windows/WSL privilege, DPAPI, startup and recovery checks."),
    ("VOICE_HARDWARE_NOT_CERTIFIED", "Integrate/measure local microphone, wake detection, streaming offline STT/TTS and barge-in."),
    ("DESKTOP_ACTIONS_NOT_APPROVED", "Implement authenticated owner consent and supervisor-gated desktop operations, default disabled."),
    ("RELEASE_PROVENANCE_NOT_VERIFIED", "Independently verify signed artifacts, installer integrity and supported rollback."),
    ("INDEPENDENT_SECURITY_REVIEW_PENDING", "Obtain independent threat-model, penetration testing and pilot signoff."),
)

def inspect_sources(root: Path) -> list[dict[str, Any]]:
    evidence = []
    for name, relative, anchors in LAB_EVIDENCE:
        location = root / relative
        checked = False
        missing_anchors: list[str] = []
        if location.is_file() and not location.is_symlink():
            try:
                raw = location.read_text(encoding="utf-8")
                missing_anchors = [anchor for anchor in anchors if anchor not in raw]
                checked = not missing_anchors
            except (OSError, UnicodeError):
                missing_anchors = ["UNREADABLE_REVIEW_SOURCE"]
        else:
            missing_anchors = ["MISSING_REVIEW_SOURCE"]
        evidence.append({
            "id": name,
            "source": relative,
            "scope": "CHECKED_IN_SYNTHETIC_OR_READONLY_REVIEW_ONLY",
            "source_markers_present": checked,
            "missing_markers": missing_anchors,
        })
    return evidence

def assess(root: Path) -> dict[str, Any]:
    evidence = inspect_sources(root)
    missing = [item["id"] for item in evidence if not item["source_markers_present"]]
    return {
        "schema": SCHEMA,
        "decision": "NO_GO",
        "scope": "UNMERGED_DRAFT_CI_REVIEW_NOT_A_PRODUCTION_CERTIFICATE",
        "synthetic_source_inventory_complete": not missing,
        "missing_review_sources": missing,
        "production_pilot_authorized": False,
        "agent_command_execution_authorized": False,
        "desktop_actions_authorized": False,
        "windows_device_certified": False,
        "voice_microphone_certified": False,
        "protected_branch_merge_permitted": False,
        "deploy_permitted": False,
        "review_only_evidence": evidence,
        "release_blockers": [{"id": key, "required_evidence": detail}
                             for key, detail in RELEASE_BLOCKERS],
    }

def validate_report(report: dict[str, Any]) -> None:
    if report["schema"] != SCHEMA or report["decision"] != "NO_GO":
        raise ValueError("PREPILOT_MUST_REMAIN_BLOCKED")
    for key in (
        "production_pilot_authorized",
        "agent_command_execution_authorized",
        "desktop_actions_authorized",
        "windows_device_certified",
        "voice_microphone_certified",
        "protected_branch_merge_permitted",
        "deploy_permitted",
    ):
        if report.get(key) is not False:
            raise ValueError("PREPILOT_AUTHORIZATION_INVARIANT_BROKEN")
    expected = {id for id, _ in RELEASE_BLOCKERS}
    if len(expected) != len(RELEASE_BLOCKERS):
        raise ValueError("PREPILOT_DUPLICATE_BLOCKER")
    found = {item.get("id") for item in report["release_blockers"]}
    if found != expected or len(report["release_blockers"]) != len(expected):
        raise ValueError("PREPILOT_BLOCKER_SET_INCOMPLETE")
    if len(report["review_only_evidence"]) != len(LAB_EVIDENCE):
        raise ValueError("PREPILOT_SOURCE_INVENTORY_INCOMPLETE")

def main() -> int:
    parser = argparse.ArgumentParser(description="Emit Stage 45.5.19 NO-GO assessment (never authorizes execution)")
    args = parser.parse_args()
    del args
    report = assess(ROOT)
    validate_report(report)
    print(json.dumps(report, sort_keys=True, indent=2))
    # A missing reviewed file makes the CI gate FAIL while preserving NO-GO.
    return 0 if report["synthetic_source_inventory_complete"] else 2

if __name__ == "__main__":
    raise SystemExit(main())
