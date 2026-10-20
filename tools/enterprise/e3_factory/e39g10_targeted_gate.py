#!/usr/bin/env python3
"""E3.9G-10 network-none only: reuses UNCHANGED E3.9G-9 exact literal gate.
No executable model text, shell, HTTP or arbitrary source/file paths here.
"""
from __future__ import annotations
import json
from pathlib import Path
import sys

import e39g9_multidomain_worker as frozen
import e39g10_targeted_adviser as advisor

OUT=frozen.OUT.parent/"e39g10"
ARMS=advisor.ARMS

def trial(token):
    parts=token.split("--")
    if len(parts)!=3 or parts[0] not in frozen.MODEL or parts[1] not in frozen.CASES or parts[2] not in ARMS:
        raise ValueError("E39G10_EXACT_MODEL_CASE_ARM_ONLY")
    slug,case,arm=parts
    dest=OUT/"models"/slug/case/arm
    for p in (OUT,OUT/"models",OUT/"models"/slug,OUT/"models"/slug/case,dest):
        if p.is_symlink():raise ValueError("E39G10_MODEL_OUTPUT_SYMLINK")
    return slug,case,arm,dest

def model_record(slug,case,arm,source,before):
    filepath=OUT/"cases"/case/("e39g10-"+slug+"-"+arm+".json")
    data=frozen.read_json(filepath,9500)
    raw=data.get("rawModelResponse")
    if (data.get("evidenceClass")!="E39G10_GENUINE_LOCAL_MODEL_TARGETED_VS_ORIGINAL"
        or data.get("caseId")!=case or data.get("modelTag")!=frozen.MODEL[slug]
        or data.get("arm")!=arm or data.get("realModelCalls")!=1
        or data.get("actualModelCompletion") is not True
        or data.get("sourceSha256")!=frozen.sha(source)
        or data.get("browserBeforeSha256")!=before
        or type(raw) is not str
        or data.get("rawResponseSha256")!=frozen.sha(raw.encode())
        or data.get("ownerApproved") is not False
        or data.get("productionPublished") is not False
        or data.get("modelOutputExecuted") is not False
        or len(data.get("skillReferences",[]))!=4):
        raise ValueError("E39G10_UNVERIFIED_ACTUAL_OLLAMA_RECEIPT")
    return data

def apply(token):
    slug,case,arm,folder=trial(token)
    path,original,broken,before=frozen.before(case)
    if folder.exists():raise ValueError("E39G10_REPLAY_OUTPUT")
    rec=model_record(slug,case,arm,broken,before)
    accepted=False;response="INVALID";reason="E39G10_INVALID_JSON"
    changed=broken
    try:
        item=frozen.strict_reply(rec["rawModelResponse"])
    except (ValueError,TypeError,json.JSONDecodeError) as exc:
        reason=type(exc).__name__
    else:
        if item=={"operation":"abstain"}:
            response="ABSTAIN";reason="E39G10_MODEL_ABSTAIN"
        else:
            try:changed=frozen.permitted(case,item,broken,original)
            except (ValueError,TypeError,KeyError) as exc:
                response="POLICY_REJECTED";reason=str(exc)
            else:
                response="POLICY_APPLIED";reason="E39G10_SAME_FROZEN_E33_GRAMMAR_ACCEPTED"
                accepted=True
    folder.mkdir(parents=True,mode=0o700)
    if accepted:path.write_bytes(changed)
    if frozen.read(path,25000)!=changed:raise ValueError("E39G10_SITE_WAS_CHANGED_OUTSIDE_POLICY")
    report={"evidenceClass":"E39G10_NETWORK_NONE_MODEL_SOURCE_POLICY",
        "modelTag":frozen.MODEL[slug],"caseId":case,"arm":arm,
        "rawResponseSha256":rec["rawResponseSha256"],
        "beforeBrowserSha256":before,
        "beforeSourceSha256":frozen.sha(broken),"afterSourceSha256":frozen.sha(changed),
        "policyOutcome":response,"policyReason":reason,"patchApplied":accepted,
        "realModelCalls":1,"ownerApproved":False,"productionPublished":False,
        "stage45Go":False,"browserRepairClaimed":False}
    with (folder/"patch.json").open("x") as f:
        json.dump(report,f,indent=2,sort_keys=True);f.write("\n")
    print("E39G10_UNCHANGED_POLICY_MODEL_PROPOSAL",case,slug,arm,response)

