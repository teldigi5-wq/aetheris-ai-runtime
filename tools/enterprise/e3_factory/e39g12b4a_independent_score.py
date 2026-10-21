#!/usr/bin/env python3
"""E3.9G-12B4A independent real-model repair scorer, no website writes/calls."""
from __future__ import annotations
import json
from pathlib import Path
import sys

import e39g12b4a_policy_worker as worker
import e39g12b2_typed_policy as frozen


def grade(token:str)->dict:
    site,fault,folder=worker.paths(token)
    entry,broken,outer=worker.inspect(token)
    patch=worker.json_read(folder/"patch.json",6000)
    actual=worker.read(folder/"index.html",14000)
    if (patch.get("evidenceClass")!="E39G12B4A_NETWORK_NONE_TYPED_SITE_PATCH"
        or patch.get("site")!=site or patch.get("fault")!=fault
        or patch.get("modelTag")!=worker.MODEL
        or patch.get("modelRawSha256")!=entry["rawResponseSha256"]
        or patch.get("sourceBeforeSha256")!=worker.sha(broken)
        or patch.get("sourceAfterSha256")!=worker.sha(actual)
        or patch.get("realModelCallsFromUntrustedReceipt")!=1
        or patch.get("ownerApprovalGranted") is not False
        or patch.get("productionPublished") is not False
        or patch.get("modelAfterBrowserVerified") is not False
        or patch.get("isolatedNetworkNoneWorker") is not True):
        raise ValueError("E39G12B4A_FORGED_POLICY_RECEIPT")
    applied=patch.get("policyOutcome")=="POLICY_APPLIED"
    if patch.get("patchApplied") is not applied:
        raise ValueError("E39G12B4A_POLICY_BOOLEAN_INCONSISTENT")
    if applied:
        candidate=frozen.decode_json(entry["rawModelResponse"])
        expected=frozen.validate(candidate,broken)
        if actual!=expected:raise ValueError("E39G12B4A_APPLIED_NONMODEL_SOURCE")
    elif actual!=broken:raise ValueError("E39G12B4A_DENIED_BUT_SOURCE_MUTATED")
    checks={}
    if applied:
        for viewport in ("mobile","desktop"):
            evidence=worker.json_read(folder/(viewport+"-after.json"),6700)
            shot=worker.read(folder/(viewport+"-after.png"),2000000)
            if (evidence.get("evidenceClass")!="E39G12B4A_INDEPENDENT_REAL_CHROMIUM_MODEL_AFTER"
                or evidence.get("token")!=token
                or evidence.get("site")!=site or evidence.get("fault")!=fault
                or evidence.get("viewport")!=viewport
                or evidence.get("width")!=(390 if viewport=="mobile" else 1280)
                or evidence.get("sourceSha256")!=worker.sha(actual)
                or evidence.get("screenshotSha256")!=worker.sha(shot)
                or evidence.get("ownerPcUsed") is not False
                or evidence.get("productionPublished") is not False
                or evidence.get("realBrowserRuns")!=1
                or evidence.get("modelRepairClaimed") is not False
                or type(evidence.get("contracts")) is not dict
                or type(evidence["contracts"].get(fault)) is not bool
                or type(evidence.get("pageJavaScriptErrors")) is not list):
                raise ValueError("E39G12B4A_FAKE_BROWSER_EVIDENCE")
            if evidence["pageJavaScriptErrors"]:
                good=False
            else:
                good=evidence["contracts"][fault] is True and evidence.get("targetContractPassed") is True
            checks[viewport]={"passed":good,"screenshotSha256":evidence["screenshotSha256"]}
    elif any((folder/(v+"-after.json")).exists() or (folder/(v+"-after.png")).exists()
             for v in ("mobile","desktop")):
        raise ValueError("E39G12B4A_REJECTED_BUT_BROWSER_CREDIT_ATTEMPTED")
    success=applied and len(checks)==2 and all(c["passed"] for c in checks.values())
    result={"evidenceClass":"E39G12B4A_INDEPENDENT_MODEL_BROWSER_GRADE",
            "site":site,"fault":fault,"token":token,"modelTag":worker.MODEL,
            "modelCallsFromUntrustedReceipt":1,
            "policyOutcome":patch["policyOutcome"],"policyApplied":applied,
            "actualBrowserViewports":len(checks),
            "realBrowserVerifiedModelRepair":success,"browserOutcomes":checks,
            "fixtureRestoreCredited":False,"externallyCurated":False,
            "ownerApprovalGranted":False,"productionPublished":False,
            "stage45Go":False}
    dest=folder/"score.json"
    if dest.exists() or dest.is_symlink():raise ValueError("E39G12B4A_SCORE_REPLAY")
    with dest.open("x") as f:json.dump(result,f,sort_keys=True,indent=2);f.write("\n")
    print("E39G12B4A_REAL_MODEL_BROWSER_GRADE",token,
          "policy",patch["policyOutcome"],"realBothViewportsPassed",success)
    return result


def summarize()->dict:
    rows=[]
    for token in worker.FAULTS:
        row=worker.json_read(worker.ROOT/"repairs"/token/"score.json",6500)
        if (row.get("evidenceClass")!="E39G12B4A_INDEPENDENT_MODEL_BROWSER_GRADE"
            or row.get("token")!=token or row.get("modelTag")!=worker.MODEL
            or row.get("modelCallsFromUntrustedReceipt")!=1
            or row.get("fixtureRestoreCredited") is not False
            or row.get("ownerApprovalGranted") is not False):
            raise ValueError("E39G12B4A_INCOMPLETE_OR_FORGED_COHORT")
        rows.append(row)
    summary={"evidenceClass":"E39G12B4A_REAL_OLLAMA_TWO_SELF_AUTHORED_SITES",
             "modelTag":worker.MODEL,"realModelCompletionsClaimed":4,
             "verifiedInputSiteCount":2,"siteFaults":4,
             "policyAccepted":sum(r["policyApplied"] is True for r in rows),
             "realBrowserVerifiedRepairs":sum(r["realBrowserVerifiedModelRepair"] is True for r in rows),
             "realAfterBrowserViewports":sum(r["actualBrowserViewports"] for r in rows),
             "externallyCurated":False,"blindHeldOut":False,
             "independentModelIdentityAttested":False,
             "ownerApproved":False,"productionPublished":False,"stage45Go":False,
             "perFault":[{"token":r["token"],"accepted":r["policyApplied"],
                          "browserVerified":r["realBrowserVerifiedModelRepair"]} for r in rows]}
    dest=worker.ROOT/"summary.json"
    if dest.exists() or dest.is_symlink():raise ValueError("E39G12B4A_REPLAY_SUMMARY")
    with dest.open("x") as f:json.dump(summary,f,indent=2,sort_keys=True);f.write("\n")
    print("E39G12B4A_FINAL_REAL_LOCAL_CODER_BROWSER_RESULTS",json.dumps(summary,sort_keys=True))
    return summary


if __name__=="__main__":
    if len(sys.argv)==3 and sys.argv[1]=="score":grade(sys.argv[2])
    elif len(sys.argv)==2 and sys.argv[1]=="summarize":summarize()
    else:raise ValueError("E39G12B4A_ONLY_SCORE_OR_SUMMARIZE")
