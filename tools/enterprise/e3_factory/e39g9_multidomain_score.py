#!/usr/bin/env python3
"""E3.9G-9 independent browser/outcome scorer. Never imported by proposer.

No model patches are allowed unless ONE old E33 grammar literal produces
exact canonical code; a model repair is counted only with two REAL Chromium
AFTER screenshots/verdicts. Fixture resets receive strictly ZERO AI credit.
"""
from __future__ import annotations
import json
from pathlib import Path
import sys

import e39g9_multidomain_worker as gate

def score(token:str):
    slug,case,arm,folder=gate.trial(token)
    receipt=gate.read_json(folder/"patch.json",4500)
    model=gate.read_json(gate.case_folder(case)/
        ("e39g9-"+slug+"-"+arm+".json"),8500)
    target,canonical,broken,_=gate.before_source_only(case)
    actual=gate.read(target,25000)
    applied=receipt.get("sandboxPatchApplied")
    if (receipt.get("evidenceClass")!="E39G9_REAL_MODEL_PROPOSAL_NETWORK_NONE_POLICY_RECEIPT"
        or receipt.get("caseId")!=case or receipt.get("modelTag")!=gate.MODEL[slug]
        or receipt.get("arm")!=arm or applied is not (receipt.get("policyOutcome")=="POLICY_APPLIED")
        or receipt.get("beforeSha256")!=gate.sha(broken)
        or receipt.get("afterSha256")!=gate.sha(actual)
        or receipt.get("modelRawSha256")!=model.get("rawResponseSha256")
        or model.get("realModelCalls")!=1
        or receipt.get("realModelCalls")!=1
        or receipt.get("ownerApproved") is not False
        or receipt.get("productionPublished") is not False):
        raise ValueError("E39G9_MODEL_WORKER_RECEIPT_NOT_TRUSTED")
    if applied:
        candidate=gate.strict_reply(model["rawModelResponse"])
        expected=gate.permitted(case,candidate,broken,canonical)
        if actual!=expected:raise ValueError("E39G9_MODEL_PATCH_NOT_FROM_FROZEN_POLICY")
    elif actual!=broken:
        raise ValueError("E39G9_POLICY_DENIED_BUT_SITE_CHANGED")
    after=[]
    if applied:
        for viewport in ("mobile","desktop"):
            obj=gate.read_json(folder/(viewport+"-after.json"),6500)
            screenshot=gate.read(folder/(viewport+"-after.png"),2_000_000)
            if (obj.get("evidenceClass")!="E39G9_INDEPENDENT_REAL_CHROMIUM_FUNCTIONAL"
                or obj.get("phase")!="after" or obj.get("caseId")!=case
                or obj.get("viewport")!=viewport or obj.get("modelSlug")!=slug
                or obj.get("arm")!=arm
                or obj.get("sourceSha256")!=gate.sha(actual)
                or obj.get("screenshotSha256")!=gate.sha(screenshot)
                or obj.get("ownerPcUsed") is not False
                or obj.get("pageJavaScriptErrors")!=[]):
                raise ValueError("E39G9_FAKE_OR_UNVERIFIED_AFTER_BROWSER")
            after.append({"viewport":viewport,"passed":obj.get("passed") is True,
                          "failureCode":obj.get("failureCode"),
                          "sourceSha256":obj.get("sourceSha256"),
                          "screenshotSha256":obj.get("screenshotSha256")})
    else:
        if any((folder/(v+"-after.json")).exists() or
               (folder/(v+"-after.png")).exists() for v in ("mobile","desktop")):
            raise ValueError("E39G9_INELIGIBLE_MODEL_BROWSER_FAKED")
    success=applied is True and len(after)==2 and all(
       v["passed"] and v["failureCode"] is None for v in after)
    result={
      "evidenceClass":"E39G9_REAL_CHROMIUM_SCORED_MODEL_PATCH",
      "caseId":case,"modelTag":gate.MODEL[slug],"arm":arm,
      "modelRawSha256":model["rawResponseSha256"],
      "realModelCalls":1,"policyOutcome":receipt["policyOutcome"],
      "policyAcceptedAndApplied":applied is True,
      "browserVerifiedModelRepair":success,
      "independentRealChromiumAfterViewports":len(after),
      "beforeSourceSha256":gate.sha(broken),"afterSourceSha256":gate.sha(actual),
      "browserAfter":after,"deterministicResetCountedAsModelFix":False,
      "externallyCuratedBlindBenchmark":False,"ownerAuthenticated":False,
      "ownerApproved":False,"productionPublished":False,"stage45Go":False,
    }
    path=folder/"score.json"
    if path.exists() or path.is_symlink():raise ValueError("E39G9_ALREADY_SCORED")
    with path.open("x") as f:
        json.dump(result,f,indent=2,sort_keys=True);f.write("\n")
    print("E39G9_GENUINE_MULTI_DOMAIN_CHROMIUM_REPAIR_VERDICT",
          gate.MODEL[slug],case,arm,"applied",applied,
          "real2ViewportRepair",success)
    return result

