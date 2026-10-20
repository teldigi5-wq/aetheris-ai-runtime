#!/usr/bin/env python3
"""E3.9G-9 network-NONE test worker only. Existing E33 categories, no new oracle.

Only this worker may alter the disposable target/e3-factory/site source.
A candidate must exactly restore ONE short, expected-safe declaration under
the existing E33 allowlist grammar; no open-ended diff/code execution.
The validator is not imported or exposed to the model adviser.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import re
import sys

import worker

BASE=worker.REPO/"target/e3-factory"
SITE=worker.TARGET
OUT=BASE/"e39g9"
CASES={
 "mobile-940":("mobile","styles.css","@media(max-width:760px){",
               "@media(max-width:760px){html{min-width:940px}"),
 "reduced-boolean":("reduced","main.js","const reduced = motion.matches;",
                    "const reduced = Boolean(false);"),
 "scroll-24":("scroll","main.js","const p = progress();","const p = 0.24;"),
}
MODEL={"granite4-3b":"granite4:3b","hermes3-3b":"hermes3:3b"}
ARMS=("baseline","skill-context")
TOKEN=re.compile(r"(granite4-3b|hermes3-3b)--(mobile-940|reduced-boolean|scroll-24)--(baseline|skill-context)\Z")

def sha(data: bytes)->str:
    return hashlib.sha256(data).hexdigest()

def read(path: Path,limit:int)->bytes:
    if path.is_symlink() or not path.is_file() or path.stat().st_size>limit:
        raise ValueError("E39G9_UNSAFE_OR_OVERSIZED_EVIDENCE")
    return path.read_bytes()

def read_json(path:Path,limit=7500)->dict:
    def no_dups(pairs):
        obj={}
        for k,v in pairs:
            if k in obj:raise ValueError("E39G9_DUPLICATE_UNTRUSTED_JSON_KEY")
            obj[k]=v
        return obj
    obj=json.loads(read(path,limit),object_pairs_hook=no_dups)
    if type(obj) is not dict:raise ValueError("E39G9_UNEXPECTED_JSON_TYPE")
    return obj

def sources(case:str):
    if case not in CASES:raise ValueError("E39G9_CASE_NOT_ALLOWLISTED")
    category,filename,clean,broken=CASES[case]
    original=read(worker.HERE/filename,25000)
    decoded=original.decode("utf-8")
    if decoded.count(clean)!=1 or broken in decoded:
        raise ValueError("E39G9_FROZEN_SOURCE_DRIFT")
    corrupted=decoded.replace(clean,broken,1).encode()
    target=SITE/filename
    if (SITE.is_symlink() or target.is_symlink() or not target.is_file()
        or SITE.resolve()!=BASE.resolve()/"site"):
        raise ValueError("E39G9_TARGET_ESCAPE_OR_SYMLINK")
    manifest=read_json(SITE/"manifest.json",5000)
    if manifest.get("generatedFilesSha256",{}).get(filename)!=sha(original):
        raise ValueError("E39G9_GENERATED_SITE_BASE_NOT_VERIFIED")
    return target,original,corrupted

def trial(token:str):
    m=TOKEN.fullmatch(token or "")
    if not m:raise ValueError("E39G9_MODEL_CASE_ARM_NOT_ALLOWLISTED")
    slug,case,arm=m.groups()
    folder=OUT/"models"/slug/case/arm
    for path in (OUT,OUT/"models",OUT/"models"/slug,OUT/"models"/slug/case,folder):
        if path.is_symlink():raise ValueError("E39G9_UNSAFE_MODEL_EVIDENCE_DIRECTORY")
    return slug,case,arm,folder

def case_folder(case:str):
    if case not in CASES: raise ValueError("E39G9_UNREVIEWED_SEED_CASE")
    folder=OUT/"cases"/case
    for path in (OUT,OUT/"cases",folder):
        if path.is_symlink():raise ValueError("E39G9_EVIDENCE_DIRECTORY_SYMLINK")
    return folder

def before(case:str):
    target,original,corrupted=sources(case)
    seed=read_json(case_folder(case)/"seed.json",2500)
    if (seed.get("evidenceClass")!="E39G9_PREEXISTING_E33_SYNTHETIC_FAULT_SEED"
        or seed.get("caseId")!=case or seed.get("beforeSha256")!=sha(corrupted)
        or seed.get("sourceOriginalSha256")!=sha(original)
        or read(target,25000)!=corrupted):
        raise ValueError("E39G9_UNVERIFIED_BROKEN_SITE")
    reports={}
    for viewport in ("mobile","desktop"):
        p=case_folder(case)/(viewport+"-before.json")
        o=read_json(p,6000)
        picture=read(case_folder(case)/(viewport+"-before.png"),2000000)
        expected_pass=case=="mobile-940" and viewport=="desktop"
        if (o.get("evidenceClass")!="E39G9_INDEPENDENT_REAL_CHROMIUM_FUNCTIONAL"
            or o.get("caseId")!=case or o.get("viewport")!=viewport
            or o.get("phase")!="before" or o.get("passed") is not expected_pass
            or o.get("pageJavaScriptErrors")!=[]
            or o.get("sourceSha256")!=sha(corrupted)
            or o.get("screenshotSha256")!=sha(picture)):
            raise ValueError("E39G9_REAL_BROWSER_BEFORE_NOT_VERIFIED")
        reports[viewport]=sha(read(p,6000))
    return target,original,corrupted,reports

def seed(case:str):
    target,original,corrupted=sources(case)
    folder=case_folder(case)
    if folder.exists() or folder.is_symlink() or read(target,25000)!=original:
        raise ValueError("E39G9_SEED_REQUIRES_NEW_CANONICAL_SITE")
    folder.mkdir(parents=True,mode=0o700)
    target.write_bytes(corrupted)
    if read(target,25000)!=corrupted:raise ValueError("E39G9_FAULT_NOT_WRITTEN")
    with (folder/"seed.json").open("x") as f:
        json.dump({"evidenceClass":"E39G9_PREEXISTING_E33_SYNTHETIC_FAULT_SEED",
                   "caseId":case,"beforeSha256":sha(corrupted),
                   "sourceOriginalSha256":sha(original),
                   "preExistingE33Fixture":True,"modelCalls":0,
                   "ownerApprovalGranted":False},f,sort_keys=True);f.write("\n")
    print("E39G9_EXISTING_SYNTHETIC_SITE_FAULT_SEEDED",case)

def strict_reply(raw:str)->dict:
    if type(raw) is not str or len(raw.encode())>1100:
        raise ValueError("E39G9_MODEL_OUTPUT_BOUND")
    obj=json.loads(raw,object_pairs_hook=lambda pairs: unique(pairs))
    if obj=={"operation":"abstain"}:return obj
    if (type(obj) is not dict or set(obj)!={"operation","old","new"}
        or obj.get("operation")!="replace" or type(obj.get("old")) is not str
        or type(obj.get("new")) is not str
        or not 1<=len(obj["old"])<=180 or len(obj["new"])>180
        or any(ord(ch)<32 for ch in obj["old"]+obj["new"])):
        raise ValueError("E39G9_UNSAFE_REPLY_SCHEMA")
    return obj

def unique(pairs):
    v={}
    for k,item in pairs:
        if k in v:raise ValueError("E39G9_DUPLICATE_UNTRUSTED_JSON_KEY")
        v[k]=item
    return v

def permitted(case:str,proposal:dict,corrupted:bytes,original:bytes)->bytes:
    """Independently enforced original E33 one-literal grammar + final clean source."""
    kind=CASES[case][0]
    old,new=proposal["old"],proposal["new"]
    text=corrupted.decode()
    if text.count(old)!=1 or len(old)>180 or len(new)>180:
        raise ValueError("E39G9_NOT_SINGLE_LITERAL")
    if kind=="mobile":
        match=re.fullmatch(r"html\{min-width:(\d{3,4})px\}",old)
        allowed=match is not None and 760<int(match.group(1))<=2000 and new==""
    elif kind=="reduced":
        allowed=bool(re.fullmatch(r"const reduced = (?:false|Boolean\(false\));",old)
            and new=="const reduced = motion.matches;"
            and "const motion = window.matchMedia(" in text)
    else:
        allowed=bool(re.fullmatch(r"const p = (?:0(?:\.\d+)?|1(?:\.0+)?);",old)
            and new=="const p = progress();" and "function progress()" in text)
    if not allowed:raise ValueError("E39G9_E33_REVIEWED_REPAIR_GRAMMAR_DENIED")
    result=text.replace(old,new,1).encode()
    if result!=original:raise ValueError("E39G9_NOT_EXACT_CANONICAL_SINGLE_DECLARATION")
    return result

def model_evidence(token:str,corrupted:bytes,reports:dict):
    slug,case,arm,folder=trial(token)
    path=case_folder(case)/("e39g9-"+slug+"-"+arm+".json")
    r=read_json(path,8500)
    raw=r.get("rawModelResponse")
    if (r.get("evidenceClass")!="E39G9_GENUINE_LOCAL_OLLAMA_MULTI_DOMAIN_ADVICE"
        or r.get("caseId")!=case or r.get("modelTag")!=MODEL[slug]
        or r.get("arm")!=arm or r.get("realModelCalls")!=1
        or r.get("modelActuallyCompleted") is not True
        or r.get("sourceSha256")!=sha(corrupted)
        or r.get("browserBeforeSha256")!=reports
        or r.get("ownerApproved") is not False
        or r.get("modelOutputExecuted") is not False
        or r.get("productionPublished") is not False
        or type(raw) is not str or r.get("rawResponseSha256")!=sha(raw.encode())):
        raise ValueError("E39G9_FORGED_OLLAMA_OR_REAL_BROWSER_SOURCE")
    return r

def apply(token:str):
    slug,case,arm,folder=trial(token)
    target,original,corrupted,reports=before(case)
    if folder.exists() or folder.is_symlink():
        raise ValueError("E39G9_ONE_TIME_ARM_ALREADY_ATTEMPTED")
    r=model_evidence(token,corrupted,reports)
    applied=False
    outcome="INVALID"
    reason="E39G9_UNPARSABLE"
    changed=corrupted
    try:
        candidate=strict_reply(r["rawModelResponse"])
    except ValueError as ex:
        reason=str(ex)
    else:
        if candidate=={"operation":"abstain"}:
            outcome="ABSTAIN";reason="E39G9_MODEL_ABSTAIN"
        else:
            try:changed=permitted(case,candidate,corrupted,original)
            except ValueError as ex:outcome="POLICY_REJECTED";reason=str(ex)
            else:outcome="POLICY_APPLIED";reason="E39G9_E33_GRAMMAR_AND_SOURCE_ACCEPTED";applied=True
    folder.mkdir(parents=True,mode=0o700)
    if applied:
        target.write_bytes(changed)
    if read(target,25000)!=changed:
        raise ValueError("E39G9_WORKER_OUTPUT_HASH_MISMATCH")
    result={"evidenceClass":"E39G9_REAL_MODEL_PROPOSAL_NETWORK_NONE_POLICY_RECEIPT",
        "modelTag":MODEL[slug],"caseId":case,"arm":arm,
        "modelRawSha256":r["rawResponseSha256"],"browserBeforeSha256":reports,
        "beforeSha256":sha(corrupted),"afterSha256":sha(changed),
        "policyOutcome":outcome,"policyReason":reason,
        "sandboxPatchApplied":applied,"modelAfterChromiumVerified":False,
        "realModelCalls":1,"ownerApproved":False,"productionPublished":False}
    with (folder/"patch.json").open("x") as f:
        json.dump(result,f,indent=2,sort_keys=True);f.write("\n")
    print("E39G9_NETWORK_NONE_REAL_MODEL_POLICY",case,arm,outcome)

def reset(token:str):
    slug,case,arm,folder=trial(token)
    target,original,corrupted,reports=before_source_only(case)
    receipt=read_json(folder/"patch.json",4500)
    score=read_json(folder/"score.json",6500)
    if (receipt.get("evidenceClass")!="E39G9_REAL_MODEL_PROPOSAL_NETWORK_NONE_POLICY_RECEIPT"
        or score.get("evidenceClass")!="E39G9_REAL_CHROMIUM_SCORED_MODEL_PATCH"
        or score.get("caseId")!=case or score.get("arm")!=arm
        or score.get("modelTag")!=MODEL[slug]
        or receipt.get("afterSha256")!=sha(read(target,25000))
        or (folder/"reset.json").exists()):
        raise ValueError("E39G9_RESET_REQUIRES_MATCHING_SCORED_MODEL_PATCH")
    target.write_bytes(corrupted)
    if read(target,25000)!=corrupted:raise ValueError("E39G9_TEST_RESET_FAILED")
    with (folder/"reset.json").open("x") as f:
        json.dump({"evidenceClass":"E39G9_ZERO_MODEL_CREDIT_RESEED_DEFECT",
                   "modelRepair":False,"sourceSha256":sha(corrupted)},f);f.write("\n")
    print("E39G9_TEST_RESET_NO_MODEL_REPAIR_CREDIT",case,arm)

def before_source_only(case:str):
    target,original,corrupted=sources(case)
    seed=read_json(case_folder(case)/"seed.json",2500)
    if seed.get("beforeSha256")!=sha(corrupted):
        raise ValueError("E39G9_BAD_SEED_FOR_RESET")
    return target,original,corrupted,{}

def finish(case:str):
    target,original,corrupted,_=before_source_only(case)
    if read(target,25000)!=corrupted:raise ValueError("E39G9_FINISH_REQUIRES_BROKEN_SITE")
    for slug in MODEL: # only this job's current model has files; avoid other model check.
        pass
    # Each per-model job runs its own isolated source and has exactly two arm receipts.
    # The requested model prefix is independently determined via CLI in finish token.
    raise ValueError("E39G9_FINISH_REQUIRES_MODEL_TOKEN")

def done(token:str):
    slug,case,arm,folder=trial(token)
    if arm!="baseline":raise ValueError("E39G9_FINISH_BASELINE_TOKEN_REQUIRED")
    target,original,corrupted,_=before_source_only(case)
    if read(target,25000)!=corrupted:
        raise ValueError("E39G9_NON_MODEL_FINAL_RESTORE_UNSAFE")
    for a in ARMS:
        where=OUT/"models"/slug/case/a
        data=read_json(where/"score.json",6500)
        reset=read_json(where/"reset.json",1500)
        if (data.get("evidenceClass")!="E39G9_REAL_CHROMIUM_SCORED_MODEL_PATCH"
            or data.get("arm")!=a or data.get("caseId")!=case
            or data.get("modelTag")!=MODEL[slug]
            or reset.get("evidenceClass")!="E39G9_ZERO_MODEL_CREDIT_RESEED_DEFECT"
            or reset.get("modelRepair") is not False):
            raise ValueError("E39G9_BOTH_ARMS_NOT_SCORED_AND_RESET")
    target.write_bytes(original)
    if read(target,25000)!=original:raise ValueError("E39G9_FINAL_FIXTURE_RESET_FAILED")
    with (case_folder(case)/("finished-"+slug+".json")).open("x") as f:
        json.dump({"evidenceClass":"E39G9_DETERMINISTIC_FINAL_RESET_NOT_AI_REPAIR",
                   "modelTag":MODEL[slug],"caseId":case,"modelRepair":False,
                   "sourceSha256":sha(original),"ownerApprovalGranted":False},f);f.write("\n")
    print("E39G9_FINISH_NON_MODEL_FIXTURE_RESET",case)

if __name__=="__main__":
    args=sys.argv[1:]
    if len(args)!=2:raise ValueError("E39G9_FIXED_ACTION_AND_TOKEN_REQUIRED")
    action,item=args
    if action=="seed":seed(item)
    elif action=="apply":apply(item)
    elif action=="reset":reset(item)
    elif action=="done":done(item)
    else:raise ValueError("E39G9_UNKNOWN_WORKER_ACTION")
