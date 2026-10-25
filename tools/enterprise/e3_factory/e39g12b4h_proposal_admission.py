#!/usr/bin/env python3
"""B4H: independent curator-side read-only admission of UNTRUSTED model proposals.

This is NOT a patch executor or correctness evaluator. A quote that passes is
ONLY eligible for separately authorized typed-policy and isolated browser QA.
No network, subprocess, model calls, browser launches, or source modifications.
"""
from __future__ import annotations

from collections import Counter
import json
import os
from pathlib import Path
import re
import sys

import e39g12b4g_curator_boundary as curator

MAX_RECEIPT = 42_000
MAX_RESPONSE_BYTES = 800
MAX_LITERAL = 180
MAX_MODEL_TAG = 100
RESPONSE_FIELDS = frozenset(("site", "fault", "operation", "old", "new", "sourceSha256"))
RECEIPT_FIELDS = frozenset((
    "evidenceClass", "sourceBundleSha256", "modelTag", "modelCallsSelfReported",
    "ownerApprovalGranted", "goldenReferenceAvailable", "modelOutputExecuted",
    "trials"
))
TRIAL_FIELDS = frozenset(("caseId", "sourceSha256", "rawModelResponse", "rawResponseSha256"))
MODEL_TAG = re.compile(r"^[a-zA-Z0-9][a-zA-Z0-9:._/+@-]{0,99}$")


def exact_object(raw: bytes) -> dict:
    if len(raw) > MAX_RECEIPT:
        raise ValueError("B4H_RECEIPT_OVERSIZE")
    obj = curator.parse(raw)  # B4G enforces duplicate-field rejection
    if type(obj) is not dict:
        raise ValueError("B4H_EXPECTED_OBJECT")
    return obj


def inspect_response(raw: str, original: dict) -> dict:
    """No response content ever emitted; only enums and booleans."""
    result = {"caseId": original["id"], "outcome": "SCHEMA_REJECTED"}
    if type(raw) is not str or not 0 < len(raw.encode("utf-8")) <= MAX_RESPONSE_BYTES:
        return result
    try:
        payload = curator.parse(raw.encode("utf-8"))
    except (ValueError, UnicodeError, json.JSONDecodeError):
        return result
    if payload == {"operation": "abstain"}:
        result["outcome"] = "MODEL_ABSTAINED"
        return result
    if set(payload) != RESPONSE_FIELDS or any(type(payload[k]) is not str for k in RESPONSE_FIELDS):
        return result
    if payload["site"] != original["site"] or payload["fault"] != original["fault"]:
        result["outcome"] = "CASE_IDENTITY_REJECTED"
        return result
    if payload["sourceSha256"] != original["brokenSha256"]:
        result["outcome"] = "SOURCE_DIGEST_REJECTED"
        return result
    if payload["operation"] != "replace_one":
        result["outcome"] = "OPERATION_REJECTED"
        return result
    old, new = payload["old"], payload["new"]
    if (not 1 <= len(old) <= MAX_LITERAL or not 1 <= len(new) <= MAX_LITERAL
        or old == new or any(ord(c) < 32 or ord(c) == 127 for c in old + new)):
        result["outcome"] = "LITERAL_BOUNDARY_REJECTED"
        return result
    source = original["brokenHtml"].encode("utf-8")
    if source.count(old.encode("utf-8")) != 1:
        result["outcome"] = "SOURCE_QUOTE_NOT_UNIQUE"
        return result
    # DO NOT apply the replacement. No independent semantic/hostile-HTML
    # validator has yet approved the new site-specific fault vocabulary.
    result["outcome"] = "QUOTE_MATCH_REVIEW_REQUIRED"
    return result


