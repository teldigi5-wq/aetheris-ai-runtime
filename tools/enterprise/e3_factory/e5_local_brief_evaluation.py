#!/usr/bin/env python3
"""E4→E5 bounded, opt-in LOCAL evaluation across separately written research briefs.

Default mode does NO network or file access. The deliberate --run-local path
requires three operator opt-ins, a pre-existing local suite of 2–4 independently
authored briefs, and only uses the established local Ollama E4/E5 adapter.
Do not claim live-model performance from unit tests or CI mocks. A passing
lexical constraint does NOT prove aesthetic quality, truth or original code.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import re
import stat
import sys
import zipfile
from pathlib import Path
from typing import Sequence

import e36_free_model_router as models
import e4_local_proposal_session as local_session
import e5_creative_brief_gate as brief_gate
import e5_local_copy_trial as trial

FOLDER = brief_gate.FOLDER
SUITE = FOLDER / "local-evaluation-suite.json"
BUNDLE = FOLDER / "local-evaluation-evidence.zip"
OPT_EVALUATE = "AETHERIS_E5_MULTI_BRIEF_EVAL_OPT_IN"
MAX_SUITE_SIZE = 6200
MAX_ATTEMPTS = 4
CASE_ID = re.compile(r"[a-z][a-z0-9-]{3,18}\Z")


def _json_bytes(value: object) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True,
                       separators=(",", ":")) + "\n").encode("utf-8")


def read_suite(raw: bytes) -> dict:
    if not raw or len(raw) > MAX_SUITE_SIZE:
        raise ValueError("E5_EVAL_SUITE_SIZE")
    try:
        data = json.loads(raw.decode("utf-8", errors="strict"),
                          object_pairs_hook=brief_gate._unique)
    except (ValueError, UnicodeError) as exc:
        raise ValueError("E5_EVAL_SUITE_JSON") from exc
    if type(data) is not dict or set(data) != {"version", "cases"}:
        raise ValueError("E5_EVAL_SUITE_SCHEMA")
    if type(data["version"]) is not int or data["version"] != 1:
        raise ValueError("E5_EVAL_SUITE_VERSION")
    cases = data["cases"]
    if type(cases) is not list or not 2 <= len(cases) <= MAX_ATTEMPTS:
        raise ValueError("E5_EVAL_SUITE_CASE_COUNT")
    out, seen = [], set()
    for item in cases:
        if type(item) is not dict or set(item) != {"caseId", "brief"}:
            raise ValueError("E5_EVAL_CASE_SCHEMA")
        case_id = item["caseId"]
        if type(case_id) is not str or not CASE_ID.fullmatch(case_id):
            raise ValueError("E5_EVAL_CASE_ID")
        if case_id in seen:
            raise ValueError("E5_EVAL_DUPLICATE_CASE")
        seen.add(case_id)
        # Must validate all briefs before ANY local provider access.
        parsed = brief_gate.read_brief(_json_bytes(item["brief"]))
        out.append({"caseId": case_id, "brief": parsed})
    return {"version": 1, "cases": out}


def _add_zip(zf: zipfile.ZipFile, name: str, contents: bytes) -> None:
    meta = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
    meta.create_system = 3
    meta.external_attr = 0o600 << 16
    meta.compress_type = zipfile.ZIP_DEFLATED
    zf.writestr(meta, contents, compress_type=zipfile.ZIP_DEFLATED,
                compresslevel=9)


def evaluate(suite: dict, model_id: str) -> tuple[bytes, dict]:
    """Make at most four explicitly opted-in local trial calls, no retry."""
    checked = read_suite(_json_bytes(suite))
    models.checked_choice("ollama", model_id)
    if (os.environ.get(OPT_EVALUATE) != "yes"
            or os.environ.get(local_session.LOCAL_EXPERIMENT_OPT_IN) != "yes"
            or os.environ.get(models.OPT_LOCAL) != "yes"):
        raise ValueError("E5_EVAL_TRIPLE_OPT_IN_REQUIRED")
    if (model_id in models.HIGH_RESOURCE_LOCAL_CANDIDATES
            and os.environ.get(models.OPT_HIGH_RESOURCE) != "yes"):
        raise ValueError("E5_EVAL_HIGH_RESOURCE_SECOND_OPT_IN_REQUIRED")

    # This counts every proposed case, including failures, rather than
    # falsely reporting only accepted ZIPs as proof of model performance.
    rows = []
    extras: list[tuple[str, bytes]] = []
    for item in checked["cases"]:
        case_id = item["caseId"]
        receipt = None
        try:
            preview, evidence, receipt = trial.LocalCopyTrial().execute(
                model_id, item["brief"])
        except (ValueError, OSError, TimeoutError):
            rows.append({
                "caseId": case_id,
                "status": "REJECTED_OR_PROVIDER_UNAVAILABLE",
                "lexicalGateAccepted": False,
                "localInferenceVerified": False,
                "previewSha256": None,
                "receiptSha256": None,
            })
            continue
        # Success means a local transport response matched the strict E5
        # lexical constraints, NOT proof of authentic model identity.
        rows.append({
            "caseId": case_id,
            "status": "LEXICAL_GATE_ACCEPTED_NOT_QUALITY_CERTIFIED",
            "lexicalGateAccepted": True,
            "localInferenceVerified": False,
            "previewSha256": hashlib.sha256(preview).hexdigest(),
            "receiptSha256": hashlib.sha256(receipt).hexdigest(),
        })
        extras.extend([
            ("previews/" + case_id + ".zip", preview),
            ("receipts/" + case_id + ".json", receipt),
            ("evidence/" + case_id + ".json", evidence),
        ])

    accepted = sum(1 for row in rows if row["lexicalGateAccepted"])
    report = {
        "schemaVersion": 1,
        "evaluation": "BOUNDED_LOCAL_PROVIDER_LEXICAL_ACCEPTANCE_ONLY",
        "suiteSha256": hashlib.sha256(_json_bytes(checked)).hexdigest(),
        "requestedLocalModel": model_id,
        "providerIdentityCryptographicallyAttested": False,
        "realOwnerHardwareBenchmarkedByThisReport": False,
        "experimentalAdapterInvocations": len(rows),
        "maximumPermittedAdapterInvocations": MAX_ATTEMPTS,
        "acceptedLexically": accepted,
        "rejectedOrUnavailable": len(rows) - accepted,
        "acceptanceFraction": str(accepted) + "/" + str(len(rows)),
        "siteGeneratedByModel": False,
        "visualDesignQualityAssessed": False,
        "ownerApproved": False,
        "deploymentAuthorized": False,
        "stage45Go": False,
        "results": rows,
    }
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, mode="w") as zf:
        _add_zip(zf, "evaluation.json", _json_bytes(report))
        for name, value in extras:
            _add_zip(zf, name, value)
    return stream.getvalue(), report


def cli(args: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", required=True,
                        help="Exact locally allowlisted Ollama model ID")
    parser.add_argument("--run-local", action="store_true")
    parsed = parser.parse_args(args)
    try:
        models.checked_choice("ollama", parsed.model)
        if not parsed.run_local:
            print(json.dumps({
                "status": "PLAN_ONLY_NO_LOCAL_MODEL_CONTACT",
                "requestedModel": parsed.model,
                "maxAdapterInvocations": MAX_ATTEMPTS,
                "actualAttempts": 0,
                "suiteRead": False,
                "requires": "--run-local plus existing E4/E3.6 local opts and a separate E5 evaluation opt-in",
                "modelInstalledVerified": False,
                "ownerApproved": False,
                "stage45Go": False,
            }, sort_keys=True))
            return 0
        if (os.environ.get(OPT_EVALUATE) != "yes"
            or os.environ.get(local_session.LOCAL_EXPERIMENT_OPT_IN) != "yes"
            or os.environ.get(models.OPT_LOCAL) != "yes"):
            raise ValueError("E5_EVAL_TRIPLE_OPT_IN_REQUIRED")
        if FOLDER.is_symlink() or not FOLDER.is_dir():
            raise ValueError("E5_EVAL_UNSAFE_DIRECTORY")
        raw = brief_gate._read_regular(SUITE, MAX_SUITE_SIZE)
        validated = read_suite(raw)
        if BUNDLE.is_symlink() or BUNDLE.exists():
            raise ValueError("E5_EVAL_BUNDLE_ALREADY_EXISTS")
        archive, report = evaluate(validated, parsed.model)
        # Refuse pre-existing outputs, including symlinks, to avoid silently
        # replacing/overwriting any evidence from a prior owner-device run.
        flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0)
        fd = os.open(BUNDLE, flags, 0o600)
        try:
            with os.fdopen(fd, "wb") as output:
                output.write(archive)
        except BaseException:
            BUNDLE.unlink(missing_ok=True)
            raise
        print(json.dumps({
            "status": "LOCAL_EVALUATION_FINISHED_UNCERTIFIED",
            "attemptsSubmitted": report["experimentalAdapterInvocations"],
            "acceptedLexically": report["acceptedLexically"],
            "rejectedOrUnavailable": report["rejectedOrUnavailable"],
            "acceptanceFraction": report["acceptanceFraction"],
            "evidenceZipSha256": hashlib.sha256(archive).hexdigest(),
            "noRealModelRunInCI": True,
            "ownerApproved": False,
            "stage45Go": False,
        }, sort_keys=True))
        return 0
    except (ValueError, OSError, TimeoutError):
        # Never include raw prompts, model text or local path in diagnostics.
        print(json.dumps({
            "status": "LOCAL_EVALUATION_REFUSED",
            "remoteFallback": False,
            "ownerApproved": False,
            "stage45Go": False,
        }, sort_keys=True))
        return 2


if __name__ == "__main__":
    sys.exit(cli())
