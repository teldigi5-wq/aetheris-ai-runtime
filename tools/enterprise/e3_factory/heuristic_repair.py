#!/usr/bin/env python3
"""E3.3 deterministic, category-based repair heuristic. NO LLM, NO shell, NO network.
Proposer never imports evaluator cases or accesses canonical expected repair bytes.
A separate independent patch gate checks source hashes and final canonical output.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import re
import sys
import worker

BASE = worker.REPO / "target" / "e3-factory"
SITE = BASE / "site"
CURRENT = BASE / "e33" / "current"
CATEGORIES = {"scroll": ("main.js", "E32_SCROLL_PROGRESS_STUCK"),
              "mobile": ("styles.css", "E32_MOBILE_HORIZONTAL_OVERFLOW"),
              "reduced": ("main.js", "E32_REDUCED_MOTION_DISABLED")}
SCHEMA = {"version", "category", "path", "baseSha256", "resultSha256",
          "old", "new", "diagnosisSha256", "kind"}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def bounded_file(file: Path, limit: int) -> bytes:
    if file.is_symlink() or not file.is_file() or file.stat().st_size > limit:
        raise ValueError("E33_SOURCE_OR_EVIDENCE_NOT_REGULAR")
    return file.read_bytes()


def artifact(category: str) -> Path:
    if category not in CATEGORIES:
        raise ValueError("E33_CATEGORY_NOT_SUPPORTED")
    if SITE.is_symlink() or SITE.resolve(strict=True) != (
        worker.REPO.resolve(strict=True) / "target/e3-factory/site"):
        raise ValueError("E33_SITE_PATH_REJECTED")
    path = SITE / CATEGORIES[category][0]
    bounded_file(path, 25_000)
    return path


def diagnosis(category: str) -> bytes:
    if CURRENT.is_symlink() or CURRENT.resolve(strict=True) != (
        worker.REPO.resolve(strict=True) / "target/e3-factory/e33/current"):
        raise ValueError("E33_EVIDENCE_PATH_REJECTED")
    raw = bounded_file(CURRENT / "diagnosis.json", 4096)
    try:
        d = json.loads(raw)
    except (ValueError, UnicodeError) as exc:
        raise ValueError("E33_BAD_BROWSER_DIAGNOSIS") from exc
    if (not isinstance(d, dict)
        or d.get("evidenceClass") != "E32_REAL_CHROMIUM_FAULT_CONTRACT"
        or d.get("caseId") != category or d.get("passed") is not False
        or d.get("failureCode") != CATEGORIES[category][1]
        or not isinstance(d.get("browserObserved"), dict)
        or not d["browserObserved"]):
        raise ValueError("E33_BROWSER_DIAGNOSIS_NOT_MATCHED")
    return raw


def heuristic(category: str, source: str) -> tuple[str, str] | None:
    """Category-level heuristics, not per-fixture original strings."""
    if category == "scroll":
        if "function progress()" not in source:
            return None
        found = list(re.finditer(r'(?m)^[ \t]*const p = (?:0(?:\.\d+)?|1(?:\.0+)?)\s*;', source))
        if len(found) != 1:
            return None
        old = found[0].group()
        new = re.sub(r'const p = .*', 'const p = progress();', old)
        return old, new
    if category == "reduced":
        if 'const motion = window.matchMedia(' not in source:
            return None
        found = list(re.finditer(r'(?m)^[ \t]*const reduced = (?:false|Boolean\(false\));', source))
        if len(found) != 1:
            return None
        old = found[0].group()
        return old, old[:old.index("const reduced")] + "const reduced = motion.matches;"
    if category == "mobile":
        # The offending value is intentionally NOT embedded in this code.
        found = list(re.finditer(r'html\{min-width:(\d{3,4})px\}', source))
        if len(found) != 1 or not (760 < int(found[0].group(1)) <= 2000):
            return None
        if "@media(max-width:760px){" not in source:
            return None
        return found[0].group(), ""
    return None


def expected_result(source: bytes, old: str, new: str) -> bytes:
    text = source.decode("utf-8")
    if not old or text.count(old) != 1 or len(old) > 160 or len(new) > 160:
        raise ValueError("E33_PATCH_AMBIGUOUS_OR_TOO_LARGE")
    return text.replace(old, new, 1).encode("utf-8")


def proposal(category: str) -> dict:
    target = artifact(category)
    diagnostic = diagnosis(category)
    before = bounded_file(target, 25_000)
    finding = heuristic(category, before.decode("utf-8"))
    if finding is None:
        raise ValueError("E33_ABSTAIN_UNRECOGNIZED_DEFECT")
    old, new = finding
    after = expected_result(before, old, new)
    result = {
        "version": 1, "category": category,
        "path": "site/" + CATEGORIES[category][0],
        "baseSha256": digest(before), "resultSha256": digest(after),
        "old": old, "new": new, "diagnosisSha256": digest(diagnostic),
        "kind": "SINGLE_LITERAL_REPLACEMENT",
    }
    dest = CURRENT / "proposal.json"
    if dest.is_symlink():
        raise ValueError("E33_PROPOSAL_LINK_REJECTED")
    with dest.open("x", encoding="utf-8") as out:
        json.dump(result, out, sort_keys=True, indent=2)
        out.write("\n")
    return result


def apply(category: str) -> dict:
    target = artifact(category)
    diagnostic = diagnosis(category)
    raw = bounded_file(CURRENT / "proposal.json", 2048)
    try:
        candidate = json.loads(raw)
    except (ValueError, UnicodeError) as exc:
        raise ValueError("E33_PROPOSAL_BAD_JSON") from exc
    if (not isinstance(candidate, dict) or set(candidate) != SCHEMA
            or candidate.get("version") != 1
            or candidate.get("category") != category
            or candidate.get("path") != "site/" + CATEGORIES[category][0]
            or candidate.get("kind") != "SINGLE_LITERAL_REPLACEMENT"
            or candidate.get("diagnosisSha256") != digest(diagnostic)):
        raise ValueError("E33_UNTRUSTED_PATCH_REJECTED")
    original = bounded_file(target, 25_000)
    if candidate["baseSha256"] != digest(original):
        raise ValueError("E33_BASE_HASH_MISMATCH")
    # Independent syntax/semantic guard checks a small documented grammar.
    # It does NOT accept arbitrary code from a model or arbitrary diff hunks.
    source = original.decode("utf-8")
    old, new = candidate["old"], candidate["new"]
    if not isinstance(old, str) or not isinstance(new, str):
        raise ValueError("E33_UNTRUSTED_PATCH_REJECTED")
    if category == "scroll":
        permitted = bool(re.fullmatch(r'[ \t]*const p = (?:0(?:\.\d+)?|1(?:\.0+)?)\s*;', old)
                         and new == old[:old.index("const p")] + "const p = progress();"
                         and "function progress()" in source)
    elif category == "reduced":
        permitted = bool(re.fullmatch(r'[ \t]*const reduced = (?:false|Boolean\(false\));', old)
                         and new == old[:old.index("const reduced")] + "const reduced = motion.matches;"
                         and 'const motion = window.matchMedia(' in source)
    else:
        m = re.fullmatch(r'html\{min-width:(\d{3,4})px\}', old)
        permitted = bool(m and new == "" and 760 < int(m.group(1)) <= 2000
                         and "@media(max-width:760px){" in source)
    if not permitted:
        raise ValueError("E33_PATCH_GRAMMAR_DENIED")
    after = expected_result(original, old, new)
    if candidate["resultSha256"] != digest(after):
        raise ValueError("E33_OUTPUT_HASH_MISMATCH")
    # Separate frozen site manifest, generated before seeding, establishes the
    # final-good digest. Proposer does not consult this manifest.
    manifest = json.loads(bounded_file(SITE / "manifest.json", 4096))
    if digest(after) != manifest["generatedFilesSha256"][CATEGORIES[category][0]]:
        raise ValueError("E33_CANONICAL_OUTPUT_MISMATCH")
    target.write_bytes(after)
    if target.read_bytes() != after:
        raise ValueError("E33_POST_APPLY_MISMATCH")
    receipt = {
        "evidenceClass": "E33_HEURISTIC_PATCH_APPLIED_VERIFICATION_PENDING",
        "category": category, "beforeSha256": digest(original),
        "afterSha256": digest(after), "path": candidate["path"],
        "diagnosisSha256": digest(diagnostic),
        "untrustedProposalValidated": True, "ownerApproved": False,
        "productionPublished": False, "modelWasRun": False,
    }
    with (CURRENT / "receipt.json").open("x", encoding="utf-8") as out:
        json.dump(receipt, out, sort_keys=True, indent=2)
        out.write("\n")
    return receipt


def main(argv: list[str]) -> None:
    if len(argv) != 2 or argv[0] not in ("propose", "apply") or argv[1] not in CATEGORIES:
        raise ValueError("E33_UNSUPPORTED_PHASE_OR_CATEGORY")
    result = proposal(argv[1]) if argv[0] == "propose" else apply(argv[1])
    print("E33_" + argv[0].upper(), argv[1], result.get("evidenceClass", "TEXT_PROPOSAL_ONLY"))


if __name__ == "__main__":
    main(sys.argv[1:])
