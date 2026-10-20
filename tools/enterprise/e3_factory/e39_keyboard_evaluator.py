#!/usr/bin/env python3
"""E3.9A new keyboard accessibility defect; independent test fixture, NO LLM.

Proof-of-failure and canonical original source stay in the evaluator, not in the
untrusted proposal gate. CI-only disposable generated website; never owner PC.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import sys
import worker

BASE = worker.REPO / "target/e3-factory"
SITE = BASE / "site"
DIR = BASE / "e39"
STYLE = SITE / "styles.css"
CANONICAL = worker.HERE / "styles.css"
ORIGINAL = ".skip-link:focus{top:12px}"
BROKEN = ".skip-link:focus{top:-80px}"


def sha(x: bytes) -> str:
    return hashlib.sha256(x).hexdigest()


def regular(path: Path, limit: int) -> bytes:
    if path.is_symlink() or not path.is_file() or path.stat().st_size > limit:
        raise ValueError("E39_UNSAFE_FILE")
    return path.read_bytes()


def seed() -> None:
    if SITE.is_symlink() or DIR.is_symlink() or DIR.exists():
        raise ValueError("E39_UNSAFE_SEED_DESTINATION")
    source = regular(CANONICAL, 25_000)
    if regular(STYLE, 25_000) != source:
        raise ValueError("E39_MUST_START_WITH_CANONICAL_WEBSITE")
    original = source.decode("utf-8")
    if original.count(ORIGINAL) != 1 or BROKEN in original:
        raise ValueError("E39_REFERENCE_FIXTURE_INCONSISTENT")
    mutated = original.replace(ORIGINAL, BROKEN, 1).encode("utf-8")
    DIR.mkdir(mode=0o700)
    STYLE.write_bytes(mutated)
    report = {"evidenceClass": "E39_INDEPENDENT_KEYBOARD_SEED_CI_ONLY",
              "caseId": "keyboard-skip-link", "expectedFailure": "E39_FOCUSED_SKIP_LINK_OFFSCREEN",
              "beforeSha256": sha(mutated), "expectedCanonicalSha256": sha(source),
              "modelWasCalled": False, "ownerPcUsed": False, "deployed": False}
    (DIR / "seed.json").write_text(json.dumps(report, sort_keys=True, indent=2)+"\n",encoding="utf-8")
    print("E39_NEW_KEYBOARD_DEFECT_SEEDED", sha(mutated))


def before() -> None:
    seed = json.loads(regular(DIR / "seed.json", 4096))
    proof = json.loads(regular(DIR / "browser-before.json", 4096))
    if (proof.get("evidenceClass") != "E39_CHROMIUM_KEYBOARD_ACCESSIBILITY"
            or proof.get("caseId") != "keyboard-skip-link"
            or proof.get("passed") is not False
            or proof.get("failureCode") != "E39_FOCUSED_SKIP_LINK_OFFSCREEN"
            or proof.get("focusReached") is not True
            or not isinstance(proof.get("geometry"), dict)
            or proof["geometry"].get("top", 0) >= 0
            or seed.get("beforeSha256") != sha(regular(STYLE, 25_000))):
        raise ValueError("E39_REAL_BROWSER_FAILURE_NOT_VERIFIED")
    print("E39_INDEPENDENT_BROWSER_FAILURE_VERIFIED")


def after() -> None:
    proof = json.loads(regular(DIR / "browser-after.json", 4096))
    source = regular(CANONICAL, 25_000)
    if (proof.get("evidenceClass") != "E39_CHROMIUM_KEYBOARD_ACCESSIBILITY"
            or proof.get("passed") is not True
            or proof.get("failureCode") is not None
            or proof.get("focusReached") is not True
            or proof.get("visibleWhenFocused") is not True
            or regular(STYLE, 25_000) != source):
        raise ValueError("E39_BROWSER_OR_SOURCE_NOT_FIXED")
    receipt = json.loads(regular(DIR / "receipt.json", 4096))
    if (receipt.get("ownerApproved") is not False
            or receipt.get("modelWasCalled") is not False
            or receipt.get("productionPublished") is not False
            or receipt.get("afterSha256") != sha(source)):
        raise ValueError("E39_RECEIPT_INVALID")
    print("E39_REAL_CHROMIUM_KEYBOARD_REPAIR_VERIFIED_FIXTURE_ONLY")


def main(args: list[str]) -> None:
    if args == ["seed"]:
        seed()
    elif args == ["before"]:
        before()
    elif args == ["after"]:
        after()
    else:
        raise ValueError("E39_ONLY_SEED_BEFORE_AFTER_ALLOWED")


if __name__ == "__main__":
    main(sys.argv[1:])
