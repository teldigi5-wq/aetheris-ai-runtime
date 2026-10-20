#!/usr/bin/env python3
"""E3.9G-10 independent scorer for actual policy-approved browser repairs.
Counts only real proposed and applied code that passed Chromium on BOTH viewports.
No model calls or source writes; deterministic worker cleanup has ZERO score.
"""
from __future__ import annotations
import json
import sys
import e39g9_multidomain_worker as prior
import e39g10_targeted_gate as gate

def score(token):
    slug,case,arm,folder=gate.trial(token)
    target,clean,broken,___=prior.before_source_only(case)
    actual=prior.read(target,25000)
    proposal=gate.model_record(slug,case,arm,broken,
        {view:prior.sha(prior.read(prior.case_folder(case)/(view+"-before.json"),6000))
         for view in ("mobile","desktop")})
    receipt=prior.read_json(folder/"patch.json",5000)
    applied=receipt.get("patchApplied")
    if (receipt.get("evidenceClass")!="E39G10_NETWORK_NONE_MODEL_SOURCE_POLICY"
        or receipt.get("modelTag")!=prior.MODEL[slug]
        or receipt.get("caseId")!=case or receipt.get("arm")!=arm
        or receipt.get("rawResponseSha256")!=proposal["rawResponseSha256"]
        or receipt.get("beforeSourceSha256")!=prior.sha(broken)
        or receipt.get("afterSourceSha256")!=prior.sha(actual)
        or receipt.get("realModelCalls")!=1
        or receipt.get("ownerApproved") is not False
        or receipt.get("productionPublished") is not False
        or applied is not (receipt.get("policyOutcome")=="POLICY_APPLIED")):
        raise ValueError("E39G10_UNBOUND_MODEL_POLICY_RECEIPT")
    if applied:
        parsed=prior.strict_reply(proposal["rawModelResponse"])
        if prior.permitted(case,parsed,broken,clean)!=actual:
            raise ValueError("E39G10_MODEL_REPAIR_NOT_ORIGINAL_POLICY")
    elif actual!=broken:raise ValueError("E39G10_REJECTED_PATCH_MUTATED_SITE")
    outcomes=[]
    if applied:
        for view in ("mobile","desktop"):
            obj=prior.read_json(folder/(view+"-after.json"),6800)
            png=prior.read(folder/(view+"-after.png"),2000000)
            if (obj.get("evidenceClass")!="E39G10_INDEPENDENT_REAL_CHROMIUM_FUNCTIONAL"
                or obj.get("caseId")!=case or obj.get("phase")!="after"
                or obj.get("arm")!=arm or obj.get("viewport")!=view
                or obj.get("modelSlug")!=slug
                or obj.get("sourceSha256")!=prior.sha(actual)
                or obj.get("screenshotSha256")!=prior.sha(png)
                or obj.get("ownerPcUsed") is not False
                or obj.get("pageJavaScriptErrors")!=[]):
                raise ValueError("E39G10_FAKE_CHROMIUM_EVIDENCE")
            outcomes.append({"viewport":view,"passed":obj.get("passed") is True,
                "failure":obj.get("failureCode"),"screenshotSha256":obj["screenshotSha256"]})
    elif any((folder/(v+"-after.json")).exists() or (folder/(v+"-after.png")).exists()
             for v in ("mobile","desktop")):
        raise ValueError("E39G10_NO_POLICY_BUT_AFTER_BROWSER_CLAIM")
    confirmed=applied is True and len(outcomes)==2 and all(
        x["passed"] and x["failure"] is None for x in outcomes)
    result={"evidenceClass":"E39G10_REAL_CHROMIUM_INDEPENDENT_MODEL_GRADE",
        "caseId":case,"modelTag":prior.MODEL[slug],"arm":arm,"realModelCalls":1,
        "policyOutcome":receipt["policyOutcome"],"policyApplied":applied,
        "browserVerifiedModelRepair":confirmed,"afterChromiumViewports":len(outcomes),
        "browserEvidence":outcomes,"modelResponseSha256":proposal["rawResponseSha256"],
        "deterministicResetCountedAsRepair":False,"ownerApproved":False,
        "productionPublished":False,"stage45Go":False,
        "externallyCuratedHeldOut":False}
    file=folder/"score.json"
    if file.exists() or file.is_symlink():raise ValueError("E39G10_ALREADY_SCORED")
    with file.open("x") as f:json.dump(result,f,sort_keys=True,indent=2);f.write("\n")
    print("E39G10_ACTUAL_MODEL_BROWSER_GRADE",prior.MODEL[slug],case,arm,
          "policy",applied,"twoViewportRepair",confirmed)
    return result

