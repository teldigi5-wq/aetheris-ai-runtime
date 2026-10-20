#!/usr/bin/env python3
"""E3.9F: ephemeral runtime-seeded, model-blind focus-challenge protocol.

The generated values are unknown until the disposable CI job starts. They are
NOT an externally curated private test set, and deterministic restoration is
NOT an AI model repair. A separate independent Chromium runner judges DOM.
"""
from __future__ import annotations

import hashlib
import hmac
import json
import re
import secrets
import sys
from pathlib import Path

import worker

ROOT = worker.REPO / "target/e3-factory"
SITE = ROOT / "site"
STYLE = SITE / "styles.css"
OUT = ROOT / "e39f-blind"
PRIVATE = OUT / "private.json"
CANONICAL = worker.HERE / "styles.css"
ORIGINAL = ".skip-link:focus{top:12px}"
FAMILIES = ("top", "left", "opacity", "visibility", "transformY",
            "transformX", "top", "left")
CASE_IDS = tuple(f"challenge-{idx:02d}" for idx in range(1, 9))
FAILURE = {"top":"E39D_FOCUS_OFFSCREEN",
           "left":"E39D_FOCUS_OFFSCREEN",
           "opacity":"E39D_FOCUS_TRANSPARENT",
           "visibility":"E39D_FOCUS_NOT_REACHABLE",
           "transformY":"E39D_FOCUS_OFFSCREEN",
           "transformX":"E39D_FOCUS_OFFSCREEN"}


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def checked(path: Path, limit: int) -> bytes:
    if path.is_symlink() or not path.is_file() or path.stat().st_size > limit:
        raise ValueError("E39F_INVALID_EVIDENCE_PATH")
    return path.read_bytes()


def read_json(path: Path, limit: int = 4096) -> dict:
    raw = json.loads(checked(path, limit))
    if not isinstance(raw, dict):
        raise ValueError("E39F_INVALID_JSON_DOCUMENT")
    return raw


def clean_source() -> bytes:
    src = checked(CANONICAL, 25000)
    if src.count(ORIGINAL.encode()) != 1:
        raise ValueError("E39F_CANONICAL_FOCUS_RULE_NOT_UNIQUE")
    return src


def manifest() -> dict:
    data = read_json(PRIVATE, 20000)
    if data.get("class") != "E39F_RUNTIME_EPHEMERAL_CHALLENGE_PRIVATE_V1":
        raise ValueError("E39F_MANIFEST_CLASS_INVALID")
    if data.get("canonicalSha256") != sha(clean_source()):
        raise ValueError("E39F_CANONICAL_MANIFEST_DRIFT")
    if set(data.get("cases", {})) != set(CASE_IDS):
        raise ValueError("E39F_INCOMPLETE_CHALLENGE_SET")
    return data


def generated(case: str, data: dict | None = None) -> bytes:
    if case not in CASE_IDS:
        raise ValueError("E39F_CASE_NOT_ALLOWED")
    data = data or manifest()
    source = clean_source()
    entry = data["cases"][case]
    rule = entry["defectiveRule"]
    if not isinstance(rule, str) or not re.fullmatch(r"\.skip-link:focus\{[^{}]{1,96}\}", rule):
        raise ValueError("E39F_GENERATED_RULE_UNSAFE")
    return source.replace(ORIGINAL.encode(), rule.encode(), 1)


def derive(seed: bytes, case: str, label: str, low: int, high: int) -> int:
    value = int.from_bytes(hmac.digest(seed, (case + ":" + label).encode(), "sha256")[:8], "big")
    return low + value % (high - low + 1)


