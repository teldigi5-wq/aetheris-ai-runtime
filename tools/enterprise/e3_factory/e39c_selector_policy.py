#!/usr/bin/env python3
"""E3.9C: narrowly selector-aware focus CSS policy, not a general CSS parser.

One exact immutable target selector/property and a reviewed 0..24px value
range. An already-verified Chromium keyboard failure and generated website
manifest are mandatory. No AI is invoked by this module; any fixture repair
is solely policy/browser QA, never a model performance metric.
"""
from __future__ import annotations

import json
import re
import sys

import e39_keyboard_patch_gate as gate

SELECTOR = ".skip-link:focus"
PROPERTY = "top"
KIND = "E39C_SELECTOR_SCOPED_FOCUS_TOP_REPLACEMENT"
KEYS = frozenset({"version", "kind", "caseId", "path", "baseSha256",
                  "beforeBrowserSha256", "resultSha256", "selector",
                  "property", "old", "new"})
# Full selector + one declaration must be present. No CSS comments, blocks,
# at-rules, var(), calc(), url(), !important, or other selectors permitted.
NEW_RULE = re.compile(r"\.skip-link:focus\{top:([0-9]{1,2})px\}\Z", re.ASCII)
ALLOWED_TOP_PIXELS = frozenset(range(25))


def candidate_result(
    candidate: dict, source: bytes, browser: bytes,
    canonical: bytes, manifest: dict
) -> tuple[bytes, int]:
    if (not isinstance(candidate, dict) or set(candidate) != KEYS
            or type(candidate.get("version")) is not int
            or candidate["version"] != 1
            or candidate.get("kind") != KIND
            or candidate.get("caseId") != "keyboard-skip-link"
            or candidate.get("path") != "site/styles.css"
            or candidate.get("selector") != SELECTOR
            or candidate.get("property") != PROPERTY
            or candidate.get("old") != gate.BEFORE
            or type(candidate.get("new")) is not str
            or candidate.get("baseSha256") != gate.sha(source)
            or candidate.get("beforeBrowserSha256") != gate.sha(browser)):
        raise ValueError("E39C_SELECTOR_OR_SOURCE_SCOPE_REFUSED")
    match = NEW_RULE.fullmatch(candidate["new"])
    if match is None:
        raise ValueError("E39C_NOT_SINGLE_FOCUS_TOP_DECLARATION")
    pixels = int(match.group(1))
    if pixels not in ALLOWED_TOP_PIXELS or match.group(1) != str(pixels):
        raise ValueError("E39C_FOCUS_TOP_OUT_OF_REVIEWED_RANGE")
    clean = canonical.decode("utf-8")
    if (canonical != gate.checked(gate.worker.HERE / "styles.css", 25_000)
            or not isinstance(manifest, dict)
            or manifest.get("generatedFilesSha256", {}).get("styles.css") != gate.sha(canonical)
            or clean.count(gate.AFTER) != 1
            or clean.count(gate.BEFORE) != 0
            or clean.count(".skip-link{position:fixed;") != 1):
        raise ValueError("E39C_UNTRUSTED_OR_AMBIGUOUS_CANONICAL_WEBSITE")
    source_text = source.decode("utf-8")
    # Unchanged base bytes are mandatory. Any extra selector, media override,
    # prompt injection comment, at-rule or non-focus rewrite is a hard denial.
    expected_broken = clean.replace(gate.AFTER, gate.BEFORE, 1)
    if source_text != expected_broken:
        raise ValueError("E39C_SOURCE_NOT_EXACT_SINGLE_CSS_FAULT")
    result = clean.replace(gate.AFTER, candidate["new"], 1).encode("utf-8")
    if candidate.get("resultSha256") != gate.sha(result):
        raise ValueError("E39C_RESULT_HASH_OR_ADDITIONAL_EDIT_REFUSED")
    return result, pixels


def fixture_proposal(top: int) -> None:
    """Deterministic CI-only TEST proposal: never claim model generated it."""
    if type(top) is not int or top not in ALLOWED_TOP_PIXELS:
        raise ValueError("E39C_FIXTURE_TOP_OUT_OF_RANGE")
    gate.ensure_workspace()
    browser = gate.actual_browser_failure()
    source = gate.checked(gate.STYLE, 25_000)
    canonical = gate.checked(gate.worker.HERE / "styles.css", 25_000)
    clean = canonical.decode("utf-8")
    if clean.count(gate.AFTER) != 1 or source.decode("utf-8") != clean.replace(
            gate.AFTER, gate.BEFORE, 1):
        raise ValueError("E39C_FIXTURE_NOT_ORIGINAL_SINGLE_DEFECT")
    new = f"{SELECTOR}{{top:{top}px}}"
    result = clean.replace(gate.AFTER, new, 1).encode("utf-8")
    proposal = {
        "version": 1, "kind": KIND, "caseId": "keyboard-skip-link",
        "path": "site/styles.css", "selector": SELECTOR, "property": PROPERTY,
        "baseSha256": gate.sha(source),
        "beforeBrowserSha256": gate.sha(browser),
        "resultSha256": gate.sha(result), "old": gate.BEFORE, "new": new,
    }
    with (gate.DIR / "proposal.json").open("x", encoding="utf-8") as handle:
        json.dump(proposal, handle, indent=2, sort_keys=True)
        handle.write("\n")
    print("E39C_DETERMINISTIC_TEST_FIXTURE_NOT_AI", top)


