#!/usr/bin/env python3
"""Enterprise E4→E5: explicit ONE-shot local Ollama copy trial, review-only.

Default CLI is PLAN ONLY. The opt-in path uses the existing, host-locked E3.6
Ollama transport, an independent E4 route decision, and E5's strict creative
brief/candidate admission. This does NOT create an original website, provide
human approval, execute model-authored code or deploy anything.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import threading
import time
from pathlib import Path
from typing import Any, Sequence

import e36_free_model_router as models
import e4_free_route_admission as admission
import e4_local_proposal_session as local_session
import e5_creative_brief_gate as creative
import e5_untrusted_copy_preview as copy_schema

ROOT = creative.ROOT
FOLDER = creative.FOLDER
ZIP = FOLDER / "local-model-copy-trial-preview.zip"
EVIDENCE = FOLDER / "local-model-copy-trial-evidence.json"
RECEIPT = FOLDER / "local-model-copy-trial-receipt.json"
MAX_PROVIDER_TEXT_BYTES = copy_schema.MAX_INPUT
TASK = "scroll"  # Existing E4 admission category; not website generator authorization.
SYSTEM = (
    "You are an UNTRUSTED text-only two-line research-copy proposer. "
    "Do not produce code, instructions, URLs, HTML, secrets, owner claims or "
    "deployment instructions. Output exactly one strict JSON object with only "
    'version=1, brandLabel, headlineLines (two short strings). '
    "This is an offline, unapproved prototype. The independent validator may "
    "reject the entire response; never claim publication or factual accuracy."
)


class LocalCopyTrial:
    """One live attempt per instance, reserved before even probing installed models."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._attempted = False

    def execute(self, model_id: str, creative_brief: dict) -> tuple[bytes, bytes, bytes]:
        if type(model_id) is not str:
            raise ValueError("E5_LOCAL_MODEL_ID_INVALID")
        models.checked_choice("ollama", model_id)
        # Verify the brief before any model/network action. It is still not
        # authenticated human consent; it is just an untrusted local file.
        brief = creative.read_brief(json.dumps(creative_brief,
                     ensure_ascii=False).encode("utf-8"))
        if (os.environ.get(local_session.LOCAL_EXPERIMENT_OPT_IN) != "yes"
                or os.environ.get(models.OPT_LOCAL) != "yes"):
            raise ValueError("E5_LOCAL_DOUBLE_OPT_IN_REQUIRED")
        high = model_id in models.HIGH_RESOURCE_LOCAL_CANDIDATES
        if high and os.environ.get(models.OPT_HIGH_RESOURCE) != "yes":
            raise ValueError("E5_LOCAL_HIGH_RESOURCE_CONSENT_REQUIRED")
        with self._lock:
            if self._attempted:
                raise ValueError("E5_LOCAL_TRIAL_ONE_ATTEMPT_EXHAUSTED")
            self._attempted = True

        # No cloud fallback, no shell tools, no automatic model installation.
        tags, _ = models.send("ollama", "GET", "/api/tags")
        installed = tags.get("models")
        if (type(installed) is not list or len(installed) > 64
            or any(type(m) is not dict or type(m.get("name")) is not str
                   or len(m["name"]) > 120 for m in installed)):
            raise ValueError("E5_LOCAL_MODEL_INVENTORY_INVALID")
        ids = [m["name"] for m in installed]
        verdict = admission.advisory({
            "task": TASK, "provider": "ollama", "modelId": model_id,
            "externalInferenceConsent": False,
            "sourceDisclosureConsent": False,
            "highResourceConsent": high,
            "maxCostUsd": "0"
        }, {
            "installedLocalModelIds": ids,
            "cloudModelDetail": None,
            "catalogFetchedUnixSeconds": None
        }, int(time.time()))
        if verdict["decision"] != "CANDIDATE_FOR_SEPARATE_RUNTIME_VERIFICATION":
            raise ValueError("E5_LOCAL_ROUTE_REFUSED_" + verdict["reason"])

        # Only bounded research terms and synthetic/locally supplied brand are
        # included; NO application source code, credentials, browser history,
        # private files, additional user data or customer deployment details.
        user_prompt = json.dumps({
            "brandLabel": brief["brandLabel"],
            "requiredHeadlineTerms": brief["requiredTerms"],
            "forbiddenHeadlineTerms": brief["forbiddenTerms"],
            "outputSchema": {"version": 1, "brandLabel": brief["brandLabel"],
                             "headlineLines": ["string up to 26 chars",
                                               "string up to 26 chars"]}
        }, ensure_ascii=False, separators=(",", ":"))
        if len(user_prompt.encode("utf-8")) > 1500:
            raise ValueError("E5_LOCAL_PROMPT_TOO_LARGE")
        result, _ = models.send("ollama", "POST", "/api/generate", payload={
            "model": model_id, "system": SYSTEM, "prompt": user_prompt,
            "format": "json", "stream": False,
            "options": {"temperature": 0, "num_predict": 180}
        })
        if (type(result) is not dict
            or result.get("model") != model_id
            or result.get("done") is not True
            or type(result.get("response")) is not str):
            raise ValueError("E5_LOCAL_RESPONSE_ENVELOPE_INVALID")
        raw = result["response"].encode("utf-8")
        if not raw or len(raw) > MAX_PROVIDER_TEXT_BYTES:
            raise ValueError("E5_LOCAL_RESPONSE_SIZE_INVALID")
        proposal = copy_schema.read_proposal(raw)
        zip_bytes, evidence = creative.build(brief, proposal)
        metadata = {
            "formatVersion": 1,
            "status": "UNATTESTED_LOCAL_OLLAMA_COPY_TRIAL_REVIEW_ONLY",
            "provider": "ollama",
            "requestedModel": model_id,
            "providerReportedModel": result["model"],
            "providerIdentityCryptographicallyAttested": False,
            "remoteFallbackPermitted": False,
            "modelInstallOrPullPerformed": False,
            "localProviderAttempted": True,
            "providerResponseSha256": hashlib.sha256(raw).hexdigest(),
            "reviewZipSha256": hashlib.sha256(zip_bytes).hexdigest(),
            "reviewEvidenceSha256": hashlib.sha256(evidence).hexdigest(),
            "siteCodeProvenance": "E3.9H_HUMAN_CODED_BROWSER_REFERENCE",
            "siteGeneratedByModel": False,
            "copyAuthorshipIndependentlyVerified": False,
            "ownerApproved": False,
            "deploymentAuthorized": False,
            "stage45Go": False
        }
        receipt = (json.dumps(metadata, sort_keys=True,
                   separators=(",", ":")) + "\n").encode("utf-8")
        return zip_bytes, evidence, receipt