def admit(root: Path, bundle: Path, receipt_path: Path) -> dict:
    """Use separately stored curated reference ONLY for seal verification."""
    checked_root = curator.root_check(root)
    if (receipt_path.is_symlink() or receipt_path.resolve().is_relative_to(checked_root)
        or receipt_path.resolve().is_relative_to(curator.REPO)):
        raise ValueError("B4H_UNTRUSTED_RECEIPT_LOCATION")
    if (bundle.is_symlink() or bundle.resolve().is_relative_to(checked_root)
        or bundle.resolve().is_relative_to(curator.REPO)):
        raise ValueError("B4H_UNTRUSTED_PUBLIC_BUNDLE_LOCATION")
    # Validates private salt + reference digests without exposing reference.
    curator.verify(checked_root, bundle)
    public, _ = curator.inspect(checked_root)
    manifest = {item["id"]: item for item in public}
    receipt = exact_object(curator.checked(receipt_path, MAX_RECEIPT))
    if (set(receipt) != RECEIPT_FIELDS
        or receipt["evidenceClass"] != "E39G12B4H_UNTRUSTED_MODEL_PROPOSALS"
        or receipt["sourceBundleSha256"] != curator.digest(curator.checked(bundle, curator.MAX_TOTAL))
        or type(receipt["modelTag"]) is not str
        or not MODEL_TAG.fullmatch(receipt["modelTag"])
        or type(receipt["modelCallsSelfReported"]) is not int
        or receipt["modelCallsSelfReported"] != len(public)
        or receipt["ownerApprovalGranted"] is not False
        or receipt["goldenReferenceAvailable"] is not False
        or receipt["modelOutputExecuted"] is not False
        or type(receipt["trials"]) is not list
        or len(receipt["trials"]) != len(public)):
        raise ValueError("B4H_UNTRUSTED_RECEIPT_CONTRACT")
    rows = {}
    for row in receipt["trials"]:
        if type(row) is not dict or set(row) != TRIAL_FIELDS:
            raise ValueError("B4H_TRIAL_SCHEMA")
        cid = row["caseId"]
        if type(cid) is not str or cid not in manifest or cid in rows:
            raise ValueError("B4H_DUPLICATE_OR_UNKNOWN_CASE_ID")
        text = row["rawModelResponse"]
        if (type(text) is not str or not 0 < len(text.encode("utf-8")) <= MAX_RESPONSE_BYTES
            or row["sourceSha256"] != manifest[cid]["brokenSha256"]
            or row["rawResponseSha256"] != curator.digest(text.encode("utf-8"))):
            raise ValueError("B4H_UNTRUSTED_ROW_DRIFT")
        rows[cid] = text
    if set(rows) != set(manifest):
        raise ValueError("B4H_CASE_COVERAGE_MISMATCH")
    results = [inspect_response(rows[cid], manifest[cid]) for cid in sorted(rows)]
    return {
        "evidenceClass": "E39G12B4H_STATIC_QUOTE_ADMISSION_NO_EXECUTION",
        "publicBundleSha256": receipt["sourceBundleSha256"],
        "receiptSha256": curator.digest(curator.checked(receipt_path, MAX_RECEIPT)),
        "modelTagUnattested": receipt["modelTag"],
        "modelCallsSelfReportedUnverified": receipt["modelCallsSelfReported"],
        "caseCount": len(results),
        "categories": dict(sorted(Counter(x["outcome"] for x in results).items())),
        "results": results,
        "independentCuratorVerified": False,
        "modelExecutionVerified": False,
        "modelOutputExecuted": False,
        "patchesApplied": 0,
        "policyAcceptedPatches": 0,
        "browserVerifiedRepairs": 0,
        "heldOutQualityCertified": False,
        "ownerApprovalGranted": False,
        "productionAuthorized": False,
        "stage45Go": False,
    }


def save_report(report: dict, target: Path, root: Path) -> None:
    resolved = target.resolve()
    if (target.is_symlink() or target.exists()
        or resolved.is_relative_to(curator.root_check(root))
        or resolved.is_relative_to(curator.REPO)
        or not target.parent.is_dir() or target.parent.is_symlink()):
        raise ValueError("B4H_REPORT_SCOPE_OR_REPLAY")
    fd = os.open(resolved, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(fd, "wb") as out:
        out.write(curator.canonical(report))


def main() -> None:
    if len(sys.argv) != 5 or sys.argv[1] != "admit":
        raise ValueError("B4H_EXACT_ADMIT_COMMAND_ONLY")
    root, bundle, receipt, report = map(Path, sys.argv[2:])
    result = admit(root, bundle, receipt)
    save_report(result, report, root)
    print("B4H_STATIC_ADMISSION_ONLY", json.dumps({
        "caseCount": result["caseCount"],
        "categories": result["categories"],
        "policyAccepted": 0, "browserVerified": 0,
        "productionAuthorized": False, "stage45Go": False,
    }, sort_keys=True))


if __name__ == "__main__":
    main()
