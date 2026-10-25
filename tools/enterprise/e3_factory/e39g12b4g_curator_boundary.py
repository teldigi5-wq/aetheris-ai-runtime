#!/usr/bin/env python3
"""E3.9G-12B4G — local curator/model evidence separation; NO MODEL EXECUTION.

Curator keeps original broken+reference HTML outside repository/runner checkout.
Only broken read-only source and defect descriptions enter a single-member ZIP.
A private salt+reference commitment stays in the curator directory, never in
the public bundle. Do NOT call this externally/blindly verified from a flag.
"""
from __future__ import annotations

import hashlib
import io
import json
import os
from pathlib import Path
import re
import secrets
import sys
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2].resolve()
NAME = re.compile(r"^[a-z][a-z0-9-]{3,47}$")
FIELDS = {"schemaVersion", "independentCuratorDeclared", "cases"}
CASE_FIELDS = {"id", "site", "fault", "defectDescription", "sourceLicense"}
PRIVATE_NAME = ".b4g-private-evaluator-seal.json"
PUBLIC_NAME = "prompts.json"
MAX_HTML = 20_000
MAX_TOTAL = 180_000
DOMAIN = b"AETHERIS_B4G_CURATOR_REFERENCE_COMMITMENT_V1\x00"


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def unique(pairs):
    obj = {}
    for key, value in pairs:
        if key in obj:
            raise ValueError("B4G_DUPLICATE_JSON_KEY")
        obj[key] = value
    return obj


def parse(raw: bytes) -> dict:
    if len(raw) > MAX_TOTAL:
        raise ValueError("B4G_INPUT_OVER_BUDGET")
    obj = json.loads(raw.decode("utf-8"), object_pairs_hook=unique)
    if type(obj) is not dict:
        raise ValueError("B4G_NON_OBJECT_JSON")
    return obj


def checked(path: Path, limit: int) -> bytes:
    if path.is_symlink() or not path.is_file():
        raise ValueError("B4G_UNSAFE_OR_MISSING_FILE")
    size = path.stat().st_size
    if not 0 < size <= limit:
        raise ValueError("B4G_FILE_SIZE")
    return path.read_bytes()


def root_check(root: Path) -> Path:
    if root.is_symlink() or not root.is_dir():
        raise ValueError("B4G_UNSAFE_CURATOR_ROOT")
    resolved = root.resolve()
    if resolved == REPO or resolved.is_relative_to(REPO):
        raise ValueError("B4G_CURATOR_MUST_BE_OUTSIDE_REPOSITORY")
    case_dir = resolved / "cases"
    if case_dir.is_symlink() or not case_dir.is_dir():
        raise ValueError("B4G_UNSAFE_CASE_DIRECTORY")
    return resolved


def inspect(root: Path) -> tuple[list[dict], list[dict]]:
    root = root_check(root)
    manifest = parse(checked(root / "manifest.json", 16000))
    if set(manifest) != FIELDS or manifest.get("schemaVersion") != 1:
        raise ValueError("B4G_MANIFEST_SCHEMA")
    if manifest.get("independentCuratorDeclared") is not True:
        raise ValueError("B4G_REQUIRE_CURATOR_DECLARATION")
    entries = manifest["cases"]
    if type(entries) is not list or not 4 <= len(entries) <= 12:
        raise ValueError("B4G_CASE_COUNT")
    seen, sites, public, sealed = set(), set(), [], []
    for entry in entries:
        if type(entry) is not dict or set(entry) != CASE_FIELDS:
            raise ValueError("B4G_CASE_SCHEMA")
        cid, site, fault = (entry[k] for k in ("id", "site", "fault"))
        if any(type(v) is not str or not NAME.fullmatch(v) for v in (cid, site, fault)):
            raise ValueError("B4G_CASE_IDENTIFIER")
        if cid in seen:
            raise ValueError("B4G_DUPLICATE_CASE")
        seen.add(cid)
        sites.add(site)
        description = entry["defectDescription"]
        license_id = entry["sourceLicense"]
        if (type(description) is not str or not 12 <= len(description) <= 350
            or "\x00" in description or type(license_id) is not str
            or not 3 <= len(license_id) <= 100):
            raise ValueError("B4G_DESCRIPTION_OR_PROVENANCE")
        case_dir = root / "cases" / cid
        if case_dir.is_symlink() or not case_dir.is_dir():
            raise ValueError("B4G_CASE_DIRECTORY")
        broken = checked(case_dir / "broken.html", MAX_HTML)
        reference = checked(case_dir / "reference.html", MAX_HTML)
        for html in (broken, reference):
            text = html.decode("utf-8", "strict")
            if "<!doctype html>" not in text.lower() or "</html>" not in text.lower():
                raise ValueError("B4G_INVALID_HTML_FIXTURE")
        if broken == reference:
            raise ValueError("B4G_MISSING_REFERENCE_DIFFERENCE")
        public.append({
            "id": cid, "site": site, "fault": fault,
            "defectDescription": description, "sourceLicense": license_id,
            "brokenHtml": broken.decode("utf-8"),
            "brokenSha256": digest(broken),
        })
        sealed.append({
            "id": cid, "site": site, "fault": fault,
            "brokenSha256": digest(broken),
            "referenceSha256": digest(reference),
        })
    if len(sites) < 2:
        raise ValueError("B4G_NEED_DISTINCT_WEBSITES")
    public.sort(key=lambda row: row["id"])
    sealed.sort(key=lambda row: row["id"])
    return public, sealed


