#!/usr/bin/env python3
"""E3.9G-12B4A: evaluator-only, network-none patcher. Never import model-side code.

Receipts are UNTRUSTED: their hashes are consistency evidence, NOT signatures,
owner proof or independently attested AI provenance. Only frozen B2 policy
may edit one disposable per-fault HTML copy. No live-site write or tool use.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import sys

import e39g12b_two_site_corpus as corpus
import e39g12b2_typed_policy as frozen

ROOT=corpus.BASE.parent/"e39g12b4a"
MODEL="qwen2.5-coder:7b"
MODEL_PREFIX="dae161e27b0e"
FAULTS={
 "studynest--keyboard-focus-visibility":("studynest","keyboard-focus-visibility"),
 "studynest--responsive-mobile-overflow":("studynest","responsive-mobile-overflow"),
 "cityline--reduced-motion":("cityline","reduced-motion"),
 "cityline--safe-untrusted-text":("cityline","safe-untrusted-text"),
}


def sha(raw:bytes)->str:return hashlib.sha256(raw).hexdigest()


def read(path:Path,limit:int)->bytes:
    if path.is_symlink() or not path.is_file() or not 0<path.stat().st_size<=limit:
        raise ValueError("E39G12B4A_UNSAFE_OR_MISSING_ARTIFACT")
    return path.read_bytes()


def json_read(path:Path,limit:int)->dict:
    def unique(pairs):
        result={}
        for k,v in pairs:
            if k in result:raise ValueError("E39G12B4A_DUPLICATE_RECEIPT_KEY")
            result[k]=v
        return result
    obj=json.loads(read(path,limit),object_pairs_hook=unique)
    if type(obj) is not dict:raise ValueError("E39G12B4A_RECEIPT_NOT_OBJECT")
    return obj


def paths(token:str):
    if token not in FAULTS:raise ValueError("E39G12B4A_UNREVIEWED_SITE_FAULT_TOKEN")
    site,fault=FAULTS[token]
    folder=ROOT/"repairs"/token
    for p in (ROOT,ROOT/"repairs",folder):
        if p.is_symlink():raise ValueError("E39G12B4A_SYMLINKED_OUTPUT")
    return site,fault,folder


def inspect(token:str)->tuple[dict,bytes,dict]:
    site,fault,folder=paths(token)
    broken=read(corpus.BASE/"sites"/site/"broken"/"index.html",14000)
    source=read(corpus.HERE/site/"index.html",14000)
    if broken!=source:raise ValueError("E39G12B4A_BROKEN_SOURCE_DRIFT")
    manifest=json_read(corpus.BASE/"corpus-manifest.json",8000)
    if (manifest.get("evidenceClass")!="E39G12B_TWO_DISTINCT_PUBLIC_SYNTHETIC_WEBSITES"
        or manifest["sites"][site]["variants"]["broken"]["sourceSha256"]!=sha(broken)):
        raise ValueError("E39G12B4A_UNVERIFIED_EVALUATOR_BASELINE")
    outer=json_read(ROOT/"incoming"/"proposals.json",22000)
    if (outer.get("evidenceClass")!="E39G12B4A_FOUR_REAL_LOCAL_OLLAMA_COMPLETIONS"
        or outer.get("modelTag")!=MODEL
        or outer.get("realModelCalls")!=4
        or outer.get("modelTextExecuted") is not False
        or outer.get("modelSourceEdited") is not False
        or outer.get("ownerApprovalGranted") is not False
        or outer.get("productionPublished") is not False
        or outer.get("goldenReferenceAvailable") is not False
        or set(outer.get("trials",{}))!=set(FAULTS)
        or type(outer.get("ollamaSelfReportedDigest")) is not str
        or not outer["ollamaSelfReportedDigest"].removeprefix("sha256:").startswith(MODEL_PREFIX)
        or type(outer.get("sizeBytes")) is not int
        or not 4_000_000_000<=outer["sizeBytes"]<=5_300_000_000):
        raise ValueError("E39G12B4A_MODEL_RECEIPT_UNBOUND_OR_UNVERIFIED")
    entry=outer["trials"][token]
    raw=entry.get("rawModelResponse")
    if (type(raw) is not str or len(raw.encode())>1800
        or entry.get("modelTag")!=MODEL or entry.get("site")!=site
        or entry.get("fault")!=fault
        or entry.get("sourceSha256")!=sha(broken)
        or entry.get("rawResponseSha256")!=sha(raw.encode())
        or entry.get("realModelCalls")!=1
        or type(entry.get("generatedTokens")) is not int
        or entry["generatedTokens"]<=0
        or type(entry.get("promptSha256")) is not str
        or len(entry["promptSha256"])!=64):
        raise ValueError("E39G12B4A_UNBOUND_INDIVIDUAL_REAL_MODEL_REPLY")
    return entry,broken,outer


def apply(token:str):
    site,fault,folder=paths(token)
    if folder.exists() or folder.is_symlink():raise ValueError("E39G12B4A_REPLAY_DENIED")
    entry,broken,outer=inspect(token)
    classification="INVALID"
    reason="E39G12B4A_INVALID_MODEL_RESPONSE"
    amended=broken
    raw=entry["rawModelResponse"]
    try:
        data=frozen.decode_json(raw)
        if data["site"]!=site or data["fault"]!=fault:
            raise ValueError("E39G12B4A_CROSS_FAULT_PROPOSAL_DENIED")
        amended=frozen.validate(data,broken)
    except (ValueError,KeyError,TypeError,json.JSONDecodeError) as exc:
        reason=str(exc)[:150]
        classification="POLICY_REJECTED"
    else:
        classification="POLICY_APPLIED"
        reason="E39G12B4A_FROZEN_TYPED_SCOPE_ACCEPTED"
    folder.mkdir(parents=True,mode=0o700)
    dest=folder/"index.html"
    if dest.is_symlink() or dest.exists():raise ValueError("E39G12B4A_OUTPUT_ALREADY_EXISTS")
    with dest.open("xb") as f:f.write(amended)
    if read(dest,14000)!=amended:raise ValueError("E39G12B4A_SITE_WRITE_DRIFT")
    receipt={
        "evidenceClass":"E39G12B4A_NETWORK_NONE_TYPED_SITE_PATCH",
        "site":site,"fault":fault,"modelTag":MODEL,
        "modelRawSha256":entry["rawResponseSha256"],
        "sourceBeforeSha256":sha(broken),"sourceAfterSha256":sha(amended),
        "policyOutcome":classification,"reason":reason,
        "patchApplied":classification=="POLICY_APPLIED",
        "modelAfterBrowserVerified":False,
        "realModelCallsFromUntrustedReceipt":1,
        "isolatedNetworkNoneWorker":True,
        "goldenSourceUsedForPolicyValidation":False,
        "ownerApprovalGranted":False,"productionPublished":False,"stage45Go":False
    }
    with (folder/"patch.json").open("x") as f:
        json.dump(receipt,f,sort_keys=True,indent=2);f.write("\n")
    print("E39G12B4A_NETWORK_NONE_POLICY_APPLIED_OR_DENIED",token,classification)


if __name__=="__main__":
    if len(sys.argv)!=3 or sys.argv[1]!="apply":
        raise ValueError("E39G12B4A_FIXED_APPLY_ONLY")
    apply(sys.argv[2])
