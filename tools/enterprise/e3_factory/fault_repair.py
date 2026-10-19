#!/usr/bin/env python3
"""E3.1 CI-only single-fault repair. Never accepts arbitrary file paths or patches."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys

import worker

ROOT = worker.REPO / "target" / "e3-factory"
SITE = ROOT / "site"
EVIDENCE = ROOT / "repair-evidence"
SOURCE = worker.HERE / "main.js"
MARKER = "const p = progress();"
BROKEN = "const p = 0; // E31_SEEDED_SCROLL_FAULT"
ALLOWED_PHASES = {"seed", "repair"}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def check_target() -> Path:
    if SITE.is_symlink() or ROOT.is_symlink() or not SITE.is_dir():
        raise ValueError("E31_SITE_MISSING_OR_LINKED")
    if SITE.resolve(strict=True) != worker.REPO.resolve(strict=True) / "target" / "e3-factory" / "site":
        raise ValueError("E31_SITE_ESCAPE")
    script = SITE / "main.js"
    if script.is_symlink() or not script.is_file():
        raise ValueError("E31_SCRIPT_MISSING_OR_LINKED")
    return script


def seed(site_file: Path, evidence_dir: Path) -> dict:
    """Produce exactly one reproducible browser-detectable fault."""
    if site_file.is_symlink() or not site_file.is_file():
        raise ValueError("E31_SCRIPT_MISSING_OR_LINKED")
    original = SOURCE.read_bytes()
    actual = site_file.read_bytes()
    if actual != original or actual.decode("utf-8").count(MARKER) != 1:
        raise ValueError("E31_SOURCE_PRECONDITION_FAILED")
    if evidence_dir.is_symlink() or evidence_dir.exists():
        raise ValueError("E31_EVIDENCE_ALREADY_EXISTS_OR_LINKED")
    evidence_dir.mkdir(parents=True)
    modified = actual.decode("utf-8").replace(MARKER, BROKEN, 1).encode("utf-8")
    if modified.count(BROKEN.encode("utf-8")) != 1:
        raise ValueError("E31_FAULT_NOT_UNIQUE")
    site_file.write_bytes(modified)
    report = {
        "phase": "FAULT_SEEDED",
        "testOnly": True,
        "allowedFile": "site/main.js",
        "expectedFailure": "Late progress not near end",
        "originalSha256": digest(original),
        "brokenSha256": digest(modified),
        "humanOwnerApproved": False,
        "productionDeployed": False,
    }
    (evidence_dir / "seed.json").write_text(
        json.dumps(report, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    return report


def repair(site_file: Path, evidence_dir: Path) -> dict:
    """Only reverse our exact seeded failure, with byte-accurate preconditions."""
    if site_file.is_symlink() or not site_file.is_file():
        raise ValueError("E31_SCRIPT_MISSING_OR_LINKED")
    seed_file = evidence_dir / "seed.json"
    if evidence_dir.is_symlink() or seed_file.is_symlink() or not seed_file.is_file():
        raise ValueError("E31_SEED_EVIDENCE_MISSING_OR_LINKED")
    evidence = json.loads(seed_file.read_text("utf-8"))
    diagnosis_file = evidence_dir / "diagnosis.json"
    if diagnosis_file.is_symlink() or not diagnosis_file.is_file():
        raise ValueError("E31_BROWSER_DIAGNOSIS_REQUIRED")
    diagnosis = json.loads(diagnosis_file.read_text("utf-8"))
    if (diagnosis.get("evidenceClass") != "REAL_CHROMIUM_QA_CI_ONLY_NOT_PRODUCTION"
            or diagnosis.get("passed") is not False
            or evidence["expectedFailure"] not in str(diagnosis.get("failure", ""))):
        raise ValueError("E31_WRONG_BROWSER_FAILURE")
    original = SOURCE.read_bytes()
    broken = original.decode("utf-8").replace(MARKER, BROKEN, 1).encode("utf-8")
    if (original.decode("utf-8").count(MARKER) != 1
            or evidence["originalSha256"] != digest(original)
            or evidence["brokenSha256"] != digest(broken)
            or digest(site_file.read_bytes()) != digest(broken)):
        raise ValueError("E31_UNAPPROVED_PATCH_OR_DRIFT")
    site_file.write_bytes(original)
    if site_file.read_bytes() != original:
        raise ValueError("E31_REPAIR_NOT_BYTE_EXACT")
    repaired = {
        "phase": "DETERMINISTIC_REPAIR_APPLIED",
        "testOnly": True,
        "allowedFile": "site/main.js",
        "detectedFailure": evidence["expectedFailure"],
        "brokenSha256": evidence["brokenSha256"],
        "repairedSha256": digest(original),
        "sourceRestoredExactly": True,
        "ownerAuthenticated": False,
        "approvalGranted": False,
        "agentExecuted": False,
        "productionDeployed": False,
    }
    (evidence_dir / "repair.json").write_text(
        json.dumps(repaired, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    return repaired


def main(argv: list[str]) -> None:
    if len(argv) != 1 or argv[0] not in ALLOWED_PHASES:
        raise ValueError("E31_ONLY_SEED_OR_REPAIR_ALLOWED")
    file = check_target()
    report = seed(file, EVIDENCE) if argv[0] == "seed" else repair(file, EVIDENCE)
    print("E31_" + report["phase"], report["allowedFile"])


if __name__ == "__main__":
    main(sys.argv[1:])