def cli(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", required=True)
    parser.add_argument("--run-local", action="store_true")
    args = parser.parse_args(argv)
    try:
        models.checked_choice("ollama", args.model)
        if not args.run_local:
            print(json.dumps({
                "status": "PLAN_ONLY_NO_INFERENCE",
                "provider": "ollama",
                "modelId": args.model,
                "modelInstalledVerified": False,
                "requires": "--run-local and two independent environment opt-ins",
                "modelDownloadsPermitted": False,
                "customerWebsiteGenerated": False,
                "deploymentAuthorized": False,
                "stage45Go": False
            }, sort_keys=True))
            return 0
        if FOLDER.is_symlink() or not FOLDER.is_dir():
            raise ValueError("E5_LOCAL_REVIEW_DIRECTORY_UNSAFE")
        raw = creative._read_regular(creative.BRIEF, creative.MAX_BRIEF_BYTES)
        brief = creative.read_brief(raw)
        zip_bytes, audit, receipt = LocalCopyTrial().execute(args.model, brief)
        for path in (ZIP, EVIDENCE, RECEIPT):
            if path.is_symlink() or (path.exists() and not path.is_file()):
                raise ValueError("E5_LOCAL_OUTPUT_NOT_REGULAR")
        ZIP.write_bytes(zip_bytes)
        EVIDENCE.write_bytes(audit)
        RECEIPT.write_bytes(receipt)
        print(json.dumps({
            "status": "LOCAL_MODEL_COPY_UNTRUSTED_OFFLINE_REVIEW",
            "zipSha256": hashlib.sha256(zip_bytes).hexdigest(),
            "evidenceSha256": hashlib.sha256(audit).hexdigest(),
            "providerIdentityAttested": False,
            "customerWebsiteGenerated": False,
            "ownerApproved": False,
            "deploymentAuthorized": False,
            "stage45Go": False
        }, sort_keys=True))
        return 0
    except (ValueError, OSError, TimeoutError):
        # Never echo model responses, source, secrets, local paths or exceptions.
        print(json.dumps({
            "status": "LOCAL_MODEL_COPY_TRIAL_DENIED_OR_UNAVAILABLE",
            "remoteFallbackPermitted": False,
            "reviewArtifactWritten": False,
            "ownerApproved": False,
            "stage45Go": False
        }, sort_keys=True))
        return 2


if __name__ == "__main__":
    raise SystemExit(cli())
