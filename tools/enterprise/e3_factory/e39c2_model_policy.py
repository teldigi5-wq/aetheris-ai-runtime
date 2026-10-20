#!/usr/bin/env python3
"""E3.9C-2A independent offline policy: real model's scalar is only a proposal.

This runs exclusively in the reviewed e39c2-apply network-none worker.
It does not import the proposer or grant its model shell/browser access.
"""
from __future__ import annotations

import hashlib
import json
import sys

import e39_keyboard_patch_gate as base
import e39c_selector_policy as css

ALLOWED = frozenset(("hermes3:3b", "granite4:3b"))
RESPONSE_KEYS = frozenset(("operation", "selector", "property", "topPx"))


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def parse_untrusted(raw: str) -> dict:
    if not isinstance(raw, str) or len(raw.encode()) > 1200:
        raise ValueError("E39C2_POLICY_TEXT_SIZE_REFUSED")
    def unique(pairs):
        value = {}
        for name, content in pairs:
            if name in value:
                raise ValueError("E39C2_POLICY_DUPLICATE_KEY_REFUSED")
            value[name] = content
        return value
    try:
        parsed = json.loads(raw, object_pairs_hook=unique,
                            parse_constant=lambda _: (_ for _ in ()).throw(
                                ValueError("E39C2_NONFINITE_NUMBER_REFUSED")))
    except json.JSONDecodeError as error:
        raise ValueError("E39C2_INVALID_JSON") from error
    if (not isinstance(parsed, dict) or set(parsed) != RESPONSE_KEYS
            or parsed.get("operation") != "replace"
            or parsed.get("selector") != css.SELECTOR
            or parsed.get("property") != css.PROPERTY
            or type(parsed.get("topPx")) is not int):
        raise ValueError("E39C2_WRONG_SELECTOR_OR_RESPONSE_SCHEMA")
    return parsed


def validate_evidence(claim: dict, source: bytes, browser: bytes) -> tuple[dict, str]:
    if (not isinstance(claim, dict)
            or claim.get("evidenceClass") != "E39C2_REAL_LOCAL_SELECTOR_MODEL_ADVISORY"
            or claim.get("caseId") != "keyboard-skip-link"
            or claim.get("realModelCalls") != 1
            or claim.get("promptVersion") != "E39C2_SELECTOR_PROPERTY_VALUE_NONORACLE_V1"
            or claim.get("sourceSha256") != sha(source)
            or claim.get("browserBeforeSha256") != sha(browser)
            or claim.get("fixtureGoldenPatchConsulted") is not False
            or claim.get("modelOutputExecutedDirectly") is not False
            or claim.get("ownerApproved") is not False
            or claim.get("productionPublished") is not False
            or claim.get("ownerPcUsed") is not False
            or claim.get("responseClass") != "SELECTOR_VALUE_PROPOSED"):
        raise ValueError("E39C2_PROVENANCE_OR_AUTHORITIES_DENIED")
    model = claim.get("model")
    if not isinstance(model, dict) or model.get("tag") not in ALLOWED:
        raise ValueError("E39C2_MODEL_NOT_ALLOWLISTED")
    raw = claim.get("rawResponse")
    if not isinstance(raw, str) or claim.get("rawResponseSha256") != sha(raw.encode()):
        raise ValueError("E39C2_MODEL_TEXT_SHA256_INVALID")
    return parse_untrusted(raw), model["tag"]


def apply() -> None:
    base.ensure_workspace()
    browser = base.actual_browser_failure()
    source = base.checked(base.STYLE, 25_000)
    manifest = json.loads(base.checked(base.SITE / "manifest.json", 4096))
    model_file = base.checked(base.DIR / "real-selector-model-response.json", 4600)
    claim = json.loads(model_file)
    proposal, model = validate_evidence(claim, source, browser)
    scalar = proposal["topPx"]
    new_rule = f"{css.SELECTOR}{{top:{scalar}px}}"
    canonical = base.checked(base.worker.HERE / "styles.css", 25_000)
    result_candidate = canonical.decode().replace(base.AFTER, new_rule, 1).encode()
    candidate = {
        "version": 1, "kind": css.KIND, "caseId": "keyboard-skip-link",
        "path": "site/styles.css", "selector": css.SELECTOR,
        "property": css.PROPERTY,
        "baseSha256": sha(source),
        "beforeBrowserSha256": sha(browser),
        "resultSha256": sha(result_candidate),
        "old": base.BEFORE, "new": new_rule,
    }
    verified, offset = css.candidate_result(
        candidate, source, browser, canonical, manifest)
    receipt_file = base.DIR / "receipt.json"
    if receipt_file.exists() or receipt_file.is_symlink():
        raise ValueError("E39C2_RECEIPT_ALREADY_EXISTS")
    base.STYLE.write_bytes(verified)
    if base.checked(base.STYLE, 25_000) != verified:
        raise ValueError("E39C2_WRITE_MISMATCH")
    receipt = {
        "evidenceClass": "E39C2_MODEL_CSS_POLICY_ACCEPTED_BROWSER_PENDING",
        "modelTag": model, "caseId": "keyboard-skip-link",
        "selector": css.SELECTOR, "property": css.PROPERTY,
        "topPx": offset, "sourceSha256": sha(source),
        "afterSha256": sha(verified),
        "rawModelTextSha256": sha(claim["rawResponse"].encode()),
        "modelTextProposed": True, "modelTextDirectlyExecuted": False,
        "ownerApproved": False, "productionPublished": False,
        "independentBrowserRequired": True,
    }
    with receipt_file.open("x", encoding="utf-8") as handle:
        json.dump(receipt, handle, sort_keys=True, indent=2)
        handle.write("\n")
    print("E39C2_SELECTOR_MODEL_POLICY_ACCEPTED_AWAIT_BROWSER", model, offset)


if __name__ == "__main__":
    if sys.argv[1:] != ["apply"]:
        raise ValueError("E39C2_ONLY_FIXED_APPLY_PHASE")
    apply()