def apply() -> None:
    gate.ensure_workspace()
    browser = gate.actual_browser_failure()
    source = gate.checked(gate.STYLE, 25_000)
    canonical = gate.checked(gate.worker.HERE / "styles.css", 25_000)
    manifest = json.loads(gate.checked(gate.SITE / "manifest.json", 4096))
    proposal = json.loads(gate.checked(gate.DIR / "proposal.json", 2048))
    updated, pixels = candidate_result(proposal, source, browser,
                                       canonical, manifest)
    destination = gate.DIR / "receipt.json"
    if destination.exists() or destination.is_symlink():
        raise ValueError("E39C_RECEIPT_ALREADY_EXISTS")
    gate.STYLE.write_bytes(updated)
    if gate.checked(gate.STYLE, 25_000) != updated:
        raise ValueError("E39C_PATCH_WRITE_MISMATCH")
    evidence = {
        "evidenceClass": "E39C_SELECTOR_POLICY_ACCEPTED_BROWSER_PENDING",
        "caseId": "keyboard-skip-link", "selector": SELECTOR,
        "property": PROPERTY, "topPx": pixels,
        "beforeSha256": gate.sha(source),
        "afterSha256": gate.sha(updated),
        "modelWasCalled": False, "modelGeneratedThisPatch": False,
        "ownerApproved": False, "productionPublished": False,
        "independentBrowserAfterRequired": True,
    }
    with destination.open("x", encoding="utf-8") as handle:
        json.dump(evidence, handle, indent=2, sort_keys=True)
        handle.write("\n")
    print("E39C_SELECTOR_SCOPED_TEST_PATCH_APPLIED_BROWSER_PENDING", pixels)


def verify() -> None:
    gate.ensure_workspace()
    receipt = json.loads(gate.checked(gate.DIR / "receipt.json", 4096))
    after = json.loads(gate.checked(gate.DIR / "browser-after.json", 4096))
    desktop = json.loads(gate.checked(gate.DIR / "desktop-after.json", 4096))
    source = gate.checked(gate.STYLE, 25_000)
    canonical = gate.checked(gate.worker.HERE / "styles.css", 25_000)
    pixels = receipt.get("topPx")
    if type(pixels) is not int or pixels not in ALLOWED_TOP_PIXELS:
        raise ValueError("E39C_RECEIPT_VALUE_UNVERIFIED")
    new = f"{SELECTOR}{{top:{pixels}px}}"
    expected = canonical.decode("utf-8").replace(gate.AFTER, new, 1).encode("utf-8")
    if (source != expected or receipt.get("afterSha256") != gate.sha(source)
            or receipt.get("evidenceClass") != "E39C_SELECTOR_POLICY_ACCEPTED_BROWSER_PENDING"
            or receipt.get("modelWasCalled") is not False
            or receipt.get("modelGeneratedThisPatch") is not False
            or receipt.get("ownerApproved") is not False
            or receipt.get("productionPublished") is not False):
        raise ValueError("E39C_RECEIPT_OR_SOURCE_INVALID")
    for label, proof in (("mobile", after), ("desktop", desktop)):
        if (proof.get("evidenceClass") != "E39_CHROMIUM_KEYBOARD_ACCESSIBILITY"
                or proof.get("caseId") != "keyboard-skip-link"
                or proof.get("passed") is not True
                or proof.get("failureCode") is not None
                or proof.get("focusReached") is not True
                or proof.get("visibleWhenFocused") is not True
                or proof.get("pageJavaScriptErrors") != []
                or not isinstance(proof.get("geometry"), dict)
                or proof["geometry"].get("top", -1) < 0
                or proof["geometry"].get("bottom", 99999)
                   > proof["geometry"].get("viewportH", 0)):
            raise ValueError("E39C_BROWSER_AFTER_FAILED_" + label)
    print("E39C_TWO_REAL_CHROMIUM_VIEWPORTS_VERIFIED_FIXTURE_ONLY", pixels)


def main(args: list[str]) -> None:
    if len(args) == 2 and args[0] == "fixture-proposal" and args[1] in ("0", "12"):
        fixture_proposal(int(args[1]))
    elif args == ["apply"]:
        apply()
    elif args == ["verify"]:
        verify()
    else:
        raise ValueError("E39C_ONLY_FIXED_TEST_OR_SCOPE_COMMANDS")


if __name__ == "__main__":
    main(sys.argv[1:])
