#!/usr/bin/env python3
"""E3.9C-2B1: eight distinct synthetic browser-observed focus failures.

Evaluator/oracle is intentionally separate from ALL model advisers; no real
model inference runs here. The only repair is a deterministic TEST fixture,
validated by an exact-source gate before independent Chromium after checks.
"""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

import worker

ROOT = worker.REPO / "target/e3-factory"
SITE = ROOT / "site"
STYLE = SITE / "styles.css"
OUTPUT = ROOT / "e39d"
CANONICAL = worker.HERE / "styles.css"
ORIGINAL = ".skip-link:focus{top:12px}"
# Four failure *mechanisms*, two different seeded variants per mechanism.
FAULTS = {
    "top-40": (".skip-link:focus{top:-40px}", "E39D_FOCUS_OFFSCREEN"),
    "top-120": (".skip-link:focus{top:-120px}", "E39D_FOCUS_OFFSCREEN"),
    "left-120": (".skip-link:focus{top:12px;left:-120px}", "E39D_FOCUS_OFFSCREEN"),
    "left-240": (".skip-link:focus{top:12px;left:-240px}", "E39D_FOCUS_OFFSCREEN"),
    "opacity-zero": (".skip-link:focus{top:12px;opacity:0}", "E39D_FOCUS_TRANSPARENT"),
    "visibility-hidden": (".skip-link:focus{top:12px;visibility:hidden}", "E39D_FOCUS_NOT_REACHABLE"),
    "transform-up": (".skip-link:focus{top:12px;transform:translateY(-180px)}", "E39D_FOCUS_OFFSCREEN"),
    "transform-left": (".skip-link:focus{top:12px;transform:translateX(-320px)}", "E39D_FOCUS_OFFSCREEN"),
}
CLASS = "E39D_HELDOUT_SYNTHETIC_FOCUS_TEST_NO_MODEL"


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def checked(path: Path, max_bytes: int) -> bytes:
    if path.is_symlink() or not path.is_file() or path.stat().st_size > max_bytes:
        raise ValueError("E39D_UNSAFE_FILE_OR_SIZE")
    return path.read_bytes()


def location(case: str) -> Path:
    if case not in FAULTS:
        raise ValueError("E39D_UNKNOWN_CASE")
    return OUTPUT / case


def clean_source() -> bytes:
    source = checked(CANONICAL, 25000)
    original = source.decode("utf-8")
    if original.count(ORIGINAL) != 1 or not original.count(".skip-link{position:fixed;") == 1:
        raise ValueError("E39D_EXPECTED_FOCUS_RULE_MISSING")
    return source


def defective_source(case: str) -> bytes:
    rule, _ = FAULTS[case]
    return clean_source().decode().replace(ORIGINAL, rule, 1).encode("utf-8")


def seed(case: str) -> None:
    directory = location(case)
    if (SITE.is_symlink() or OUTPUT.is_symlink() or directory.is_symlink()
            or directory.exists()):
        raise ValueError("E39D_UNSAFE_TEST_WORKSPACE")
    if checked(STYLE, 25000) != clean_source():
        raise ValueError("E39D_NOT_CANONICAL_INITIAL_SOURCE")
    target = defective_source(case)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    directory.mkdir(mode=0o700)
    STYLE.write_bytes(target)
    report = {
        "evidenceClass": CLASS, "caseId": case,
        "expectedFailure": FAULTS[case][1],
        "beforeSourceSha256": sha(target),
        "canonicalSourceSha256": sha(clean_source()),
        "modelWasCalled": False, "isBlindModelBenchmark": False,
        "ownerPcUsed": False, "productionPublished": False,
    }
    (directory / "seed.json").write_text(json.dumps(report, sort_keys=True, indent=2)+"\n")
    print("E39D_SYNTHETIC_FAULT_SEEDED_NO_MODEL", case, sha(target))


