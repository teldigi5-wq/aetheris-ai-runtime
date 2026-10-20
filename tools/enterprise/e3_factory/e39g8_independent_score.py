#!/usr/bin/env python3
"""E3.9G-8 independent after-browser model patch judge; ZERO fixture-restoration credit.

Worker and proposer never import this scorer. Counts a real model repair
ONLY when actual signed/hashed model response, frozen policy worker receipt,
and two independent real Chromium after observations agree. No live site.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import sys

import e39d_heldout_focus_evaluator as fixture
import e39e_model_policy as frozen
import e39g8_policy_worker as worker

EVIDENCE = "E39G8_INDEPENDENT_CHROMIUM_MODEL_REPAIR_SCORE"

def read(path: Path, limit: int) -> dict:
    if path.is_symlink() or not path.is_file() or path.stat().st_size > limit:
        raise ValueError("E39G8_MISSING_UNSAFE_OR_OVERSIZED_EVIDENCE")
    def keys(pairs):
        obj={}
        for k,v in pairs:
            if k in obj: raise ValueError("E39G8_DUPLICATE_RECEIPT_KEY")
            obj[k]=v
        return obj
    return json.loads(path.read_text("utf-8"),object_pairs_hook=keys)

def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()

def score(token: str) -> dict:
    slug,case,arm,folder=worker.unpack(token)
    receipt=read(folder/"patch-receipt.json",5000)
    g7=read(fixture.location(case)/("e39g7-"+slug+"-"+arm+".json"),7000)
    s7=read(fixture.location(case)/("e39g7-"+slug+"-score.json"),7500)
    before=fixture.defective_source(case)
    current=fixture.checked(fixture.STYLE,25000)
    classification=s7["arms"][arm]["outcome"]
    should_apply=classification=="POLICY_ELIGIBLE_NOT_BROWSER_TESTED"
    if (receipt.get("evidenceClass")!="E39G8_NETWORK_NONE_FROZEN_POLICY_MODEL_PATCH"
        or receipt.get("arm")!=arm or receipt.get("caseId")!=case
        or receipt.get("modelTag")!=worker.MODEL[slug]
        or receipt.get("actualModelCallsFromVerifiedG7Receipt")!=1
        or receipt.get("modelRawSha256")!=g7.get("rawResponseSha256")
        or receipt.get("beforeBrowserSha256")!=g7.get("browserBeforeSha256")
        or receipt.get("sourceBeforeSha256")!=sha(before)
        or receipt.get("sourceAfterSha256")!=sha(current)
        or receipt.get("frozenPolicyEligible") is not should_apply
        or receipt.get("sandboxPatchApplied") is not should_apply
        or receipt.get("ownerApprovalGranted") is not False
        or receipt.get("productionPublished") is not False
        or receipt.get("modelBrowserRepairVerified") is not False
        or g7.get("realModelCalls")!=1
        or g7.get("websiteEdited") is not False
        or s7.get("modelTag")!=worker.MODEL[slug]):
        raise ValueError("E39G8_FORGED_MODEL_POLICY_APPLICATION")
    if should_apply:
        proposal=frozen.parse_response(g7["rawModelResponse"])
        expected=frozen.validate_candidate(proposal,before,fixture.clean_source())
        if current!=expected:
            raise ValueError("E39G8_MODIFIED_SITE_NOT_EXPECTED_MODEL_PATCH")
    elif current!=before:
        raise ValueError("E39G8_MODEL_REJECTED_BUT_SITE_CHANGED")
    details={}
    browser_pass=False
    if should_apply:
        for viewport in ("mobile","desktop"):
            obs=read(folder/(viewport+"-after.json"),6000)
            screenshot=folder/(viewport+"-after.png")
            if screenshot.is_symlink() or not screenshot.is_file() or screenshot.stat().st_size>2_000_000:
                raise ValueError("E39G8_AFTER_BROWSER_IMAGE_MISSING")
            if (obs.get("evidenceClass")!="E39G8_INDEPENDENT_REAL_CHROMIUM_AFTER_MODEL_PATCH"
                or obs.get("caseId")!=case or obs.get("arm")!=arm
                or obs.get("modelSlug")!=slug
                or obs.get("phase")!="after" or obs.get("viewport")!=viewport
                or obs.get("sourceSha256")!=sha(current)
                or obs.get("screenshotSha256")!=sha(screenshot.read_bytes())
                or obs.get("ownerPcUsed") is not False):
                raise ValueError("E39G8_UNBOUND_REAL_BROWSER_EVIDENCE")
            details[viewport]={
                "passed":obs.get("passed") is True,
                "focusReached":obs.get("focusReached") is True,
                "visibleWhenFocused":obs.get("visibleWhenFocused") is True,
                "failureCode":obs.get("failureCode"),
                "errors":obs.get("pageJavaScriptErrors"),
                "screenshotSha256":obs.get("screenshotSha256"),
                "sourceSha256":obs.get("sourceSha256")}
        browser_pass=all(
            v["passed"] and v["focusReached"] and v["visibleWhenFocused"]
            and v["failureCode"] is None and v["errors"]==[]
            for v in details.values())
    else:
        if any((folder/(v+"-after.json")).exists() or (folder/(v+"-after.png")).exists()
               for v in ("mobile","desktop")):
            raise ValueError("E39G8_INELIGIBLE_BROWSER_REPAIR_FAKED")
    report={
        "evidenceClass":EVIDENCE,"modelTag":worker.MODEL[slug],
        "caseId":case,"arm":arm,"modelCallsVerifiedFromG7":1,
        "modelResponseSha256":g7["rawResponseSha256"],
        "sourceBeforeSha256":sha(before),"sourceAfterSha256":sha(current),
        "frozenPolicyEligible":should_apply,"actualModelProposedSandboxPatchApplied":should_apply,
        "actualModelAfterChromiumExecuted":should_apply,
        "realAfterChromiumViewports":len(details),
        "chromiumVerifiedModelRepair":should_apply and browser_pass,
        "independentBrowser":details,
        "modelTextExecutedDirectly":False,"ownerApprovalGranted":False,
        "productionPublished":False,"userPcUsed":False,
        "externallyCuratedBlindCorpus":False,
        "deterministicFixtureRestorationCredited":False,
    }
    path=folder/"model-browser-score.json"
    if path.exists() or path.is_symlink():raise ValueError("E39G8_SCORE_ALREADY_WRITTEN")
    with path.open("x",encoding="utf-8") as f:
        json.dump(report,f,sort_keys=True,indent=2);f.write("\n")
    print("E39G8_REAL_MODEL_CHROMIUM_VERIFIED",worker.MODEL[slug],case,arm,
          "applied",should_apply,"bothViewportsPassed",browser_pass)
    return report

def summarize(model: str) -> dict:
    if model not in worker.MODEL.values():raise ValueError("E39G8_UNKNOWN_MODEL")
    slug=next(s for s,t in worker.MODEL.items() if t==model)
    out=[]
    for case in ("top-120","opacity-zero"):
        for arm in ("baseline","skill-context"):
            folder=worker.ROOT/slug/case/arm
            row=read(folder/"model-browser-score.json",6000)
            clean=read(folder/"cleanup.json",2000)
            if (row.get("evidenceClass")!=EVIDENCE
                or row.get("modelTag")!=model or row.get("caseId")!=case
                or row.get("arm")!=arm or row.get("modelCallsVerifiedFromG7")!=1
                or row.get("ownerApprovalGranted") is not False
                or row.get("productionPublished") is not False
                or row.get("deterministicFixtureRestorationCredited") is not False
                or clean.get("evidenceClass")!="E39G8_NON_MODEL_RESET_TO_SAME_DEFECT"
                or clean.get("modelGeneratedRepair") is not False):
                raise ValueError("E39G8_AGGREGATE_EVIDENCE_INVALID")
            out.append(row)
    arms={}
    for arm in ("baseline","skill-context"):
        rows=[r for r in out if r["arm"]==arm]
        arms[arm]={"attempts":len(rows),
            "policyApplied":sum(r["actualModelProposedSandboxPatchApplied"] is True for r in rows),
            "actualRealChromiumRepaired":sum(r["chromiumVerifiedModelRepair"] is True for r in rows),
            "afterChromiumViewportsRun":sum(r["realAfterChromiumViewports"] for r in rows)}
    report={"evidenceClass":"E39G8_ACTUAL_MODEL_REPAIRS_IN_DISPOSABLE_REAL_CHROMIUM",
        "modelTag":model,"realModelCalls":4,"cases":2,"arms":arms,
        "noAssumedSkillBenefit":True,"heldOutExternalGeneralization":False,
        "stage45Certified":False,"userPcUsed":False,"ownerApprovalGranted":False,
        "productionPublished":False,"casesAndArms":[{"case":r["caseId"],"arm":r["arm"],
            "applied":r["actualModelProposedSandboxPatchApplied"],
            "browserVerified":r["chromiumVerifiedModelRepair"]} for r in out]}
    path=worker.ROOT/slug/"summary.json"
    if path.exists() or path.is_symlink():raise ValueError("E39G8_SUMMARY_ALREADY_EXISTS")
    with path.open("x") as f:
        json.dump(report,f,sort_keys=True,indent=2);f.write("\n")
    print("E39G8_REAL_CHROMIUM_MODEL_REPAIR_SUMMARY",json.dumps(report,sort_keys=True))
    return report

if __name__=="__main__":
    if len(sys.argv)==3 and sys.argv[1]=="score":score(sys.argv[2])
    elif len(sys.argv)==3 and sys.argv[1]=="summarize":summarize(sys.argv[2])
    else:raise ValueError("E39G8_EXACT_SCORE_TOKEN_OR_SUMMARIZE_MODEL")
