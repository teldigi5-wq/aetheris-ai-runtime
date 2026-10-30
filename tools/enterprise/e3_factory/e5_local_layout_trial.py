#!/usr/bin/env python3
"""Enterprise E4/E5 local-only, one-shot layout TOKEN proposal; no AI code execution.

PLAN ONLY by default. On explicit operator opt-in, an installed localhost
Ollama model may select among 18 pre-reviewed visual variants. The underlying
responsive CSS and WebGL remain HUMAN-WRITTEN. No remote provider or deployment.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import sys
import threading
import time
import zipfile
from typing import Sequence

import e36_free_model_router as models
import e4_free_route_admission as routes
import e4_local_proposal_session as sessions
import e5_creative_brief_gate as briefs
import e5_layout_design_intent as intent_spec
import e5_structured_layout_preview as layouts
import e5_untrusted_copy_preview as copies

TASK = "website-layout"
OPT_LAYOUT = "AETHERIS_E5_LOCAL_LAYOUT_TRIAL_OPT_IN"
OPT_INTENT = "AETHERIS_E5_ABSTRACT_DESIGN_INTENT_OPT_IN"
FOLDER = layouts.FOLDER
INTENT = FOLDER / "layout-intent.json"
BRIEF = layouts.BRIEF
COPY = layouts.COPY
OUTPUT = FOLDER / "local-model-layout-review.zip"
MAX_RAW_RESPONSE_BYTES = layouts.MAX_LAYOUT_BYTES
SYSTEM = (
    "You are an untrusted local experimental composition selector. "
    "Choose ONE set of strictly enumerated design tokens; never write CSS, "
    "HTML, JS, URLs, explanations, markdown, shell commands or custom values. "
    "Respond with one JSON object: version=1, composition, palette, typography. "
    "This output can only be used as an unapproved offline visual research preview."
)


def _canonical(data: object) -> bytes:
    return (json.dumps(data, sort_keys=True, ensure_ascii=False,
                       separators=(",", ":")) + "\n").encode("utf-8")


class LocalLayoutTrial:
    """One attempt per instance; consumed before the model inventory request."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._attempted = False

    def execute(self, model_id: str, creative_brief: dict,
                copy_proposal: dict, design_intent: dict | None = None) -> tuple[bytes, dict]:
        if type(model_id) is not str:
            raise ValueError("E5_LAYOUT_INVALID_MODEL")
        models.checked_choice("ollama", model_id)
        # Both independently constrained inputs must pass before inference.
        # Only validation occurs; parent ZIP is not published or executed.
        briefs.build(creative_brief, copy_proposal)
        # Independent categorical design-intent may be supplied by the operator,
        # never inferred from private brand/copy. Check before any model call.
        constrained_intent = (None if design_intent is None else
            intent_spec.read_intent(intent_spec.canonical(design_intent)))
        if constrained_intent is not None and os.environ.get(OPT_INTENT) != "yes":
            raise ValueError("E5_INTENT_EXPLICIT_FOURTH_OPT_IN_REQUIRED")
        if (os.environ.get(models.OPT_LOCAL) != "yes"
            or os.environ.get(sessions.LOCAL_EXPERIMENT_OPT_IN) != "yes"
            or os.environ.get(OPT_LAYOUT) != "yes"):
            raise ValueError("E5_LAYOUT_THREE_OPT_INS_REQUIRED")
        high = model_id in models.HIGH_RESOURCE_LOCAL_CANDIDATES
        if high and os.environ.get(models.OPT_HIGH_RESOURCE) != "yes":
            raise ValueError("E5_LAYOUT_HIGH_RESOURCE_OPT_IN_REQUIRED")
        with self._lock:
            if self._attempted:
                raise ValueError("E5_LAYOUT_ONE_ATTEMPT_EXHAUSTED")
            self._attempted = True

        observed, _ = models.send("ollama", "GET", "/api/tags")
        if type(observed) is not dict:
            raise ValueError("E5_LAYOUT_MODEL_INVENTORY_INVALID")
        entries = observed.get("models")
        if (type(entries) is not list or len(entries) > 64
            or any(type(v) is not dict or type(v.get("name")) is not str
                   or len(v["name"]) > 120 for v in entries)):
            raise ValueError("E5_LAYOUT_MODEL_INVENTORY_INVALID")
        ids = [item["name"] for item in entries]
        route = routes.advisory({
            "task": TASK, "provider": "ollama", "modelId": model_id,
            "externalInferenceConsent": False,
            "sourceDisclosureConsent": False,
            "highResourceConsent": high,
            "maxCostUsd": "0",
        }, {
            "installedLocalModelIds": ids,
            "cloudModelDetail": None,
            "catalogFetchedUnixSeconds": None,
        }, int(time.time()))
        if route["decision"] != "CANDIDATE_FOR_SEPARATE_RUNTIME_VERIFICATION":
            raise ValueError("E5_LAYOUT_LOCAL_ROUTE_DENIED")

        # No source, user/customer data, secret, brand, browser history or
        # creative brief is sent to the model. Its task is solely choosing
        # a finite set of compiler tokens from static literal enumerations.
        prompt_data = {
            "schemaVersion": 1,
            "composition": sorted(layouts.OPTIONS["composition"]),
            "palette": sorted(layouts.OPTIONS["palette"]),
            "typography": sorted(layouts.OPTIONS["typography"]),
            "selectionPolicy": "Select exactly one allowed value per field; "
                               "return JSON with version 1.",
        }
        if constrained_intent is not None:
            # The model sees ONLY the three abstract enums, not the brief,
            # brand, candidate copy or user's untrusted written instructions.
            prompt_data["abstractDesignIntent"] = constrained_intent
        prompt = _canonical(prompt_data).decode("utf-8")
        if len(prompt.encode("utf-8")) > 1000:
            raise ValueError("E5_LAYOUT_PROMPT_TOO_LARGE")

        answer, _ = models.send("ollama", "POST", "/api/generate", payload={
            "model": model_id,
            "system": SYSTEM,
            "prompt": prompt,
            "format": "json",
            "stream": False,
            "options": {"temperature": 0, "num_predict": 100},
        })
        if (type(answer) is not dict or answer.get("model") != model_id
            or answer.get("done") is not True
            or type(answer.get("response")) is not str):
            raise ValueError("E5_LAYOUT_PROVIDER_ENVELOPE_INVALID")
        raw = answer["response"].encode("utf-8")
        decision = layouts.read_layout(raw)
        preview, manifest = layouts.build(creative_brief, copy_proposal, decision)
        alignment = (None if constrained_intent is None else
                     intent_spec.score(constrained_intent, decision))
        receipt = {
            "formatVersion": 1,
            "result": "UNVERIFIED_LOCAL_MODEL_LAYOUT_TOKENS_REVIEW_ONLY",
            "task": TASK,
            "provider": "ollama",
            "claimedModel": model_id,
            "modelIdentityCryptographicallyAttested": False,
            "modelAuthorshipIndependentlyVerified": False,
            "briefAndCopySentToModel": False,
            "onlyAbstractDesignEnumsSent": constrained_intent is not None,
            "abstractDesignIntentProvided": constrained_intent is not None,
            "abstractDesignIntentSha256": (None if constrained_intent is None
                else intent_spec.sha256(constrained_intent)),
            "staticPreferenceAlignment": alignment,
            "humanAestheticReviewPerformed": False,
            "sourceCodeSentToModel": False,
            "modelAuthoredCssOrJavascript": False,
            "candidateResponseSha256": hashlib.sha256(raw).hexdigest(),
            "previewSha256": hashlib.sha256(preview).hexdigest(),
            "layoutDecision": decision,
            "ownerApproved": False,
            "deploymentAuthorized": False,
            "productionReady": False,
            "stage45Go": False,
        }
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, "w") as archive:
            for name, content in (
                ("preview.zip", preview),
                ("receipt.json", _canonical(receipt)),
            ):
                info = zipfile.ZipInfo(name, layouts.reference.PINNED_TIME)
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o100600 << 16
                archive.writestr(info, content, compress_type=zipfile.ZIP_DEFLATED,
                                 compresslevel=9)
        return stream.getvalue(), receipt


