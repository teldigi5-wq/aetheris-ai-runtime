#!/usr/bin/env python3
"""B4J fixed trusted-control patch worker. Not for client or model-authored sites.

Only two code-reviewed exact CSS changes; checks reviewed positive reference
independently. Intended to run inside existing network-none, bounded Docker
worker. The approved deterministic fixture is NOT a model coding benchmark.
"""
from __future__ import annotations
import hashlib
import json
import os
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
FIX = HERE / "e39g12b4i" / "fixtures"
OUT = REPO / "target/e3-factory/e39g12b4j"
RULES = {
    "focus": (".skip:focus{top:-120px;}", ".skip:focus{top:16px;}"),
    "motion": (
        "@media(prefers-reduced-motion:reduce){.ticker{animation:tickerPulse 2s linear infinite;}}",
        "@media(prefers-reduced-motion:reduce){.ticker{animation:none;}}",
    ),
}
FIELDS = {"case", "operation", "sourceSha256", "old", "new"}


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def pairs_unique(pairs):
    obj = {}
    for k, v in pairs:
        if k in obj:
            raise ValueError("B4J_DUPLICATE_JSON_FIELD")
        obj[k] = v
    return obj


def checked(path: Path, max_bytes: int) -> bytes:
    if path.is_symlink() or not path.is_file() or not 0 < path.stat().st_size <= max_bytes:
        raise ValueError("B4J_UNSAFE_FILE")
    return path.read_bytes()


def title_normalize(reference: bytes, case: str) -> bytes:
    bad = f"B4I — Trusted {case} positive control".encode("utf-8")
    good = f"B4I — Trusted {case} negative control".encode("utf-8")
    if reference.count(bad) != 1:
        raise ValueError("B4J_REFERENCE_TITLE_DRIFT")
    return reference.replace(bad, good, 1)


def review(case: str, proposal: dict, broken: bytes, reference: bytes) -> bytes:
    if case not in RULES:
        raise ValueError("B4J_CASE_UNREVIEWED")
    if type(proposal) is not dict or set(proposal) != FIELDS:
        raise ValueError("B4J_SCHEMA_REJECTED")
    old, new = RULES[case]
    if (proposal.get("case") != case
        or proposal.get("operation") != "replace_one"
        or proposal.get("sourceSha256") != sha(broken)
        or type(proposal.get("old")) is not str or proposal["old"] != old
        or type(proposal.get("new")) is not str or proposal["new"] != new):
        raise ValueError("B4J_UNAUTHORIZED_EDIT")
    if len(broken) > 5000 or len(reference) > 5000:
        raise ValueError("B4J_SOURCE_LIMIT")
    if broken.count(old.encode()) != 1:
        raise ValueError("B4J_NOT_UNIQUE_SOURCE_LITERAL")
    result = broken.replace(old.encode(), new.encode(), 1)
    if result != title_normalize(reference, case):
        raise ValueError("B4J_POSITIVE_REFERENCE_MISMATCH")
    return result


def apply(case: str) -> None:
    if case not in RULES:
        raise ValueError("B4J_CASE_UNREVIEWED")
    if OUT.is_symlink() or not OUT.is_dir():
        raise ValueError("B4J_MISSING_OR_SYMLINK_OUTPUT_ROOT")
    root = OUT.resolve()
    destination = root / "repairs" / case
    if destination.exists() or destination.is_symlink():
        raise ValueError("B4J_NO_REPLAY")
    proposal_path = root / "incoming" / (case + ".json")
    raw = checked(proposal_path, 1500)
    proposal = json.loads(raw, object_pairs_hook=pairs_unique)
    broken = checked(FIX / (case + "-negative.html"), 5000)
    positive = checked(FIX / (case + "-positive.html"), 5000)
    result = review(case, proposal, broken, positive)
    parent = root / "repairs"
    if parent.is_symlink() or not parent.is_dir():
        raise ValueError("B4J_OUTPUT_PARENT_SCOPE")
    # Fixed case token, exclusive mkdir, no symlink outputs.
    destination.mkdir(mode=0o700)
    (destination / "index.html").write_bytes(result)
    report = {
        "evidenceClass": "E39G12B4J_TRUSTED_FIXED_POLICY_APPLIED_CONTROL",
        "case": case,
        "sourceBeforeSha256": sha(broken),
        "sourceAfterSha256": sha(result),
        "reviewedReferenceMatches": True,
        "patchAppliedInNetworkNoneWorker": True,
        "modelCallPerformed": False,
        "modelAuthoredPatch": False,
        "untrustedGenericHtmlAuthorized": False,
        "independentCuratorVerified": False,
        "browserVerifiedModelRepair": False,
        "ownerApprovalGranted": False,
        "stage45Go": False,
        "productionAuthorized": False,
    }
    (destination / "report.json").write_text(
        json.dumps(report, sort_keys=True, indent=2) + "\n",
        encoding="utf-8",
    )
    print("B4J_REVIEWED_TRUSTED_FIXTURE_APPLIED_NETWORK_NONE", case, sha(result))


if __name__ == "__main__":
    if len(sys.argv) != 3 or sys.argv[1] != "apply":
        raise ValueError("B4J_FIXED_COMMAND_ONLY")
    apply(sys.argv[2])
