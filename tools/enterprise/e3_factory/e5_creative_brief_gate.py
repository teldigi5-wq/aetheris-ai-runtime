#!/usr/bin/env python3
"""E5 independent creative-brief constraint check for OFFLINE COPY REVIEW ONLY.

Inputs are two exact local JSON files. Neither a human brief nor model copy is
authenticated by this tool. Matching words do NOT establish accuracy,
authorship, aesthetic merit, accessibility or approval. No model is invoked.
No network, deployment, source modification or API is available.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import stat
import sys
from pathlib import Path

import e5_reference_preview as reference
import e5_untrusted_copy_preview as preview

ROOT = reference.PROJECT
FOLDER = ROOT / "target" / "e5"
BRIEF = FOLDER / "creative-brief.json"
PROPOSAL = FOLDER / "copy-proposal.json"
ZIP_OUT = FOLDER / "brief-gated-copy-preview.zip"
AUDIT_OUT = FOLDER / "brief-gated-copy-evidence.json"
MAX_BRIEF_BYTES = 1200
BRIEF_KEYS = {"version", "brandLabel", "requiredTerms", "forbiddenTerms"}
WORD = re.compile(r"[a-z][a-z-]{2,19}\Z")
MAX_TERMS = 5


def _unique(pairs: list[tuple[str, object]]) -> dict:
    result: dict = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("E5_BRIEF_DUPLICATE_FIELD")
        result[key] = value
    return result


def _terms(value: object, *, allow_empty: bool) -> list[str]:
    if not isinstance(value, list) or len(value) > MAX_TERMS:
        raise ValueError("E5_BRIEF_TERM_COUNT")
    if not allow_empty and not value:
        raise ValueError("E5_BRIEF_REQUIRES_TERM")
    if any(not isinstance(item, str) or not WORD.fullmatch(item) for item in value):
        raise ValueError("E5_BRIEF_TERM_FORMAT")
    if len(value) != len(set(value)):
        raise ValueError("E5_BRIEF_DUPLICATE_TERM")
    return value


def read_brief(raw: bytes) -> dict:
    if not raw or len(raw) > MAX_BRIEF_BYTES:
        raise ValueError("E5_BRIEF_SIZE")
    try:
        obj = json.loads(raw.decode("utf-8", errors="strict"),
                         object_pairs_hook=_unique)
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise ValueError("E5_BRIEF_INVALID_JSON") from exc
    if not isinstance(obj, dict) or set(obj) != BRIEF_KEYS:
        raise ValueError("E5_BRIEF_SCHEMA")
    if type(obj["version"]) is not int or obj["version"] != 1:
        raise ValueError("E5_BRIEF_VERSION")
    brand = preview._safe_text(obj["brandLabel"], 3, 38)
    required = _terms(obj["requiredTerms"], allow_empty=False)
    forbidden = _terms(obj["forbiddenTerms"], allow_empty=True)
    if set(required) & set(forbidden):
        raise ValueError("E5_BRIEF_CONTRADICTS_ITSELF")
    return {"version": 1, "brandLabel": brand,
            "requiredTerms": required, "forbiddenTerms": forbidden}


def _contains_term(text: str, term: str) -> bool:
    # Case-insensitive whole-word checks on bounded ASCII term vocabulary,
    # not substring checks and NOT a model-based semantic compliance verdict.
    return re.search(r"(?<![a-z])" + re.escape(term) + r"(?![a-z])",
                     text.casefold()) is not None


def build(brief: dict, proposal: dict) -> tuple[bytes, bytes]:
    trusted_shape = read_brief(
        json.dumps(brief, ensure_ascii=False).encode("utf-8"))
    candidate = preview.read_proposal(
        json.dumps(proposal, ensure_ascii=False).encode("utf-8"))
    if candidate["brandLabel"] != trusted_shape["brandLabel"]:
        raise ValueError("E5_BRIEF_BRAND_MISMATCH")
    headline = " ".join(candidate["headlineLines"])
    if any(not _contains_term(headline, t) for t in trusted_shape["requiredTerms"]):
        raise ValueError("E5_BRIEF_REQUIRED_TERM_ABSENT")
    if any(_contains_term(headline, t) for t in trusted_shape["forbiddenTerms"]):
        raise ValueError("E5_BRIEF_FORBIDDEN_TERM_PRESENT")

    # The existing E5 candidate builder validates the original reference and
    # modifies ONLY the escaped brand + headline nodes. Active code and CSS
    # remain byte-identical to the reviewed, HUMAN-WRITTEN E3.9H reference.
    zip_bytes, manifest = preview.build(candidate)
    canonical_brief = (json.dumps(trusted_shape, sort_keys=True,
                        ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
    canonical_proposal = (json.dumps(candidate, sort_keys=True,
                           ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
    audit = {
        "formatVersion": 1,
        "decision": "LEXICAL_CONSTRAINT_MATCH_ONLY_NOT_APPROVAL",
        "artifactClass": manifest["artifactClass"],
        "sourceCodeProvenance": "E3.9H_HUMAN_CODED_BROWSER_REFERENCE",
        "briefAuthenticityVerified": False,
        "copyAuthenticityVerified": False,
        "modelAuthored": None,
        "ownerApproved": False,
        "deploymentAuthorized": False,
        "productionReady": False,
        "stage45Status": "NO_GO",
        "briefSha256": hashlib.sha256(canonical_brief).hexdigest(),
        "proposalSha256": hashlib.sha256(canonical_proposal).hexdigest(),
        "previewZipSha256": hashlib.sha256(zip_bytes).hexdigest(),
        "requiredTermCount": len(trusted_shape["requiredTerms"]),
        "forbiddenTermCount": len(trusted_shape["forbiddenTerms"]),
    }
    evidence = (json.dumps(audit, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")
    return zip_bytes, evidence


def _read_regular(path: Path, limit: int) -> bytes:
    # Reject symlinks even for the final component on POSIX runners.
    if path.is_symlink():
        raise ValueError("E5_BRIEF_SYMLINK_INPUT")
    flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
    try:
        fd = os.open(path, flags)
    except OSError as exc:
        raise ValueError("E5_BRIEF_INPUT_MISSING_OR_UNSAFE") from exc
    try:
        meta = os.fstat(fd)
        if not stat.S_ISREG(meta.st_mode) or meta.st_size > limit:
            raise ValueError("E5_BRIEF_INPUT_NOT_REGULAR")
        return os.read(fd, limit + 1)
    finally:
        os.close(fd)


def main() -> int:
    if len(sys.argv) != 1:
        print("E5_NO_ARBITRARY_INPUT_PATH_OR_DEPLOYMENT_ARGUMENTS", file=sys.stderr)
        return 2
    try:
        if FOLDER.is_symlink() or not FOLDER.is_dir():
            raise ValueError("E5_UNSAFE_REVIEW_DIRECTORY")
        raw_brief = _read_regular(BRIEF, MAX_BRIEF_BYTES)
        raw_proposal = _read_regular(PROPOSAL, preview.MAX_INPUT)
        brief = read_brief(raw_brief)
        proposal = preview.read_proposal(raw_proposal)
        zip_bytes, evidence = build(brief, proposal)
        for dest in (ZIP_OUT, AUDIT_OUT):
            if dest.is_symlink() or (dest.exists() and not dest.is_file()):
                raise ValueError("E5_UNSAFE_REVIEW_OUTPUT")
        ZIP_OUT.write_bytes(zip_bytes)
        AUDIT_OUT.write_bytes(evidence)
        print(json.dumps({
            "status": "LOCAL_BRIEF_CONSTRAINT_REVIEW_ONLY",
            "zip": str(ZIP_OUT.relative_to(ROOT)),
            "evidence": str(AUDIT_OUT.relative_to(ROOT)),
            "zipSha256": hashlib.sha256(zip_bytes).hexdigest(),
            "ownerApproved": False,
            "modelAuthored": None,
            "productionReady": False,
            "release": "NO_GO",
        }, sort_keys=True))
        return 0
    except (OSError, ValueError) as exc:
        # Diagnostics are fixed code phrases; never echo untrusted content.
        print("E5_BRIEF_REVIEW_REFUSED_" + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
