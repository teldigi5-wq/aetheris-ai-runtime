#!/usr/bin/env python3
"""E3.9A bounded keyboard CSS patch gate. It is NOT an AI proposer.

The evaluator creates a disposable synthetic fault and generates the fixture
only to verify the secure policy. Never count this as model-generated repair.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import sys

import heuristic_repair as oldgate
import worker

BASE = worker.REPO / "target/e3-factory"
DIR = BASE / "e39"
SITE = BASE / "site"
STYLE = SITE / "styles.css"
KIND = "E39_LITERAL_KEYBOARD_CSS_REPLACEMENT"
BEFORE = ".skip-link:focus{top:-80px}"
AFTER = ".skip-link:focus{top:12px}"
KEYS = frozenset({"version", "kind", "caseId", "path", "baseSha256",
                  "resultSha256", "beforeBrowserSha256", "old", "new"})


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def checked(path: Path, size: int) -> bytes:
    if path.is_symlink() or not path.is_file() or path.stat().st_size > size:
        raise ValueError("E39_FILE_LINK_MISSING_OR_TOO_LARGE")
    return path.read_bytes()


def ensure_workspace() -> None:
    for p in (BASE, SITE, DIR):
        if p.is_symlink() or not p.is_dir():
            raise ValueError("E39_WORKSPACE_PATH_INVALID")
    if SITE.resolve(strict=True) != worker.REPO.resolve(strict=True) / "target/e3-factory/site":
        raise ValueError("E39_WORKSPACE_ESCAPE")
    if DIR.resolve(strict=True) != worker.REPO.resolve(strict=True) / "target/e3-factory/e39":
        raise ValueError("E39_EVIDENCE_ESCAPE")


def actual_browser_failure() -> bytes:
    before = checked(DIR / "browser-before.json", 4096)
    data = json.loads(before)
    seed = json.loads(checked(DIR / "seed.json", 4096))
    if (data.get("evidenceClass") != "E39_CHROMIUM_KEYBOARD_ACCESSIBILITY"
            or data.get("passed") is not False
            or data.get("caseId") != "keyboard-skip-link"
            or data.get("failureCode") != "E39_FOCUSED_SKIP_LINK_OFFSCREEN"
            or data.get("focusReached") is not True
            or not isinstance(data.get("geometry"), dict)
            or data["geometry"].get("top", 0) >= 0
            or seed.get("beforeSha256") != sha(checked(STYLE, 25_000))):
        raise ValueError("E39_REAL_BROWSER_FAILURE_PROOF_REQUIRED")
    return before


def proposal_fixture() -> dict:
    """Creates *a deterministic TEST suggestion*, never called by AI."""
    ensure_workspace()
    observed = actual_browser_failure()
    original = checked(STYLE, 25_000)
    text = original.decode("utf-8")
    if text.count(BEFORE) != 1:
        raise ValueError("E39_FAULT_PATTERN_NOT_EXACT")
    restored = text.replace(BEFORE, AFTER, 1).encode("utf-8")
    proposal = {"version": 1, "kind": KIND, "caseId": "keyboard-skip-link",
                "path": "site/styles.css", "baseSha256": sha(original),
                "resultSha256": sha(restored),
                "beforeBrowserSha256": sha(observed),
                "old": BEFORE, "new": AFTER}
    destination = DIR / "proposal.json"
    if destination.is_symlink():
        raise ValueError("E39_PROPOSAL_SYMLINK")
    with destination.open("x", encoding="utf-8") as handle:
        json.dump(proposal, handle, sort_keys=True, indent=2)
        handle.write("\n")
    return proposal


def validate(candidate: dict, source: bytes, browser: bytes, manifest: dict) -> bytes:
    if (not isinstance(candidate, dict) or set(candidate) != KEYS
            or type(candidate.get("version")) is not int or candidate["version"] != 1
            or candidate.get("kind") != KIND
            or candidate.get("caseId") != "keyboard-skip-link"
            or candidate.get("path") != "site/styles.css"
            or candidate.get("old") != BEFORE or candidate.get("new") != AFTER
            or candidate.get("baseSha256") != sha(source)
            or candidate.get("beforeBrowserSha256") != sha(browser)):
        raise ValueError("E39_UNTRUSTED_PROPOSAL_REJECTED")
    # Even matching a literal does not permit arbitrary writes: verify exact
    # canonical website bytes, original build-manifest digest and one edit.
    current = source.decode("utf-8")
    if current.count(BEFORE) != 1:
        raise ValueError("E39_AMBIGUOUS_CSS_EDIT")
    proposed = current.replace(BEFORE, AFTER, 1).encode("utf-8")
    actual = manifest.get("generatedFilesSha256", {})
    if (not isinstance(actual, dict)
            or candidate.get("resultSha256") != sha(proposed)
            or actual.get("styles.css") != sha(proposed)):
        raise ValueError("E39_CANONICAL_SOURCE_HASH_REJECTED")
    return proposed


def apply() -> dict:
    ensure_workspace()
    browser = actual_browser_failure()
    source = checked(STYLE, 25_000)
    manifest = json.loads(checked(SITE / "manifest.json", 4096))
    proposed = json.loads(checked(DIR / "proposal.json", 2048))
    after = validate(proposed, source, browser, manifest)
    STYLE.write_bytes(after)
    if checked(STYLE, 25_000) != after:
        raise ValueError("E39_APPLY_WRITE_DID_NOT_MATCH")
    receipt = {"evidenceClass": "E39_POLICY_ALLOWED_PATCH_BROWSER_CHECK_PENDING",
               "beforeSha256": sha(source), "afterSha256": sha(after),
               "path": "site/styles.css",
               "modelWasCalled": False, "ownerApproved": False,
               "productionPublished": False, "needsRealBrowserAfterTest": True}
    with (DIR / "receipt.json").open("x", encoding="utf-8") as stream:
        json.dump(receipt, stream, indent=2, sort_keys=True)
        stream.write("\n")
    print("E39_TEST_FIXTURE_PATCH_APPLIED_NOT_AI_NOT_PRODUCTION")
    return receipt


def main(args: list[str]) -> None:
    if args == ["fixture-proposal"]:
        proposal_fixture()
    elif args == ["apply"]:
        apply()
    else:
        raise ValueError("E39_ONLY_FIXTURE_PROPOSAL_OR_APPLY_ALLOWED")


if __name__ == "__main__":
    main(sys.argv[1:])
