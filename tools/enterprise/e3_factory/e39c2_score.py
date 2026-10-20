#!/usr/bin/env python3
"""E3.9C-2A score genuine model proposals against TWO actual Chromium viewports.

Nonaccepted model text must leave the broken source unchanged. Policy-accepted
text has zero credit unless both keyboard-focused browser checks pass.
"""
from __future__ import annotations
import hashlib
import json
import sys

import e39_keyboard_patch_gate as base
import e39c_selector_policy as css

STATES = frozenset(("POLICY_ACCEPTED", "POLICY_REJECTED", "ABSTAIN", "INVALID"))


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def measure(state: str) -> dict:
    if state not in STATES:
        raise ValueError("E39C2_UNKNOWN_SCORE_STATUS")
    source = base.checked(base.STYLE, 25_000)
    seed = json.loads(base.checked(base.DIR / "seed.json", 4096))
    before_raw = base.checked(base.DIR / "browser-before.json", 4096)
    before = json.loads(before_raw)
    model = json.loads(base.checked(base.DIR / "real-selector-model-response.json", 4600))
    if (model.get("evidenceClass") != "E39C2_REAL_LOCAL_SELECTOR_MODEL_ADVISORY"
            or model.get("sourceSha256") != seed.get("beforeSha256")
            or model.get("browserBeforeSha256") != sha(before_raw)
            or model.get("realModelCalls") != 1
            or model.get("ownerApproved") is not False
            or model.get("productionPublished") is not False
            or model.get("modelOutputExecutedDirectly") is not False
            or before.get("passed") is not False
            or before.get("failureCode") != "E39_FOCUSED_SKIP_LINK_OFFSCREEN"):
        raise ValueError("E39C2_MODEL_OR_REAL_BROWSER_PROVENANCE_INVALID")
    kind = model.get("responseClass")
    if (state == "ABSTAIN" and kind != "ABSTAIN"
            or state == "INVALID" and kind != "INVALID_MODEL_RESPONSE"
            or state in ("POLICY_ACCEPTED", "POLICY_REJECTED")
               and kind != "SELECTOR_VALUE_PROPOSED"):
        raise ValueError("E39C2_RESPONSE_CLASS_AND_POLICY_MISMATCH")
    after_evidence = {}
    passed = False
    if state == "POLICY_ACCEPTED":
        receipt = json.loads(base.checked(base.DIR / "receipt.json", 4096))
        canonical = base.checked(base.worker.HERE / "styles.css", 25_000)
        px = receipt.get("topPx")
        if type(px) is not int or px not in css.ALLOWED_TOP_PIXELS:
            raise ValueError("E39C2_ACCEPTED_TOP_VALUE_INVALID")
        modified = canonical.decode().replace(
            base.AFTER, f"{css.SELECTOR}{{top:{px}px}}", 1).encode()
        if (receipt.get("modelTag") != model["model"]["tag"]
                or receipt.get("evidenceClass") !=
                   "E39C2_MODEL_CSS_POLICY_ACCEPTED_BROWSER_PENDING"
                or receipt.get("modelTextProposed") is not True
                or receipt.get("modelTextDirectlyExecuted") is not False
                or receipt.get("ownerApproved") is not False
                or receipt.get("productionPublished") is not False
                or receipt.get("afterSha256") != sha(source)
                or modified != source):
            raise ValueError("E39C2_MODEL_POLICY_RECEIPT_MISMATCH")
        for label, name in (("mobile", "browser-after.json"),
                            ("desktop", "desktop-after.json")):
            proof = json.loads(base.checked(base.DIR / name, 4096))
            after_evidence[label] = proof
        passed = all(
            proof.get("evidenceClass") == "E39_CHROMIUM_KEYBOARD_ACCESSIBILITY"
            and proof.get("passed") is True
            and proof.get("failureCode") is None
            and proof.get("focusReached") is True
            and proof.get("visibleWhenFocused") is True
            and proof.get("pageJavaScriptErrors") == []
            and proof.get("geometry", {}).get("top", -1) >= 0
            and proof.get("geometry", {}).get("bottom", 9999)
                <= proof.get("geometry", {}).get("viewportH", 0)
            for proof in after_evidence.values()
        )
    else:
        canonical = base.checked(base.worker.HERE / "styles.css", 25_000)
        broken = canonical.decode().replace(base.AFTER, base.BEFORE, 1).encode()
        if (source != broken or sha(source) != seed.get("beforeSha256")
                or (base.DIR / "receipt.json").exists()):
            raise ValueError("E39C2_REJECTED_PROPOSAL_CHANGED_SOURCE")
    output = {
        "evidenceClass": "E39C2_GENUINE_MODEL_TWO_VIEWPORT_CHROMIUM_RESULT",
        "model": model["model"], "realModelCalls": 1,
        "responseClass": kind, "policyOutcome": state,
        "policyAccepted": state == "POLICY_ACCEPTED",
        "chromiumVerifiedModelRepair": passed,
        "beforeBrowserFailedAsExpected": True,
        "mobileDesktopAfterEvidence": after_evidence,
        "generatedTokens": model["selfReportedGeneratedTokens"],
        "wallMs": model["wallMs"],
        "modelOutputExecutedDirectly": False,
        "ownerApproved": False, "productionPublished": False,
        "ownerPcUsed": False, "generalizationCertified": False,
    }
    score_file = base.DIR / "e39c2-model-score.json"
    if score_file.exists() or score_file.is_symlink():
        raise ValueError("E39C2_SCORE_EXISTS")
    with score_file.open("x", encoding="utf-8") as handle:
        json.dump(output, handle, indent=2, sort_keys=True)
        handle.write("\n")
    print("E39C2_GENUINE_TWO_VIEWPORT_MODEL_SCORE", model["model"]["tag"],
          "verified", passed, "status", state, "ms", output["wallMs"])
    return output


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise ValueError("E39C2_SINGLE_POLICY_OUTCOME_REQUIRED")
    measure(sys.argv[1])