def generate_rules(seed: bytes) -> dict:
    if len(seed) != 32:
        raise ValueError("E39F_SEED_LENGTH_INVALID")
    results = {}
    for case, family in zip(CASE_IDS, FAMILIES):
        if family == "top":
            value = derive(seed, case, family, 36, 190)
            rule = f".skip-link:focus{{top:-{value}px}}"
        elif family == "left":
            value = derive(seed, case, family, 120, 360)
            rule = f".skip-link:focus{{top:12px;left:-{value}px}}"
        elif family == "opacity":
            rule = ".skip-link:focus{top:12px;opacity:0}"
        elif family == "visibility":
            rule = ".skip-link:focus{top:12px;visibility:hidden}"
        elif family == "transformY":
            value = derive(seed, case, family, 120, 360)
            rule = f".skip-link:focus{{top:12px;transform:translateY(-{value}px)}}"
        else:
            value = derive(seed, case, family, 200, 420)
            rule = f".skip-link:focus{{top:12px;transform:translateX(-{value}px)}}"
        results[case] = {"family":family, "defectiveRule":rule,
                         "expectedFailure":FAILURE[family]}
    return results


def initialize() -> None:
    if OUT.exists() or OUT.is_symlink():
        raise ValueError("E39F_SEALED_EVIDENCE_ALREADY_EXISTS")
    canonical = clean_source()
    if checked(STYLE, 25000) != canonical:
        raise ValueError("E39F_INITIAL_SOURCE_NOT_CANONICAL")
    site_manifest = read_json(SITE / "manifest.json")
    if site_manifest.get("generatedFilesSha256", {}).get("styles.css") != sha(canonical):
        raise ValueError("E39F_ORIGINAL_SITE_SHA_NOT_BOUND")
    seed = secrets.token_bytes(32)
    private = {"class":"E39F_RUNTIME_EPHEMERAL_CHALLENGE_PRIVATE_V1",
               "seedHex":seed.hex(), "seedCommitmentSha256":sha(seed),
               "canonicalSha256":sha(canonical), "cases":generate_rules(seed),
               "modelScored":False, "ownerApproved":False, "productionPublished":False}
    OUT.mkdir(mode=0o700)
    PRIVATE.write_text(json.dumps(private, sort_keys=True, indent=2) + "\n")
    PRIVATE.chmod(0o600)
    print("E39F_RUNTIME_SEED_COMMITMENT", sha(seed))
    print("E39F_8_RUNTIME_GENERATED_UNSEEN_CASES; NO_MODEL_CALL")


def directory(case: str) -> Path:
    if case not in CASE_IDS:
        raise ValueError("E39F_BAD_CASE")
    return OUT / case


def seed_case(case: str) -> None:
    data = manifest()
    folder = directory(case)
    if folder.exists() or folder.is_symlink():
        raise ValueError("E39F_CHALLENGE_ALREADY_STARTED")
    if checked(STYLE, 25000) != clean_source():
        raise ValueError("E39F_SOURCE_NOT_RESET")
    broken = generated(case, data)
    folder.mkdir(mode=0o700)
    STYLE.write_bytes(broken)
    receipt = {"class":"E39F_RUNTIME_GENERATED_BLINDED_PARAMETER_CASE",
               "caseId":case, "sourceSha256":sha(broken),
               "canonicalSha256":sha(clean_source()),
               "commitmentSha256":data["seedCommitmentSha256"],
               "modelGeneratedRepair":False, "ownerApproved":False}
    (folder/"seed.json").write_text(json.dumps(receipt, sort_keys=True, indent=2)+"\n")
    print("E39F_CHALLENGE_SEEDED_WITHHELD_VALUE", case, sha(broken))


def verify_before(case: str) -> None:
    data = manifest()
    seed = read_json(directory(case)/"seed.json")
    if (seed.get("sourceSha256") != sha(generated(case, data))
            or checked(STYLE, 25000) != generated(case, data)):
        raise ValueError("E39F_BEFORE_SOURCE_DRIFT")
    for viewport in ("mobile","desktop"):
        p=read_json(directory(case)/f"{viewport}-before.json")
        if (p.get("evidenceClass") != "E39F_REAL_CHROMIUM_BLINDED_CASE"
                or p.get("caseId") != case or p.get("viewport") != viewport
                or p.get("phase") != "before" or p.get("passed") is not False
                or p.get("failureCode") != data["cases"][case]["expectedFailure"]
                or p.get("pageJavaScriptErrors") != []):
            raise ValueError("E39F_BEFORE_BROWSER_NOT_OBSERVED")
    print("E39F_TWO_REAL_CHROMIUM_BEFORE_FAILURES",case)