def canonical(obj) -> bytes:
    return (json.dumps(obj, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
            + "\n").encode("utf-8")


def public_zip(packet: dict) -> bytes:
    payload = canonical(packet)
    if len(payload) > MAX_TOTAL:
        raise ValueError("B4G_MODEL_BUNDLE_TOO_LARGE")
    out = io.BytesIO()
    with ZipFile(out, "w", ZIP_DEFLATED) as z:
        info = ZipInfo(PUBLIC_NAME, (2020, 1, 1, 0, 0, 0))
        info.compress_type = ZIP_DEFLATED
        z.writestr(info, payload)
    return out.getvalue()


def private_commitment(sealed: list[dict], salt: bytes) -> str:
    return digest(DOMAIN + salt + canonical(sealed))


def packet_of(public: list[dict], commitment: str) -> dict:
    return {
        "evidenceClass": "E39G12B4G_BROKEN_SOURCE_ONLY_MODEL_INPUT",
        "schemaVersion": 1,
        "cases": public,
        "sealedReferenceCommitment": commitment,
        "referenceSolutionsIncluded": False,
        "modelCallsPerformed": 0,
        "ownerApprovalGranted": False,
        "independentCuratorVerified": False,
        "genuinelyBlindOutcomeVerified": False,
        "productionAuthorized": False,
    }


def stage(root: Path, public_path: Path) -> None:
    root = root_check(root)
    if public_path.is_symlink() or public_path.exists():
        raise ValueError("B4G_PUBLIC_FILE_REPLAY")
    target = public_path.resolve()
    if target == root or target.is_relative_to(root):
        raise ValueError("B4G_NO_MODEL_ZIP_INSIDE_CURATOR")
    if not target.parent.is_dir() or target.parent.is_symlink():
        raise ValueError("B4G_PUBLIC_PARENT")
    seal_path = root / PRIVATE_NAME
    if seal_path.exists() or seal_path.is_symlink():
        raise ValueError("B4G_PRIVATE_SEAL_REPLAY")
    public, sealed = inspect(root)
    salt = secrets.token_bytes(32)
    commitment = private_commitment(sealed, salt)
    blob = public_zip(packet_of(public, commitment))
    private = {
        "evidenceClass": "E39G12B4G_EVALUATOR_ONLY_PRIVATE_SEAL",
        "saltHex": salt.hex(),
        "referenceRecords": sealed,
        "commitment": commitment,
        "publicBundleSha256": digest(blob),
        "independentCuratorVerified": False,
        "ownerApprovalGranted": False,
        "modelCallsPerformed": 0,
    }
    # Files are created exclusively, with no overwrite and restrictive mode.
    # Never upload the private seal or reference source in CI artifacts.
    fd = os.open(seal_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(canonical(private))
        fd = os.open(target, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
        with os.fdopen(fd, "wb") as f:
            f.write(blob)
    except BaseException:
        seal_path.unlink(missing_ok=True)
        raise
    print("B4G_PUBLIC_BROKEN_ONLY_SEAL_STAGED",
          json.dumps({"cases": len(public), "sourceSites": len(set(x["site"] for x in public)),
                      "bundleSha256": digest(blob), "sealedCommitment": commitment,
                      "independentlyVerified": False, "modelCalls": 0}))


def verify(root: Path, public_path: Path) -> None:
    root = root_check(root)
    public, sealed = inspect(root)
    secret = parse(checked(root / PRIVATE_NAME, 12000))
    if (set(secret) != {
        "evidenceClass", "saltHex", "referenceRecords", "commitment",
        "publicBundleSha256", "independentCuratorVerified",
        "ownerApprovalGranted", "modelCallsPerformed"
    } or secret["evidenceClass"] != "E39G12B4G_EVALUATOR_ONLY_PRIVATE_SEAL"
        or secret["referenceRecords"] != sealed
        or secret["independentCuratorVerified"] is not False
        or secret["ownerApprovalGranted"] is not False
        or secret["modelCallsPerformed"] != 0):
        raise ValueError("B4G_PRIVATE_EVIDENCE_DRIFT")
    try:
        salt = bytes.fromhex(secret["saltHex"])
    except (ValueError, TypeError):
        raise ValueError("B4G_INVALID_PRIVATE_SALT") from None
    if len(salt) != 32:
        raise ValueError("B4G_INVALID_PRIVATE_SALT")
    commitment = private_commitment(sealed, salt)
    raw = checked(public_path, MAX_TOTAL)
    if (secret["commitment"] != commitment
        or secret["publicBundleSha256"] != digest(raw)):
        raise ValueError("B4G_COMMITMENT_MISMATCH")
    with ZipFile(io.BytesIO(raw)) as z:
        if z.namelist() != [PUBLIC_NAME]:
            raise ValueError("B4G_MODEL_ZIP_SCOPE")
        member = z.infolist()[0]
        if member.file_size > MAX_TOTAL or member.compress_size > MAX_TOTAL:
            raise ValueError("B4G_MODEL_ZIP_DECOMPRESSION_LIMIT")
        payload = parse(z.read(PUBLIC_NAME))
    if payload != packet_of(public, commitment):
        raise ValueError("B4G_PUBLIC_PACKET_DRIFT")
    print("B4G_VERIFIED_LOCAL_CURATOR_SPLIT_NO_MODEL_RUN",
          json.dumps({"cases": len(public), "publicSha256": digest(raw),
                      "releaseCertified": False, "modelCalls": 0}))


def main() -> None:
    if len(sys.argv) != 4 or sys.argv[1] not in ("stage", "verify"):
        raise ValueError("B4G_FIXED_COMMAND_STAGE_OR_VERIFY")
    action, root, public_path = sys.argv[1], Path(sys.argv[2]), Path(sys.argv[3])
    if action == "stage":
        stage(root, public_path)
    else:
        verify(root, public_path)


if __name__ == "__main__":
    main()
