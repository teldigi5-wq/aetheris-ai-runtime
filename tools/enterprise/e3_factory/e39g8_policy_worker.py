#!/usr/bin/env python3
"""E3.9G-8 network-NONE worker: frozen policy alone may change disposable CSS.

No model/network/tools here. Re-check genuine E39G7 model evidence, source,
before-browser hashes, independent E39G7 score and unchanged E39E CSS gate.
Never treats static eligibility or deterministic cleanup as browser success.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import re
import sys

import e39d_heldout_focus_evaluator as fixture
import e39e_model_policy as frozen

TOKEN = re.compile(r"(granite4-3b|hermes3-3b)--(top-120|opacity-zero)--(baseline|skill-context)\Z")
ROOT = fixture.ROOT / "e39g8"
MODEL = {"granite4-3b":"granite4:3b","hermes3-3b":"hermes3:3b"}

def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()

def unpack(token: str):
    match=TOKEN.fullmatch(token)
    if match is None: raise ValueError("E39G8_FIXED_MODEL_CASE_ARM_REQUIRED")
    slug,case,arm=match.groups()
    directory=ROOT/slug/case/arm
    if directory.is_symlink(): raise ValueError("E39G8_SYMLINK_OUTPUT_DENIED")
    return slug,case,arm,directory

def evidence(token):
    slug,case,arm,folder=unpack(token)
    source,canonical=frozen.snapshot(case)  # still exact broken CSS with verified BEFORE
    orig=fixture.location(case)
    g7=frozen.load(orig / ("e39g7-"+slug+"-"+arm+".json"),7000)
    scored=frozen.load(orig / ("e39g7-"+slug+"-score.json"),7500)
    before={v:sha(fixture.checked(orig/(v+"-before.json"),4096))
            for v in ("mobile","desktop")}
    if (g7.get("evidenceClass")!="E39G7_REAL_SKILL_VS_BASELINE_OLLAMA_REPLY"
        or g7.get("caseId")!=case or g7.get("arm")!=arm
        or g7.get("modelTag")!=MODEL[slug]
        or g7.get("realModelCalls")!=1 or g7.get("actualModelCompletion") is not True
        or g7.get("websiteEdited") is not False
        or g7.get("modelTextExecuted") is not False
        or g7.get("chromiumVerifiedRepair") is not False
        or g7.get("ownerApprovalGranted") is not False
        or g7.get("productionPublished") is not False
        or g7.get("sourceSha256")!=sha(source)
        or g7.get("browserBeforeSha256")!=before
        or scored.get("evidenceClass")!="E39G7_REAL_MODEL_TWO_ARM_POLICY_ELIGIBILITY_ONLY"
        or scored.get("modelTag")!=MODEL[slug]
        or scored.get("caseId")!=case or scored.get("realModelCalls")!=2
        or scored.get("patchApplied") is not False
        or scored.get("browserAfterModelRepairVerified") is not False
        or scored.get("externallyCuratedSealedCorpus") is not False):
        raise ValueError("E39G8_G7_MODEL_OR_BROWSER_PROVENANCE_INVALID")
    raw=g7.get("rawModelResponse")
    if type(raw) is not str or g7.get("rawResponseSha256")!=sha(raw.encode()):
        raise ValueError("E39G8_TAMPERED_MODEL_RESPONSE")
    score=scored.get("arms",{}).get(arm,{})
    if (score.get("modelResponseSha256")!=sha(raw.encode())
        or score.get("promptSha256")!=g7.get("promptSha256")
        or score.get("arm")!=arm):
        raise ValueError("E39G8_UNBOUND_G7_POLICY_SCORE")
    classified=score.get("outcome")
    if classified not in ("POLICY_ELIGIBLE_NOT_BROWSER_TESTED",
                          "POLICY_REJECTED","ABSTAIN","INVALID"):
        raise ValueError("E39G8_UNKNOWN_STATIC_OUTCOME")
    eligible=classified=="POLICY_ELIGIBLE_NOT_BROWSER_TESTED"
    parsed=None
    amended=source
    try:
        parsed=frozen.parse_response(raw)
        amended=frozen.validate_candidate(parsed,source,canonical)
    except ValueError:
        if eligible: raise ValueError("E39G8_STATIC_POLICY_CLAIM_FALSE")
    else:
        if not eligible: raise ValueError("E39G8_VALID_PATCH_WAS_MISCLASSIFIED")
    if score.get("policyEligible") is not eligible:
        raise ValueError("E39G8_STATIC_BOOLEAN_MISMATCH")
    return slug,case,arm,folder,source,amended,g7,eligible,classified

def apply(token: str) -> None:
    slug,case,arm,folder,source,amended,g7,eligible,classified=evidence(token)
    if folder.exists() or folder.is_symlink():
        raise ValueError("E39G8_ARM_OUTPUT_ALREADY_EXISTS")
    folder.mkdir(parents=True,mode=0o700)
    if eligible:
        fixture.STYLE.write_bytes(amended)
        if fixture.checked(fixture.STYLE,25000)!=amended:
            raise ValueError("E39G8_WORKER_WRITE_MISMATCH")
    else:
        if fixture.checked(fixture.STYLE,25000)!=source:
            raise ValueError("E39G8_INELIGIBLE_CHANGED_SITE")
    receipt={
        "evidenceClass":"E39G8_NETWORK_NONE_FROZEN_POLICY_MODEL_PATCH",
        "modelTag":MODEL[slug],"caseId":case,"arm":arm,
        "actualModelCallsFromVerifiedG7Receipt":1,
        "modelRawSha256":g7["rawResponseSha256"],
        "beforeBrowserSha256":g7["browserBeforeSha256"],
        "sourceBeforeSha256":sha(source),
        "sourceAfterSha256":sha(amended if eligible else source),
        "frozenPolicyEligible":eligible,
        "sandboxPatchApplied":eligible,
        "policyClassification":classified,
        "testOnlyGeneratedSite":True,"ownerPcUsed":False,
        "ownerApprovalGranted":False,"productionPublished":False,
        "modelBrowserRepairVerified":False,
    }
    with (folder/"patch-receipt.json").open("x") as f:
        json.dump(receipt,f,sort_keys=True,indent=2);f.write("\n")
    print("E39G8_NETWORK_NONE_MODEL_POLICY_APPLY",MODEL[slug],case,arm,
          "applied",eligible)

def cleanup(token: str) -> None:
    slug,case,arm,folder=unpack(token)
    receipt=frozen.load(folder/"patch-receipt.json",4500)
    score=frozen.load(folder/"model-browser-score.json",6500)
    orig=fixture.defective_source(case)
    if (receipt.get("evidenceClass")!="E39G8_NETWORK_NONE_FROZEN_POLICY_MODEL_PATCH"
        or receipt.get("modelTag")!=MODEL[slug]
        or receipt.get("caseId")!=case or receipt.get("arm")!=arm
        or receipt.get("sourceBeforeSha256")!=sha(orig)
        or score.get("evidenceClass")!="E39G8_INDEPENDENT_CHROMIUM_MODEL_REPAIR_SCORE"
        or score.get("modelTag")!=MODEL[slug] or score.get("caseId")!=case
        or score.get("arm")!=arm
        or score.get("ownerApprovalGranted") is not False
        or score.get("productionPublished") is not False
        or (folder/"cleanup.json").exists()
        or (folder/"cleanup.json").is_symlink()):
        raise ValueError("E39G8_CLEANUP_PROVENANCE_INVALID")
    current=fixture.checked(fixture.STYLE,25000)
    if sha(current)!=receipt.get("sourceAfterSha256"):
        raise ValueError("E39G8_CLEANUP_SOURCE_DRIFT")
    # No direct model code execution: deterministic TEST ONLY reset to broken
    # source allows the next arm to use precisely the same BEFORE fault.
    fixture.STYLE.write_bytes(orig)
    if fixture.checked(fixture.STYLE,25000)!=orig:
        raise ValueError("E39G8_CLEANUP_RESET_MISMATCH")
    with (folder/"cleanup.json").open("x") as f:
        json.dump({"evidenceClass":"E39G8_NON_MODEL_RESET_TO_SAME_DEFECT",
                   "modelGeneratedRepair":False,"ownerApprovalGranted":False,
                   "sourceAfterResetSha256":sha(orig)},f,sort_keys=True);f.write("\n")
    print("E39G8_NETWORK_NONE_TEST_RESET_NO_MODEL_CREDIT",MODEL[slug],case,arm)

if __name__=="__main__":
    if len(sys.argv)!=2 or sys.argv[1] not in ("apply","cleanup"):
        # invoked as <action> <token>, not by accepting arbitrary file paths
        if len(sys.argv)!=3 or sys.argv[1] not in ("apply","cleanup"):
            raise ValueError("E39G8_ONLY_FIXED_ACTION_AND_TOKEN")
    if len(sys.argv)!=3: raise ValueError("E39G8_EXACT_TWO_ARGS")
    {"apply":apply,"cleanup":cleanup}[sys.argv[1]](sys.argv[2])