def restore_fixture(case: str) -> None:
    """A network-none deterministic HARNESS control; NEVER an AI repair."""
    verify_before(case)
    target=directory(case)/"test-only-restoration.json"
    if target.exists() or target.is_symlink():
        raise ValueError("E39F_ALREADY_RESTORED")
    original=checked(STYLE, 25000)
    STYLE.write_bytes(clean_source())
    receipt={"class":"E39F_NON_MODEL_TEST_ONLY_RESTORATION",
             "caseId":case, "sourceBeforeSha256":sha(original),
             "sourceAfterSha256":sha(clean_source()),
             "modelGeneratedRepair":False,"modelCalls":0,
             "ownerApproved":False,"productionPublished":False}
    target.write_text(json.dumps(receipt,sort_keys=True,indent=2)+"\n")
    print("E39F_TEST_FIXTURE_ONLY_ZERO_MODEL_CREDIT",case)


def verify_after(case: str) -> None:
    receipt=read_json(directory(case)/"test-only-restoration.json")
    if (receipt.get("class") != "E39F_NON_MODEL_TEST_ONLY_RESTORATION"
            or receipt.get("modelGeneratedRepair") is not False
            or receipt.get("modelCalls") != 0
            or checked(STYLE,25000) != clean_source()):
        raise ValueError("E39F_AFTER_SOURCE_NOT_VALIDATED")
    for viewport in ("mobile","desktop"):
        p=read_json(directory(case)/f"{viewport}-after.json")
        if (p.get("evidenceClass") != "E39F_REAL_CHROMIUM_BLINDED_CASE"
                or p.get("caseId") != case or p.get("viewport") != viewport
                or p.get("phase") != "after" or p.get("passed") is not True
                or p.get("visibleWhenFocused") is not True
                or p.get("focusReached") is not True
                or p.get("pageJavaScriptErrors") != []):
            raise ValueError("E39F_AFTER_BROWSER_NOT_VERIFIED")
    print("E39F_INDEPENDENT_REAL_CHROMIUM_RESTORATION_PROOF_NO_MODEL",case)


def finalize() -> None:
    data=manifest()
    for case in CASE_IDS:
        verify_after(case)
    seed=bytes.fromhex(data["seedHex"])
    if sha(seed) != data["seedCommitmentSha256"] or generate_rules(seed) != data["cases"]:
        raise ValueError("E39F_REPRODUCIBILITY_COMMITMENT_MISMATCH")
    post={"class":"E39F_POST_TEST_REVEAL_FOR_REPRODUCIBILITY",
          "seedHex":seed.hex(), "commitmentSha256":sha(seed),
          "cases":CASE_IDS, "realChromiumBeforeAfterPasses":16,
          "browserVerdictsTotal":32,
          "realModelCalls":0, "modelGeneratedRepairs":0,
          "externallyCuratedSealedCorpus":False,
          "productionReady":False}
    (OUT/"post-test-reveal.json").write_text(json.dumps(post,indent=2,sort_keys=True)+"\n")
    print("E39F_ALL_8_EPHEMERAL_SEEDED_CASES_BROWSER_PROVEN_FIXTURE_NOT_AI")


if __name__ == "__main__":
    args=sys.argv[1:]
    if args == ["init"]:
        initialize()
    elif args == ["finalize"]:
        finalize()
    elif len(args)==2 and args[1] in CASE_IDS:
        {"seed":seed_case,"before":verify_before,"restore":restore_fixture,
         "after":verify_after}[args[0]](args[1])
    else:
        raise ValueError("E39F_EXACT_COMMAND_AND_CASE_REQUIRED")