def summarize(model):
    if model not in prior.MODEL.values():raise ValueError("E39G10_MODEL_NOT_ALLOWED")
    slug=next(k for k,v in prior.MODEL.items() if v==model)
    arms={}
    for arm in gate.ARMS:
        items=[]
        for case in prior.CASES:
            folder=gate.OUT/"models"/slug/case/arm
            row=prior.read_json(folder/"score.json",7000)
            reset=prior.read_json(folder/"reset.json",1800)
            done=prior.read_json(gate.OUT/"cases"/case/("finished-"+slug+".json"),1600)
            if (row.get("evidenceClass")!="E39G10_REAL_CHROMIUM_INDEPENDENT_MODEL_GRADE"
                or row.get("modelTag")!=model or row.get("caseId")!=case
                or row.get("arm")!=arm or row.get("realModelCalls")!=1
                or row.get("deterministicResetCountedAsRepair") is not False
                or row.get("ownerApproved") is not False
                or reset.get("evidenceClass")!="E39G10_NONMODEL_RESET_NEVER_CREDIT"
                or reset.get("modelGeneratedRepair") is not False
                or done.get("evidenceClass")!="E39G10_FIXTURE_RECOVERY_NONMODEL"
                or done.get("modelRepair") is not False):
                raise ValueError("E39G10_INCOMPLETE_MODEL_CASE_COHORT")
            items.append(row)
        arms[arm]={"cases":len(items),
            "policyApplied":sum(v["policyApplied"] is True for v in items),
            "browserVerifiedRepairs":sum(v["browserVerifiedModelRepair"] is True for v in items),
            "afterViewports":sum(v["afterChromiumViewports"] for v in items),
            "perCase":[{"case":v["caseId"],"policy":v["policyOutcome"],
                "realBrowserRepair":v["browserVerifiedModelRepair"]} for v in items]}
    report={"evidenceClass":"E39G10_SOURCE_TARGETED_PROMPT_AB_PUBLIC_SYNTHETIC",
        "modelTag":model,"realModelCalls":6,"pairedCases":3,
        "comparison":"same_model_skills_and_cases_only_prompt_differs",
        "arms":arms,"verifiedGeneralization":False,"skillImpactIdentified":False,
        "noOwnerPc":True,"productionPublished":False,"stage45Go":False}
    dest=gate.OUT/"models"/slug/"summary.json"
    if dest.exists() or dest.is_symlink():raise ValueError("E39G10_DUPLICATE_SUMMARY")
    with dest.open("x") as f:json.dump(report,f,sort_keys=True,indent=2);f.write("\n")
    print("E39G10_REAL_MODEL_PROMPT_AB_SUMMARY",json.dumps(report,sort_keys=True))
    return report

if __name__=="__main__":
    if len(sys.argv)!=3:raise ValueError("E39G10_SCORE_OR_SUMMARIZE_TWO_ARGS")
    if sys.argv[1]=="score":score(sys.argv[2])
    elif sys.argv[1]=="summarize":summarize(sys.argv[2])
    else:raise ValueError("E39G10_INVALID_SCORER_ACTION")
