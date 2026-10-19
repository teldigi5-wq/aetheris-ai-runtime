#!/usr/bin/env python3
"""Independent E3.3 test evaluator. Fixtures are NOT imported by heuristic_repair.
These six SOURCE variations are held out from the proposer, not cryptographically
hidden from anyone who can inspect CI source. No model is called.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import shutil
import sys
import worker

HERE = Path(__file__).resolve().parent
BASE = worker.REPO / "target" / "e3-factory"
CATEGORIES = {"scroll": "E32_SCROLL_PROGRESS_STUCK",
              "mobile": "E32_MOBILE_HORIZONTAL_OVERFLOW",
              "reduced": "E32_REDUCED_MOTION_DISABLED"}
CASES = {
    "scroll-00": ("scroll", "main.js", "const p = progress();", "const p = 0.0;"),
    "scroll-24": ("scroll", "main.js", "const p = progress();", "const p = 0.24;"),
    "mobile-940": ("mobile", "styles.css", "@media(max-width:760px){",
                   "@media(max-width:760px){html{min-width:940px}"),
    "mobile-1024": ("mobile", "styles.css", "@media(max-width:760px){",
                    "@media(max-width:760px){html{min-width:1024px}"),
    "reduced-false": ("reduced", "main.js", "const reduced = motion.matches;",
                     "const reduced = false;"),
    "reduced-boolean": ("reduced", "main.js", "const reduced = motion.matches;",
                       "const reduced = Boolean(false);"),
}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def paths(case: str) -> tuple[str, Path, Path, Path]:
    if case not in CASES:
        raise ValueError("E33_EVALUATOR_CASE_UNKNOWN")
    category, filename, _, _ = CASES[case]
    src = BASE / "site" / filename
    current = BASE / "e33" / "current"
    evidence = BASE / "e33" / case
    if (src.is_symlink() or not src.is_file() or current.is_symlink()
            or evidence.is_symlink() or (BASE / "e33").is_symlink()):
        raise ValueError("E33_EVALUATOR_UNSAFE_PATH")
    return category, src, current, evidence


def seed(case: str) -> None:
    category, file, current, evidence = paths(case)
    _, filename, marker, corrupt = CASES[case]
    source = (HERE / filename).read_bytes()
    present = file.read_bytes()
    if present != source:
        raise ValueError("E33_EVALUATOR_REQUIRES_CLEAN_SOURCE")
    clean = present.decode("utf-8")
    if clean.count(marker) != 1:
        raise ValueError("E33_EVALUATOR_AMBIGUOUS_MARKER")
    patched = clean.replace(marker, corrupt, 1).encode("utf-8")
    if current.exists():
        shutil.rmtree(current)
    current.mkdir(parents=True)
    if evidence.exists():
        raise ValueError("E33_CASE_ALREADY_SEEDED")
    evidence.mkdir(parents=True)
    (BASE / "e32" / category).mkdir(parents=True, exist_ok=True)
    file.write_bytes(patched)
    report = {
        "caseId": case, "category": category,
        "beforeSha256": digest(patched), "expectedCleanSha256": digest(source),
        "evidenceClass": "E33_INDEPENDENT_EVALUATOR_SEEDED",
        "proposerHasExpectedPatchTable": False, "modelRun": False,
    }
    (evidence / "seed.json").write_text(json.dumps(report, indent=2) + "\n", "utf-8")
    print("E33_HELDOUT_VARIANT_SEEDED", case)


def check_before(case: str) -> None:
    category, file, current, evidence = paths(case)
    report_file = BASE / "e32" / category / "diagnosis.json"
    record = json.loads(report_file.read_text("utf-8"))
    if (record.get("evidenceClass") != "E32_REAL_CHROMIUM_FAULT_CONTRACT"
            or record.get("caseId") != category or record.get("passed") is not False
            or record.get("failureCode") != CATEGORIES[category]
            or not record.get("browserObserved")):
        raise ValueError("E33_EXPECTED_BROWSER_FAILURE_ABSENT")
    shutil.copy2(report_file, current / "diagnosis.json")
    shutil.copy2(report_file, evidence / "browser-before.json")
    screenshot = BASE / "e32" / category / "before-browser.png"
    if screenshot.is_file():
        shutil.copy2(screenshot, evidence / "before-browser.png")
    print("E33_EXPECTED_REAL_CHROMIUM_FAILURE", case, record["failureCode"])


def check_after(case: str) -> None:
    category, file, current, evidence = paths(case)
    observation = json.loads((BASE / "e32" / category / "diagnosis.json").read_text("utf-8"))
    if observation.get("passed") is not True or observation.get("failureCode") is not None:
        raise ValueError("E33_BROWSER_REPAIR_NOT_VERIFIED")
    original = (HERE / CASES[case][1]).read_bytes()
    if file.read_bytes() != original:
        raise ValueError("E33_SOURCE_NOT_CANONICAL")
    receipt = json.loads((current / "receipt.json").read_text("utf-8"))
    if (receipt.get("afterSha256") != digest(original)
            or receipt.get("modelWasRun") is not False
            or receipt.get("ownerApproved") is not False):
        raise ValueError("E33_REPAIR_RECEIPT_INVALID")
    shutil.copy2(current / "proposal.json", evidence / "proposal.json")
    shutil.copy2(current / "receipt.json", evidence / "receipt.json")
    shutil.copy2(observation_file := BASE / "e32" / category / "diagnosis.json",
                 evidence / "browser-after.json")
    screenshot = BASE / "e32" / category / "after-browser.png"
    if screenshot.is_file():
        shutil.copy2(screenshot, evidence / "after-browser.png")
    print("E33_BROWSER_VERIFIED_CANONICAL_REPAIR", case)


def main(argv: list[str]) -> None:
    if len(argv) != 2 or argv[0] not in ("seed", "before", "after") or argv[1] not in CASES:
        raise ValueError("E33_EVALUATOR_FIXED_PHASE_AND_CASE_ONLY")
    {"seed": seed, "before": check_before, "after": check_after}[argv[0]](argv[1])


if __name__ == "__main__":
    main(sys.argv[1:])