def reset(token):
    slug,case,arm,folder=trial(token)
    path,original,broken,_=frozen.before_source_only(case)
    patch=frozen.read_json(folder/"patch.json",5000)
    score=frozen.read_json(folder/"score.json",7000)
    if (patch.get("evidenceClass")!="E39G10_NETWORK_NONE_MODEL_SOURCE_POLICY"
        or patch.get("caseId")!=case or patch.get("modelTag")!=frozen.MODEL[slug]
        or patch.get("arm")!=arm
        or score.get("evidenceClass")!="E39G10_REAL_CHROMIUM_INDEPENDENT_MODEL_GRADE"
        or score.get("caseId")!=case or score.get("modelTag")!=frozen.MODEL[slug]
        or score.get("arm")!=arm
        or patch.get("afterSourceSha256")!=frozen.sha(frozen.read(path,25000))):
        raise ValueError("E39G10_NOT_SCORED_OR_SITE_DRIFT")
    output=folder/"reset.json"
    if output.exists() or output.is_symlink():raise ValueError("E39G10_ALREADY_RESET")
    path.write_bytes(broken)
    output.write_text(json.dumps({"evidenceClass":"E39G10_NONMODEL_RESET_NEVER_CREDIT",
        "caseId":case,"modelGeneratedRepair":False,"ownerApproved":False,
        "sourceSha256":frozen.sha(broken)})+"\n")
    print("E39G10_RESET_TEST_ONLY_NO_AI_CREDIT",token)

def done(token):
    slug,case,arm,folder=trial(token)
    if arm!="original":raise ValueError("E39G10_EXPECT_ORIGINAL_TOKEN")
    path,original,broken,_=frozen.before_source_only(case)
    if frozen.read(path,25000)!=broken:raise ValueError("E39G10_FINISH_NOT_DEFECTIVE")
    for a in ARMS:
        dest=OUT/"models"/slug/case/a
        data=frozen.read_json(dest/"score.json",7000)
        reset_data=frozen.read_json(dest/"reset.json",2000)
        if (data.get("evidenceClass")!="E39G10_REAL_CHROMIUM_INDEPENDENT_MODEL_GRADE"
            or data.get("caseId")!=case or data.get("arm")!=a
            or reset_data.get("evidenceClass")!="E39G10_NONMODEL_RESET_NEVER_CREDIT"
            or reset_data.get("modelGeneratedRepair") is not False):
            raise ValueError("E39G10_BOTH_MODEL_ARMS_NOT_SCORABLE")
    path.write_bytes(original)
    if frozen.read(path,25000)!=original:raise ValueError("E39G10_CANONICAL_RESET_FAILED")
    dest=OUT/"cases"/case/("finished-"+slug+".json")
    with dest.open("x") as f:
        json.dump({"evidenceClass":"E39G10_FIXTURE_RECOVERY_NONMODEL",
            "caseId":case,"modelTag":frozen.MODEL[slug],
            "modelRepair":False,"ownerApproved":False},f)
    print("E39G10_CANONICAL_NONMODEL_RESET",case)

if __name__=="__main__":
    if len(sys.argv)!=3 or sys.argv[1] not in ("apply","reset","done"):
        raise ValueError("E39G10_EXACT_ACTION_TOKEN_REQUIRED")
    {"apply":apply,"reset":reset,"done":done}[sys.argv[1]](sys.argv[2])