def cli(argv: Sequence[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--model", required=True)
    p.add_argument("--run-local", action="store_true")
    p.add_argument("--with-intent", action="store_true",
                   help="Send only separately opted-in abstract design categories")
    args = p.parse_args(argv)
    try:
        models.checked_choice("ollama", args.model)
        if not args.run_local:
            print(json.dumps({
                "status": "PLAN_ONLY_NO_INFERENCE_OR_BRIEF_ACCESS",
                "task": TASK,
                "modelInstalledVerified": False,
                "modelCalls": 0,
                "remoteFallback": False,
                "ownerApproved": False,
                "stage45Go": False,
            }, sort_keys=True))
            return 0
        if (os.environ.get(models.OPT_LOCAL) != "yes"
            or os.environ.get(sessions.LOCAL_EXPERIMENT_OPT_IN) != "yes"
            or os.environ.get(OPT_LAYOUT) != "yes"):
            raise ValueError("E5_LAYOUT_THREE_OPT_INS_REQUIRED")
        if args.with_intent and os.environ.get(OPT_INTENT) != "yes":
            raise ValueError("E5_INTENT_EXPLICIT_FOURTH_OPT_IN_REQUIRED")
        if not FOLDER.is_dir() or FOLDER.is_symlink():
            raise ValueError("E5_LAYOUT_UNSAFE_FOLDER")
        if OUTPUT.is_symlink() or OUTPUT.exists():
            raise ValueError("E5_LAYOUT_OUTPUT_ALREADY_EXISTS")
        brief = briefs.read_brief(briefs._read_regular(BRIEF, briefs.MAX_BRIEF_BYTES))
        text_proposal = copies.read_proposal(briefs._read_regular(COPY, copies.MAX_INPUT))
        design_intent = (intent_spec.read_intent(
            briefs._read_regular(INTENT, intent_spec.MAX_INTENT_BYTES))
            if args.with_intent else None)
        bundle, receipt = LocalLayoutTrial().execute(
            args.model, brief, text_proposal, design_intent)
        flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0)
        fd = os.open(OUTPUT, flags, 0o600)
        with os.fdopen(fd, "wb") as output:
            output.write(bundle)
        print(json.dumps({
            "status": "LOCAL_LAYOUT_TOKEN_REVIEW_ONLY",
            "bundleSha256": hashlib.sha256(bundle).hexdigest(),
            "modelIdentityCryptographicallyAttested": False,
            "modelAuthoredCssOrJavascript": False,
            "ownerApproved": False,
            "stage45Go": False,
        }, sort_keys=True))
        return 0
    except (OSError, ValueError, TimeoutError):
        # Never log model output, prompts, paths, environment or credentials.
        print(json.dumps({"status": "LOCAL_LAYOUT_REFUSED_OR_UNAVAILABLE",
                          "remoteFallback": False, "stage45Go": False}, sort_keys=True))
        return 2


if __name__ == "__main__":
    sys.exit(cli())
