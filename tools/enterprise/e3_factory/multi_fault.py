#!/usr/bin/env python3
"""E3.2 deterministic multi-fault source patch gate. All proposals are untrusted."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys

import worker

HERE = Path(__file__).resolve().parent
SITE = worker.TARGET
ROOT = worker.REPO / "target" / "e3-factory" / "e32"
CASES = {
    "scroll": {
        "filename": "main.js", "old": "const p = progress();",
        "broken": "const p = 0; /* E32_SCROLL_FAULT */",
        "failureCode": "E32_SCROLL_PROGRESS_STUCK",
    },
    "mobile": {
        "filename": "styles.css", "old": "@media(max-width:760px){",
        "broken": "@media(max-width:760px){html{min-width:850px}",
        "failureCode": "E32_MOBILE_HORIZONTAL_OVERFLOW",
    },
    "reduced": {
        "filename": "main.js", "old": "const reduced = motion.matches;",
        "broken": "const reduced = false; /* E32_REDUCED_FAULT */",
        "failureCode": "E32_REDUCED_MOTION_DISABLED",
    },
}


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def fixed_source(case: str) -> tuple[dict, bytes, bytes]:
    if case not in CASES:
        raise ValueError("E32_CASE_NOT_ALLOWLISTED")
    rule = CASES[case]
    source = (HERE / rule["filename"]).read_bytes()
    text = source.decode("utf-8")
    if text.count(rule["old"]) != 1 or rule["broken"] in text:
        raise ValueError("E32_CANONICAL_SOURCE_DRIFT")
    broken = text.replace(rule["old"], rule["broken"], 1).encode("utf-8")
    return rule, source, broken


def target_path(case: str) -> Path:
    rule, _, _ = fixed_source(case)
    if SITE.is_symlink() or not SITE.is_dir():
        raise ValueError("E32_WORKSPACE_NOT_SAFE")
    if SITE.resolve(strict=True) != worker.REPO.resolve(strict=True) / "target/e3-factory/site":
        raise ValueError("E32_WORKSPACE_ESCAPE")
    target = SITE / rule["filename"]
    if target.is_symlink() or not target.is_file():
        raise ValueError("E32_TARGET_NOT_REGULAR")
    return target


def checked_evidence(case: str) -> Path:
    if ROOT.is_symlink():
        raise ValueError("E32_EVIDENCE_SYMLINK")
    target = ROOT / case
    if target.is_symlink():
        raise ValueError("E32_EVIDENCE_SYMLINK")
    return target


def expected_proposal(case: str, clean: bytes, broken: bytes, rule: dict) -> dict:
    # No arbitrary paths, full-file replacements, regex, scripts, shell or open-ended diffs.
    return {
        "schemaVersion": 1, "caseId": case,
        "path": "site/" + rule["filename"],
        "baseSha256": sha(broken), "outputSha256": sha(clean),
        "find": rule["broken"], "replace": rule["old"],
        "failureCode": rule["failureCode"], "operation": "ONE_LITERAL_REPLACEMENT",
    }


def seed(case: str, target: Path, evidence_dir: Path) -> dict:
    rule, clean, broken = fixed_source(case)
    if target.is_symlink() or not target.is_file() or target.read_bytes() != clean:
        raise ValueError("E32_SEED_UNEXPECTED_BASE")
    if evidence_dir.is_symlink() or evidence_dir.exists():
        raise ValueError("E32_CASE_ALREADY_SEEDED")
    evidence_dir.mkdir(parents=True)
    proposal = expected_proposal(case, clean, broken, rule)
    target.write_bytes(broken)
    record = {
        "evidenceClass": "E32_FROZEN_BUG_SEEDED_NOT_BROWSER_PROVEN",
        "caseId": case, "file": proposal["path"],
        "cleanSha256": sha(clean), "brokenSha256": sha(broken),
        "expectedBrowserFailure": rule["failureCode"],
        "ownerApprovalGranted": False, "modelPatchProduced": False,
    }
    (evidence_dir / "seed.json").write_text(json.dumps(record, sort_keys=True, indent=2) + "\n", "utf-8")
    (evidence_dir / "proposal.json").write_text(json.dumps(proposal, sort_keys=True, indent=2) + "\n", "utf-8")
    return record


def repair(case: str, target: Path, evidence_dir: Path) -> dict:
    rule, clean, broken = fixed_source(case)
    if target.is_symlink() or not target.is_file() or evidence_dir.is_symlink():
        raise ValueError("E32_UNSAFE_REPAIR_TARGET")
    diagnosis = evidence_dir / "diagnosis.json"
    proposal_file = evidence_dir / "proposal.json"
    seed_file = evidence_dir / "seed.json"
    files = [diagnosis, proposal_file, seed_file]
    if any(path.is_symlink() or not path.is_file() for path in files):
        raise ValueError("E32_REQUIRED_EVIDENCE_MISSING_OR_LINKED")
    if any(path.stat().st_size > 4096 for path in files):
        raise ValueError("E32_EVIDENCE_SIZE_EXCEEDED")
    try:
        proposal = json.loads(proposal_file.read_text("utf-8"))
        observed = json.loads(diagnosis.read_text("utf-8"))
        initial = json.loads(seed_file.read_text("utf-8"))
    except (ValueError, UnicodeError) as exc:
        raise ValueError("E32_EVIDENCE_INVALID") from exc
    expected = expected_proposal(case, clean, broken, rule)
    if not isinstance(proposal, dict) or proposal != expected:
        raise ValueError("E32_UNTRUSTED_PATCH_REJECTED")
    if (not isinstance(initial, dict)
            or initial.get("caseId") != case
            or initial.get("brokenSha256") != sha(broken)
            or initial.get("cleanSha256") != sha(clean)):
        raise ValueError("E32_SEED_EVIDENCE_MISMATCH")
    if (not isinstance(observed, dict)
            or observed.get("evidenceClass") != "E32_REAL_CHROMIUM_FAULT_CONTRACT"
            or observed.get("caseId") != case or observed.get("passed") is not False
            or observed.get("failureCode") != rule["failureCode"]):
        raise ValueError("E32_BROWSER_DIAGNOSIS_MISMATCH")
    if target.read_bytes() != broken or sha(target.read_bytes()) != proposal["baseSha256"]:
        raise ValueError("E32_UNAPPROVED_SOURCE_DRIFT")
    # Only the canonical code can be restored. Any other candidate is denied.
    target.write_bytes(clean)
    if target.read_bytes() != clean:
        raise ValueError("E32_REPAIR_POSTCONDITION_FAILED")
    result = {
        "evidenceClass": "E32_DETERMINISTIC_PATCH_GATE_REPAIRED",
        "caseId": case, "allowedFile": expected["path"],
        "detectedFailure": rule["failureCode"],
        "beforeSha256": sha(broken), "afterSha256": sha(clean),
        "sourceRestoredExactly": True, "untrustedProposalAcceptedByExactAllowlist": True,
        "modelProposedPatch": False, "ownerAuthenticated": False,
        "ownerApprovalGranted": False, "externalExecutionReleased": False,
        "productionPublished": False,
    }
    (evidence_dir / "repair.json").write_text(json.dumps(result, sort_keys=True, indent=2) + "\n", "utf-8")
    return result


def main(args: list[str]) -> None:
    if len(args) != 2 or args[0] not in ("seed", "repair") or args[1] not in CASES:
        raise ValueError("E32_ONLY_FROZEN_PHASE_AND_CASE_ALLOWED")
    phase, case = args
    target = target_path(case)
    evidence = checked_evidence(case)
    if phase == "seed":
        if ROOT.exists() and not ROOT.is_dir():
            raise ValueError("E32_EVIDENCE_ROOT_INVALID")
        ROOT.mkdir(parents=True, exist_ok=True)
        result = seed(case, target, evidence)
    else:
        result = repair(case, target, evidence)
    print("E32_" + phase.upper() + "_COMPLETE", case, result["evidenceClass"])


if __name__ == "__main__":
    main(sys.argv[1:])