def verify_before(case: str) -> None:
    directory = location(case)
    seed = json.loads(checked(directory / "seed.json", 4096))
    # Both screens must fail the *specific* expected fault mechanism.
    for screen in ("mobile", "desktop"):
        observed = json.loads(checked(directory / f"{screen}-before.json", 4096))
        if (observed.get("evidenceClass") != "E39D_REAL_CHROMIUM_FOCUS_EVIDENCE"
                or observed.get("caseId") != case
                or observed.get("phase") != "before"
                or observed.get("viewport") != screen
                or observed.get("passed") is not False
                or observed.get("failureCode") != FAULTS[case][1]
                or observed.get("pageJavaScriptErrors") != []
                or seed.get("beforeSourceSha256") != sha(checked(STYLE, 25000))):
            raise ValueError("E39D_INDEPENDENT_BROWSER_FAULT_NOT_VERIFIED_" + screen)
    print("E39D_TWO_REAL_CHROMIUM_FAILURES_CONFIRMED", case)


def restore(case: str) -> None:
    directory = location(case)
    verify_before(case)
    original = clean_source()
    source = checked(STYLE, 25000)
    manifest = json.loads(checked(SITE / "manifest.json", 4096))
    if (source != defective_source(case)
            or manifest.get("generatedFilesSha256", {}).get("styles.css") != sha(original)
            or directory.joinpath("receipt.json").exists()
            or directory.joinpath("receipt.json").is_symlink()):
        raise ValueError("E39D_CANONICAL_SOURCE_POLICY_DENIED")
    STYLE.write_bytes(original)
    if checked(STYLE, 25000) != original:
        raise ValueError("E39D_REPAIR_SOURCE_MISMATCH")
    receipt = {
        "evidenceClass": "E39D_DETERMINISTIC_TEST_RESTORE_NOT_AI",
        "caseId": case, "fromSha256": sha(source), "afterSha256": sha(original),
        "changedOnlyFixedGeneratedStylesheet": True,
        "modelWasCalled": False, "modelGeneratedRepair": False,
        "ownerApproved": False, "productionPublished": False,
    }
    with (directory / "receipt.json").open("x", encoding="utf-8") as fp:
        json.dump(receipt,fp,indent=2,sort_keys=True)
        fp.write("\n")
    print("E39D_DETERMINISTIC_RESTORE_INSIDE_NETWORK_NONE_WORKER", case)


def verify_after(case: str) -> None:
    directory = location(case)
    receipt = json.loads(checked(directory / "receipt.json", 4096))
    source = checked(STYLE, 25000)
    if (source != clean_source() or receipt.get("afterSha256") != sha(source)
            or receipt.get("modelWasCalled") is not False
            or receipt.get("modelGeneratedRepair") is not False
            or receipt.get("ownerApproved") is not False
            or receipt.get("productionPublished") is not False):
        raise ValueError("E39D_AFTER_SOURCE_OR_AUTHORITY_INVALID")
    for screen in ("mobile", "desktop"):
        observed = json.loads(checked(directory / f"{screen}-after.json", 4096))
        if (observed.get("evidenceClass") != "E39D_REAL_CHROMIUM_FOCUS_EVIDENCE"
                or observed.get("caseId") != case
                or observed.get("phase") != "after"
                or observed.get("viewport") != screen
                or observed.get("passed") is not True
                or observed.get("focusReached") is not True
                or observed.get("visibleWhenFocused") is not True
                or observed.get("failureCode") is not None
                or observed.get("pageJavaScriptErrors") != []):
            raise ValueError("E39D_AFTER_BROWSER_NOT_CONFIRMED_" + screen)
    print("E39D_TWO_REAL_CHROMIUM_AFTER_CHECKS_PASS_TEST_ONLY", case)


def main(args: list[str]) -> None:
    if len(args) != 2 or args[0] not in ("seed","before","restore","after"):
        raise ValueError("E39D_FIXED_EVALUATOR_COMMAND_REQUIRED")
    action, case = args
    if case not in FAULTS:
        raise ValueError("E39D_UNKNOWN_CASE")
    {"seed": seed, "before":verify_before, "restore":restore,
     "after":verify_after}[action](case)


if __name__ == "__main__":
    main(sys.argv[1:])
