#!/usr/bin/env python3
"""E3.9F forensic audit of the *original*, digest-pinned E3.9C-2B2 CI ZIPs.

No model inference or website writes occur here. The script replays original
model replies through the unchanged independent repair validator. Read only
the exact expected ZIP members; never extract untrusted archives.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys
from zipfile import ZipFile

import e39d_heldout_focus_evaluator as evaluator
import e39e_model_policy as policy
import e39e_real_model_adviser as adviser

CASES = ("top-40", "top-120", "left-120", "left-240",
         "opacity-zero", "visibility-hidden", "transform-up", "transform-left")
BASELINE = {
    "hermes3:3b": {
        "artifactId": 12240847334,
        "zipSha256": "e6201903481c5e7dd46110aa7b7f6c9d6df8a08bbedad3d968aaa56c00cf9af8",
    },
    "granite4:3b": {
        "artifactId": 12240632502,
        "zipSha256": "4c1c6853b3f63a79835b7ae4a07d00fc1978bdda986e7c77d422accae86f8313",
    },
}
REPO_HEAD = "891e7059b47c46a9b46843efdade1d5d2a4dee3d"
RUN_ID = 39369258085


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def read_member(z: ZipFile, path: str, limit: int = 16_384) -> bytes:
    info = z.getinfo(path)
    if info.is_dir() or info.file_size > limit or info.file_size < 1:
        raise ValueError("E39F_UNSAFE_OR_EMPTY_ARTIFACT_MEMBER")
    raw = z.read(info)
    if len(raw) != info.file_size:
        raise ValueError("E39F_ARTIFACT_TRUNCATED")
    return raw


def parse_member(z: ZipFile, path: str) -> dict:
    def unique(pairs):
        out = {}
        for key, value in pairs:
            if key in out:
                raise ValueError("E39F_DUPLICATE_EVIDENCE_FIELD")
            out[key] = value
        return out
    value = json.loads(read_member(z, path), object_pairs_hook=unique)
    if type(value) is not dict:
        raise ValueError("E39F_NONOBJECT_EVIDENCE")
    return value


def diagnose(raw: str, case: str) -> tuple[str, str]:
    """Exact baseline E39E validator, not a replacement or relaxed gate."""
    try:
        parsed = adviser.strict_json(raw)
    except ValueError:
        return "INVALID", "E39F_RESPONSE_INVALID"
    if parsed == {"operation": "abstain"}:
        return "ABSTAIN", "E39F_MODEL_ABSTAINED"
    try:
        candidate = policy.parse_response(raw)
        policy.validate_candidate(candidate, evaluator.defective_source(case),
                                  evaluator.clean_source())
    except ValueError as error:
        code = str(error)
        if not code.startswith("E39E_") or not code.isupper():
            raise ValueError("E39F_UNRECOGNIZED_POLICY_ERROR") from error
        return "POLICY_REJECTED", code
    return "POLICY_ACCEPTED", "E39F_POLICY_ACCEPTED_BROWSER_REQUIRED"


def audit_archive(path: Path, model: str) -> dict:
    expected = BASELINE[model]
    if not path.is_file() or path.is_symlink() or path.stat().st_size > 20_000_000:
        raise ValueError("E39F_ARCHIVE_MISSING_OR_TOO_LARGE")
    archive_hash = digest(path.read_bytes())
    if archive_hash != expected["zipSha256"]:
        raise ValueError("E39F_BASELINE_ARCHIVE_DIGEST_MISMATCH")
    records = []
    with ZipFile(path) as z:
        for case in CASES:
            prefix = "e39d/" + case + "/"
            response = parse_member(z, prefix + "model-response.json")
            score = parse_member(z, prefix + "score.json")
            seed = parse_member(z, prefix + "seed.json")
            raw = response.get("rawResponse")
            if type(raw) is not str:
                raise ValueError("E39F_MISSING_RAW_TRANSCRIPT")
            outcome, reason = diagnose(raw, case)
            before_sha = digest(evaluator.defective_source(case))
            if (response.get("evidenceClass") != "E39E_GENUINE_EIGHT_CASE_MODEL_REPLY"
                or response.get("model", {}).get("tag") != model
                or response.get("caseId") != case
                or response.get("realModelCalls") != 1
                or response.get("promptVersion")
                   != "E39E_EIGHT_CASES_SELECTOR_SCALAR_NONORACLE_V1"
                or response.get("rawResponseSha256") != digest(raw.encode())
                or response.get("sourceSha256") != before_sha
                or seed.get("beforeSourceSha256") != before_sha
                or score.get("evidenceClass") != "E39E_ACTUAL_LOCAL_MODEL_BROWSER_SCORED"
                or score.get("caseId") != case or score.get("modelTag") != model
                or score.get("policyOutcome") != outcome
                or score.get("realModelCalls") != 1
                or score.get("responseClass") != response.get("responseClass")
                or score.get("ownerApproved") is not False
                or score.get("productionPublished") is not False
                or response.get("ownerApproved") is not False
                or response.get("oracleExpectedPatchLoaded") is not False):
                raise ValueError("E39F_BASELINE_PROVENANCE_MISMATCH_" + case)
            for viewport in ("mobile", "desktop"):
                before_bytes = read_member(z, prefix + viewport + "-before.json")
                before = json.loads(before_bytes)
                if (response.get("browserBeforeSha256", {}).get(viewport)
                        != digest(before_bytes)
                        or before.get("passed") is not False
                        or before.get("viewport") != viewport
                        or before.get("caseId") != case):
                    raise ValueError("E39F_BAD_PREPATCH_BROWSER_RECEIPT")
            verified = bool(score.get("chromiumVerifiedModelRepair"))
            if outcome != "POLICY_ACCEPTED" and verified:
                raise ValueError("E39F_UNACCEPTED_PATCH_CREDITED")
            if outcome == "POLICY_ACCEPTED":
                for viewport in ("mobile", "desktop"):
                    after = parse_member(z, prefix + viewport + "-after.json")
                    if (after.get("passed") is not True
                            or after.get("caseId") != case
                            or after.get("viewport") != viewport):
                        if verified:
                            raise ValueError("E39F_FALSE_CHROMIUM_CREDIT")
                if verified:
                    reason = "E39F_REAL_CHROMIUM_VERIFIED"
            if score.get("policyAccepted") is not (outcome == "POLICY_ACCEPTED"):
                raise ValueError("E39F_POLICY_SCORE_INCONSISTENT")
            records.append({
                "case": case, "outcome": outcome, "reasonCode": reason,
                "browserVerified": verified,
                "rawResponseSha256": response["rawResponseSha256"],
                "outputTokens": score.get("selfReportedGeneratedTokens"),
                "callWallMs": score.get("wallMs"),
            })
    return {
        "modelTag": model, "artifactId": expected["artifactId"],
        "artifactSha256": archive_hash, "attempted": len(records),
        "browserVerified": sum(x["browserVerified"] for x in records),
        "abstained": sum(x["outcome"] == "ABSTAIN" for x in records),
        "rejected": sum(x["outcome"] == "POLICY_REJECTED" for x in records),
        "invalid": sum(x["outcome"] == "INVALID" for x in records),
        "records": records,
    }


def audit(hermes_zip: Path, granite_zip: Path) -> dict:
    reports = [audit_archive(hermes_zip, "hermes3:3b"),
               audit_archive(granite_zip, "granite4:3b")]
    return {
        "evidenceClass": "E39F_DIGEST_BOUND_ORIGINAL_TRANSCRIPT_TAXONOMY",
        "baselineCommit": REPO_HEAD,
        "baselineActionsRunId": RUN_ID,
        "cases": sum(r["attempted"] for r in reports),
        "genuineCallEvidenceVerified": True,
        "newModelCallsPerformed": 0,
        "generalizationCertified": False,
        "sealedEvaluationPerformed": False,
        "modelReports": reports,
    }


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("Usage: e39f_failure_taxonomy.py HERMES_BASELINE_ZIP GRANITE_BASELINE_ZIP")
    print(json.dumps(audit(Path(sys.argv[1]), Path(sys.argv[2])),
                     indent=2, sort_keys=True))