def summarize(model:str):
    if model not in gate.MODEL.values():raise ValueError("E39G9_UNREVIEWED_MODEL")
    slug=next(k for k,v in gate.MODEL.items() if v==model)
    cases=list(gate.CASES)
    arms={}
    for arm in gate.ARMS:
        casescores=[]
        for case in cases:
            folder=gate.OUT/"models"/slug/case/arm
            score=gate.read_json(folder/"score.json",6500)
            reset=gate.read_json(folder/"reset.json",2000)
            done=gate.read_json(gate.case_folder(case)/("finished-"+slug+".json"),2000)
            if (score.get("evidenceClass")!="E39G9_REAL_CHROMIUM_SCORED_MODEL_PATCH"
                or score.get("modelTag")!=model or score.get("caseId")!=case
                or score.get("arm")!=arm or score.get("realModelCalls")!=1
                or score.get("deterministicResetCountedAsModelFix") is not False
                or score.get("ownerApproved") is not False
                or score.get("stage45Go") is not False
                or reset.get("evidenceClass")!="E39G9_ZERO_MODEL_CREDIT_RESEED_DEFECT"
                or done.get("evidenceClass")!="E39G9_DETERMINISTIC_FINAL_RESET_NOT_AI_REPAIR"
                or done.get("modelRepair") is not False):
                raise ValueError("E39G9_INCOMPLETE_OR_FORGED_COHORT")
            casescores.append(score)
        arms[arm]={
          "cases":len(casescores),
          "policyApplied":sum(s["policyAcceptedAndApplied"] is True for s in casescores),
          "independentBrowserVerifiedRepairs":sum(s["browserVerifiedModelRepair"] is True for s in casescores),
          "realAfterViewportsRun":sum(s["independentRealChromiumAfterViewports"] for s in casescores),
          "perCase":[{"caseId":s["caseId"],"policyOutcome":s["policyOutcome"],
                      "browserVerified":s["browserVerifiedModelRepair"]} for s in casescores],
        }
    report={
      "evidenceClass":"E39G9_THREE_PUBLIC_SYNTHETIC_WEBSITE_FUNCTIONAL_FAMILIES",
      "modelTag":model,"realModelCalls":6,"newCategories":["mobile reflow","reduced motion","scroll responsiveness"],
      "arms":arms,"skillBenefitEstablished":False,
      "externalHeldOutEvaluation":False,"productionReleased":False,
      "stage45Go":False,"ownerPcUsed":False,
      "testSiteOnly":True,
    }
    dest=gate.OUT/"models"/slug/"summary.json"
    if dest.exists() or dest.is_symlink():raise ValueError("E39G9_ALREADY_SUMMARIZED")
    with dest.open("x") as f:
        json.dump(report,f,indent=2,sort_keys=True);f.write("\n")
    print("E39G9_TRUE_MULTI_DOMAIN_MODEL_BROWSER_SUMMARY",json.dumps(report,sort_keys=True))
    return report

if __name__=="__main__":
    args=sys.argv[1:]
    if len(args)==2 and args[0]=="score":score(args[1])
    elif len(args)==2 and args[0]=="summarize":summarize(args[1])
    else:raise ValueError("E39G9_FIXED_SCORE_TOKEN_OR_SUMMARIZE_MODEL")
