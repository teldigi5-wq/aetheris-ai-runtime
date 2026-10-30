#!/usr/bin/env python3
"""Enterprise E4/E5: six-case local intent-alignment benchmark, NOT quality certification.

Plan-only by default. The explicit local-only path makes at most SIX one-shot
layout proposals, using only checked-in synthetic brief/copy and enumerated
design intent. Every refusal counts as zero; a public deterministic static
rubric is the baseline (and reaches the ceiling by construction). Neither
a matching layout nor a localhost response establishes model creativity,
provider attestation, genuine on-device performance, or production readiness.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import sys
import zipfile
from pathlib import Path
from typing import Sequence

import e36_free_model_router as models
import e4_local_proposal_session as sessions
import e5_creative_brief_gate as brief_gate
import e5_layout_design_intent as preferences
import e5_local_layout_trial as trial
import e5_structured_layout_preview as layouts

FOLDER = layouts.FOLDER
OUTPUT = FOLDER / "six-case-intent-benchmark.zip"
OPT_BENCHMARK = "AETHERIS_E5_SIX_CASE_INTENT_BENCHMARK_OPT_IN"
MAX_CASES = 6
MAX_EVIDENCE_BYTES = 2_000_000
SYNTHETIC_BRIEF = {
    "version": 1, "brandLabel": "Prototype Atelier",
    "requiredTerms": ["quiet", "motion"], "forbiddenTerms": ["certified"]
}
SYNTHETIC_COPY = {
    "version": 1, "brandLabel": "Prototype Atelier",
    "headlineLines": ["Quiet materials.", "Purposeful motion."]
}
# Version-locked, public, non-sensitive, diverse combinations. NOT held-out.
CASE_VALUES = (
    ("case-01", "immersive", "light", "editorial"),
    ("case-02", "informational", "dark", "contemporary"),
    ("case-03", "balanced", "dark", "editorial"),
    ("case-04", "immersive", "dark", "contemporary"),
    ("case-05", "informational", "light", "contemporary"),
    ("case-06", "balanced", "light", "editorial"),
)


def _canonical(value: object) -> bytes:
    return (json.dumps(value, sort_keys=True, ensure_ascii=False,
                       separators=(",", ":")) + "\n").encode("utf-8")


def cases() -> tuple[tuple[str, dict], ...]:
    candidates: list[tuple[str, dict]] = []
    for identifier, goal, contrast, typeface in CASE_VALUES:
        approved = preferences.read_intent(_canonical({
            "version": 1, "layoutGoal": goal,
            "contrastPreference": contrast,
            "typographicVoice": typeface,
        }))
        candidates.append((identifier, approved))
    assert len(candidates) == MAX_CASES
    assert len({name for name, _ in candidates}) == MAX_CASES
    assert len({preferences.sha256(value) for _, value in candidates}) == MAX_CASES
    return tuple(candidates)


def _put(archive: zipfile.ZipFile, name: str, data: bytes) -> None:
    info = zipfile.ZipInfo(name, layouts.reference.PINNED_TIME)
    info.external_attr = 0o100600 << 16
    info.compress_type = zipfile.ZIP_DEFLATED
    archive.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)


def check_local_permission(model_id: str) -> None:
    models.checked_choice("ollama", model_id)
    required = (models.OPT_LOCAL, sessions.LOCAL_EXPERIMENT_OPT_IN,
                trial.OPT_LAYOUT, trial.OPT_INTENT, OPT_BENCHMARK)
    if any(os.environ.get(key) != "yes" for key in required):
        raise ValueError("E5_BENCHMARK_FIVE_EXPLICIT_OPT_INS_REQUIRED")
    if (model_id in models.HIGH_RESOURCE_LOCAL_CANDIDATES
            and os.environ.get(models.OPT_HIGH_RESOURCE) != "yes"):
        raise ValueError("E5_BENCHMARK_HEAVY_MODEL_ADDITIONAL_OPT_IN_REQUIRED")


def evaluate(model_id: str) -> tuple[bytes, dict]:
    """At most six independent local one-shot trials, each with max two calls."""
    check_local_permission(model_id)
    validated_cases = cases()
    # Ensure source/template integrity and synthetic copy shape before ANY
    # local network attempt. No customer/user file enters the model prompt.
    brief_gate.build(SYNTHETIC_BRIEF, SYNTHETIC_COPY)

    accepted = 0
    matched = 0
    result_rows: list[dict] = []
    contents: list[tuple[str, bytes]] = []
    for case_id, design_intent in validated_cases:
        outcome = {
            "caseId": case_id,
            "intentSha256": preferences.sha256(design_intent),
            "status": "REJECTED_OR_PROVIDER_UNAVAILABLE",
            "validLayoutReturned": False,
            "alignmentPoints": 0,
            "possiblePoints": 3,
            "previewSha256": None,
            "receiptSha256": None,
        }
        try:
            # One fresh attempt object per pre-registered case. No retries.
            bundle, receipt = trial.LocalLayoutTrial().execute(
                model_id, SYNTHETIC_BRIEF, SYNTHETIC_COPY, design_intent)
            # Treat adapter output as untrusted despite coming from local code.
            if (receipt.get("task") != "website-layout"
                    or receipt.get("abstractDesignIntentSha256") != outcome["intentSha256"]
                    or receipt.get("stage45Go") is not False
                    or receipt.get("ownerApproved") is not False):
                raise ValueError("E5_BENCHMARK_RECEIPT_INVALID")
            with zipfile.ZipFile(io.BytesIO(bundle)) as package:
                if set(package.namelist()) != {"receipt.json", "preview.zip"}:
                    raise ValueError("E5_BENCHMARK_PACKAGE_SCHEMA")
                if package.testzip() is not None:
                    raise ValueError("E5_BENCHMARK_PACKAGE_CORRUPT")
                embedded = json.loads(package.read("receipt.json"))
                if embedded != receipt:
                    raise ValueError("E5_BENCHMARK_RECEIPT_MISMATCH")
                site = package.read("preview.zip")
            if hashlib.sha256(site).hexdigest() != receipt.get("previewSha256"):
                raise ValueError("E5_BENCHMARK_PREVIEW_HASH_MISMATCH")
            assessment = preferences.score(design_intent, receipt["layoutDecision"])
            points = assessment["matched"]
            if (receipt.get("staticPreferenceAlignment") != assessment
                    or type(points) is not int or not 0 <= points <= 3):
                raise ValueError("E5_BENCHMARK_ALIGNMENT_MISMATCH")
        except (OSError, ValueError, TimeoutError, KeyError, TypeError, zipfile.BadZipFile):
            result_rows.append(outcome)
            continue
        outcome.update({
            "status": "VALID_ENUM_TOKENS_ONLY",
            "validLayoutReturned": True,
            "alignmentPoints": points,
            "previewSha256": hashlib.sha256(site).hexdigest(),
            "receiptSha256": hashlib.sha256(_canonical(receipt)).hexdigest(),
        })
        accepted += 1
        matched += points
        result_rows.append(outcome)
        contents.extend([
            ("previews/" + case_id + ".zip", site),
            ("receipts/" + case_id + ".json", _canonical(receipt)),
        ])

    baseline = sum(3 for _ in validated_cases)
    report = {
        "schemaVersion": 1,
        "status": "LOCAL_PROVIDER_CONTRACT_BENCHMARK_NOT_CERTIFICATION",
        "requestedProvider": "ollama",
        "claimedModel": model_id,
        "publicSuiteVersion": "E5_INTENT_ALIGNMENT_SIX_CASES_V1",
        "publicSuiteSha256": hashlib.sha256(_canonical(CASE_VALUES)).hexdigest(),
        "submitted": len(validated_cases),
        "maximumLocalAdapterInvocations": MAX_CASES,
        "validResponses": accepted,
        "invalidOrUnavailable": MAX_CASES - accepted,
        "alignmentPointsIncludingFailuresAsZero": matched,
        "totalPossibleAlignmentPoints": baseline,
        "alignmentFraction": f"{matched}/{baseline}",
        "humanAuthoredStaticBaselinePoints": baseline,
        "staticBaselineUsesPublicRule": True,
        "benchmarkCasesHeldOut": False,
        "creativeQualityMeasured": False,
        "modelAuthorshipCryptographicallyAttested": False,
        "ownerHardwarePerformanceCertified": False,
        "siteCodeModelAuthored": False,
        "ownerApproved": False,
        "deploymentAuthorized": False,
        "stage45Go": False,
        "results": result_rows,
    }
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as package:
        _put(package, "evaluation.json", _canonical(report))
        for path, data in contents:
            _put(package, path, data)
    result = output.getvalue()
    if len(result) > MAX_EVIDENCE_BYTES:
        raise ValueError("E5_BENCHMARK_EVIDENCE_SIZE")
    return result, report


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", required=True)
    parser.add_argument("--run-local", action="store_true")
    args = parser.parse_args(argv)
    try:
        models.checked_choice("ollama", args.model)
        if not args.run_local:
            print(json.dumps({
                "status": "PLAN_ONLY_ZERO_INFERENCE",
                "publicSyntheticCases": MAX_CASES,
                "actualAttempts": 0,
                "modelInstalledVerified": False,
                "benchmarkAuthorized": False,
                "stage45Go": False,
            }, sort_keys=True))
            return 0
        check_local_permission(args.model)
        if not FOLDER.is_dir() or FOLDER.is_symlink():
            raise ValueError("E5_BENCHMARK_DIRECTORY_UNSAFE")
        if OUTPUT.is_symlink() or OUTPUT.exists():
            raise ValueError("E5_BENCHMARK_EXISTING_EVIDENCE")
        bundle, report = evaluate(args.model)
        flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0)
        fd = os.open(OUTPUT, flags, 0o600)
        try:
            with os.fdopen(fd, "wb") as stored:
                stored.write(bundle)
        except BaseException:
            OUTPUT.unlink(missing_ok=True)
            raise
        print(json.dumps({
            "status": "PUBLIC_SUITE_LOCAL_PROVIDER_MEASURED_UNCERTIFIED",
            "submitted": report["submitted"],
            "validResponses": report["validResponses"],
            "invalidOrUnavailable": report["invalidOrUnavailable"],
            "alignmentFraction": report["alignmentFraction"],
            "sha256": hashlib.sha256(bundle).hexdigest(),
            "creativeQualityMeasured": False,
            "stage45Go": False,
        }, sort_keys=True))
        return 0
    except (OSError, ValueError, TimeoutError):
        # Do not print raw model output, prompts, local filesystem paths, or env.
        print(json.dumps({
            "status": "LOCAL_BENCHMARK_REFUSED_OR_UNAVAILABLE",
            "modelRequestsNotGuaranteed": True,
            "remoteFallbackPermitted": False,
            "stage45Go": False,
        }, sort_keys=True))
        return 2


if __name__ == "__main__":
    sys.exit(main())
