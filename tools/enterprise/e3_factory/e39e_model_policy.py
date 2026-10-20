#!/usr/bin/env python3
"""E3.9E model-neutral, network-none CSS gate and per-case browser scorer.

Only this worker may change a generated site file. It binds source, immutable
canonical manifest, and TWO actual failing Chromium browser reports; supports
one reviewed declaration under .skip-link:focus. Wrong alternatives are denied.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import re
import sys

import e39d_heldout_focus_evaluator as e

MODEL_TAGS = frozenset(("hermes3:3b","granite4:3b"))
RESPONSE_KEYS = frozenset(("operation","selector","property","value"))
FOCUS = ".skip-link:focus"
RULE = re.compile(r"\.skip-link:focus\{[^{}]{1,160}\}\Z",re.ASCII)
BEFORE = re.compile(r"\.skip-link:focus\{([^{}]{1,160})\}",re.ASCII)
PX = re.compile(r"(0|[1-9]|1[0-9]|2[0-4])px\Z",re.ASCII)


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def load(path: Path,limit:int=4600):
    return json.loads(e.checked(path,limit))


def unique(pairs):
    result = {}
    for k,v in pairs:
        if k in result:
            raise ValueError("E39E_DUPLICATE_MODEL_JSON_KEY")
        result[k]=v
    return result


def parse_response(raw: str):
    if not isinstance(raw,str) or len(raw.encode())>1200:
        raise ValueError("E39E_MODEL_TEXT_BUDGET")
    try:
        obj=json.loads(raw,object_pairs_hook=unique,
                       parse_constant=lambda _: (_ for _ in ()).throw(
                           ValueError("E39E_INVALID_NONFINITE")))
    except json.JSONDecodeError as err:
        raise ValueError("E39E_INVALID_MODEL_JSON") from err
    if (not isinstance(obj,dict) or set(obj)!=RESPONSE_KEYS
            or obj.get("operation")!="replace"
            or obj.get("selector")!=FOCUS
            or obj.get("property") not in
               ("top","left","opacity","visibility","transform")
            or type(obj.get("value")) is not str or len(obj["value"])>28):
        raise ValueError("E39E_MODEL_SELECTOR_OR_FIELDS_DENIED")
    return obj


def snapshot(case: str):
    if case not in e.FAULTS:
        raise ValueError("E39E_UNKNOWN_CASE")
    e.verify_before(case)
    canonical=e.clean_source()
    source=e.checked(e.STYLE,25000)
    if source!=e.defective_source(case):
        raise ValueError("E39E_NOT_EXACT_SINGLE_SEEDED_FAULT")
    manifest=load(e.SITE/"manifest.json")
    if manifest.get("generatedFilesSha256",{}).get("styles.css")!=sha(canonical):
        raise ValueError("E39E_CANONICAL_MANIFEST_MISMATCH")
    return source,canonical


def source_property(source:bytes,canonical:bytes):
    text=source.decode("utf-8")
    canon=canonical.decode("utf-8")
    found=BEFORE.findall(text)
    if len(found)!=1 or canon.count(e.ORIGINAL)!=1:
        raise ValueError("E39E_FOCUS_RULE_NOT_UNIQUE")
    rule=FOCUS+"{"+found[0]+"}"
    if text.replace(rule,e.ORIGINAL,1)!=canon:
        raise ValueError("E39E_UNRELATED_CSS_SOURCE_CHANGE")
    declaration=found[0]
    if re.fullmatch(r"top:-[1-9][0-9]{0,3}px",declaration):
        prop="top"
    elif re.fullmatch(r"top:12px;left:-[1-9][0-9]{0,3}px",declaration):
        prop="left"
    elif declaration=="top:12px;opacity:0":
        prop="opacity"
    elif declaration=="top:12px;visibility:hidden":
        prop="visibility"
    elif re.fullmatch(r"top:12px;transform:translate[XY]\(-[1-9][0-9]{0,3}px\)",declaration):
        prop="transform"
    else:
        raise ValueError("E39E_SOURCE_CSS_NOT_REVIEWED")
    return rule,prop


def validate_candidate(candidate:dict, source:bytes, canonical:bytes) -> bytes:
    old_rule,prop=source_property(source,canonical)
    if candidate.get("property")!=prop:
        raise ValueError("E39E_WRONG_DECLARATION_FOR_BROWSER_DEFECT")
    value=candidate["value"]
    if prop in ("top","left"):
        if PX.fullmatch(value) is None:
            raise ValueError("E39E_CSS_PIXEL_VALUE_OUT_OF_BOUND")
    elif prop=="opacity":
        if value not in ("1","0.95"):
            raise ValueError("E39E_FOCUS_OPACITY_NOT_VISIBLE")
    elif prop=="visibility":
        if value!="visible":
            raise ValueError("E39E_FOCUS_VISIBILITY_INVALID")
    elif prop=="transform":
        if value!="none":
            raise ValueError("E39E_FOCUS_TRANSFORM_INVALID")
    if prop=="top":
        new_rule=FOCUS+"{top:"+value+"}"
    else:
        new_rule=FOCUS+"{top:12px;"+prop+":"+value+"}"
    if RULE.fullmatch(new_rule) is None:
        raise ValueError("E39E_MULTIRULE_OR_UNTRUSTED_CSS")
    # Strong bytewise scope: exactly one focus rule differs; no other source edits.
    result=source.decode().replace(old_rule,new_rule,1).encode()
    control=canonical.decode().replace(e.ORIGINAL,new_rule,1).encode()
    if result!=control or result==source:
        raise ValueError("E39E_UNREVIEWED_WHOLE_FILE_RESULT")
    return result


def model_evidence(case:str,source:bytes):
    folder=e.location(case)
    item=load(folder/"model-response.json")
    expected=load(folder/"seed.json")
    if (item.get("evidenceClass")!="E39E_GENUINE_EIGHT_CASE_MODEL_REPLY"
            or item.get("caseId")!=case
            or item.get("model",{}).get("tag") not in MODEL_TAGS
            or item.get("realModelCalls")!=1
            or item.get("sourceSha256")!=sha(source)
            or item.get("responseClass")!="SELECTOR_PROPOSAL"
            or item.get("ownerApproved") is not False
            or item.get("productionPublished") is not False
            or item.get("modelTextExecutedDirectly") is not False
            or item.get("oracleExpectedPatchLoaded") is not False
            or item.get("promptVersion")!="E39F_TWO_VIEWPORT_TYPED_SOURCE_NONORACLE_V2"
            or expected.get("beforeSourceSha256")!=sha(source)):
        raise ValueError("E39E_MODEL_PROVENANCE_OR_AUTHORITY_DENIED")
    for viewport in ("mobile","desktop"):
        if item.get("browserBeforeSha256",{}).get(viewport)!=sha(
                e.checked(folder/f"{viewport}-before.json",4096)):
            raise ValueError("E39E_BROWSER_EVIDENCE_HASH_INVALID")
    raw=item.get("rawResponse")
    if not isinstance(raw,str) or item.get("rawResponseSha256")!=sha(raw.encode()):
        raise ValueError("E39E_RAW_MODEL_RESPONSE_HASH_INVALID")
    return parse_response(raw),item


def apply(case:str):
    source,canonical=snapshot(case)
    candidate,item=model_evidence(case,source)
    result=validate_candidate(candidate,source,canonical)
    receipt_file=e.location(case)/"receipt.json"
    if receipt_file.exists() or receipt_file.is_symlink():
        raise ValueError("E39E_MODEL_PATCH_RECEIPT_ALREADY_EXISTS")
    e.STYLE.write_bytes(result)
    if e.checked(e.STYLE,25000)!=result:
        raise ValueError("E39E_WRITTEN_SOURCE_MISMATCH")
    report={"evidenceClass":"E39E_MODEL_TEXT_POLICY_ACCEPTED_BROWSER_PENDING",
            "caseId":case,"modelTag":item["model"]["tag"],
            "beforeSha256":sha(source),"afterSha256":sha(result),
            "modelResponseSha256":item["rawResponseSha256"],
            "selector":FOCUS,"property":candidate["property"],
            "modelTextDirectlyExecuted":False,
            "ownerApproved":False,"productionPublished":False}
    with receipt_file.open("x") as fp:
        json.dump(report,fp,sort_keys=True,indent=2);fp.write("\n")
    print("E39E_MODEL_PATCH_ACCEPTED_BROWSER_PENDING",case,candidate["property"])


def score(case:str,status:str):
    if case not in e.FAULTS or status not in (
            "POLICY_ACCEPTED","POLICY_REJECTED","ABSTAIN","INVALID"):
        raise ValueError("E39E_INVALID_SCORING_ARGUMENTS")
    folder=e.location(case)
    item=load(folder/"model-response.json")
    seed=load(folder/"seed.json")
    if (item.get("evidenceClass")!="E39E_GENUINE_EIGHT_CASE_MODEL_REPLY"
            or item.get("caseId")!=case or item.get("realModelCalls")!=1
            or item.get("sourceSha256")!=seed.get("beforeSourceSha256")
            or item.get("model",{}).get("tag") not in MODEL_TAGS
            or item.get("modelTextExecutedDirectly") is not False
            or item.get("ownerApproved") is not False
            or item.get("productionPublished") is not False):
        raise ValueError("E39E_INVALID_SCORE_PROVENANCE")
    kind=item.get("responseClass")
    if (status=="ABSTAIN" and kind!="ABSTAIN"
            or status=="INVALID" and kind!="INVALID_MODEL_RESPONSE"
            or status in ("POLICY_ACCEPTED","POLICY_REJECTED")
               and kind!="SELECTOR_PROPOSAL"):
        raise ValueError("E39E_STATUS_DOES_NOT_MATCH_MODEL_RESPONSE")
    source=e.checked(e.STYLE,25000)
    passed=False
    details={}
    if status=="POLICY_ACCEPTED":
        receipt=load(folder/"receipt.json")
        if (receipt.get("evidenceClass")!="E39E_MODEL_TEXT_POLICY_ACCEPTED_BROWSER_PENDING"
                or receipt.get("caseId")!=case
                or receipt.get("modelTag")!=item["model"]["tag"]
                or receipt.get("modelResponseSha256")!=item["rawResponseSha256"]
                or receipt.get("afterSha256")!=sha(source)
                or receipt.get("modelTextDirectlyExecuted") is not False
                or receipt.get("ownerApproved") is not False):
            raise ValueError("E39E_POLICY_RECEIPT_PROVENANCE_INVALID")
        # Independently recreate and validate the proposed CSS; don't trust receipt alone.
        canonical=e.clean_source()
        parsed=parse_response(item["rawResponse"])
        if source!=validate_candidate(parsed,e.defective_source(case),canonical):
            raise ValueError("E39E_ACCEPTED_OUTPUT_MISMATCH")
        for viewport in ("mobile","desktop"):
            details[viewport]=load(folder/f"{viewport}-after.json")
        passed=all(
            v.get("evidenceClass")=="E39D_REAL_CHROMIUM_FOCUS_EVIDENCE"
            and v.get("phase")=="after"
            and v.get("caseId")==case
            and v.get("passed") is True
            and v.get("focusReached") is True
            and v.get("visibleWhenFocused") is True
            and v.get("failureCode") is None
            and v.get("pageJavaScriptErrors")==[]
            for v in details.values()
        )
    else:
        if source!=e.defective_source(case) or (folder/"receipt.json").exists():
            raise ValueError("E39E_DENIED_PATCH_CHANGED_GENERATED_WEBSITE")
    report={
        "evidenceClass":"E39E_ACTUAL_LOCAL_MODEL_BROWSER_SCORED",
        "caseId":case,"modelTag":item["model"]["tag"],
        "responseClass":kind,"policyOutcome":status,
        "policyAccepted":status=="POLICY_ACCEPTED",
        "chromiumVerifiedModelRepair":passed,
        "selfReportedGeneratedTokens":item["selfReportedGeneratedTokens"],
        "wallMs":item["wallMs"],"sourceSha256AtScore":sha(source),
        "afterBrowser":details,"realModelCalls":1,
        "ownerApproved":False,"productionPublished":False,
        "ownerPcUsed":False,"generalizationCertified":False,
    }
    destination=folder/"score.json"
    if destination.exists() or destination.is_symlink():
        raise ValueError("E39E_SCORE_ALREADY_PRESENT")
    with destination.open("x") as fp:
        json.dump(report,fp,sort_keys=True,indent=2);fp.write("\n")
    print("E39E_REAL_MODEL_CASE_SCORE",case,item["model"]["tag"],
          "accepted",status=="POLICY_ACCEPTED","verified",passed,
          "tokens",report["selfReportedGeneratedTokens"],"wallMs",report["wallMs"])


def cleanup(case:str):
    """Post-score reset, never credited as AI repair; safe on accepted/rejected."""
    if case not in e.FAULTS:
        raise ValueError("E39E_BAD_CLEANUP_CASE")
    score_receipt=load(e.location(case)/"score.json")
    current=e.checked(e.STYLE,25000)
    if (score_receipt.get("evidenceClass")!="E39E_ACTUAL_LOCAL_MODEL_BROWSER_SCORED"
            or score_receipt.get("caseId")!=case
            or score_receipt.get("sourceSha256AtScore")!=sha(current)):
        raise ValueError("E39E_CLEANUP_SCORE_OR_SOURCE_MISMATCH")
    canonical=e.clean_source()
    if score_receipt["policyAccepted"] is not True and current!=e.defective_source(case):
        raise ValueError("E39E_CLEANUP_DENIED_SOURCE_CHANGED")
    if score_receipt["policyAccepted"] is True:
        parsed,_=model_evidence_for_cleanup(case)
        if current!=validate_candidate(parsed,e.defective_source(case),canonical):
            raise ValueError("E39E_CLEANUP_ACCEPTED_SOURCE_DRIFT")
    e.STYLE.write_bytes(canonical)
    if e.checked(e.STYLE,25000)!=canonical:
        raise ValueError("E39E_CLEANUP_WRITE_MISMATCH")
    path=e.location(case)/"cleanup.json"
    with path.open("x") as fp:
        json.dump({"evidenceClass":"E39E_NOT_AI_BENCHMARK_RESET",
                   "caseId":case,"modelGeneratedRepair":False,
                   "sourceAfterSha256":sha(canonical),
                   "ownerApproved":False,"productionPublished":False},fp)
        fp.write("\n")
    print("E39E_BENCHMARK_CLEANUP_NEVER_CREDIT_MODEL",case)


def model_evidence_for_cleanup(case:str):
    item=load(e.location(case)/"model-response.json")
    return parse_response(item["rawResponse"]),item


if __name__=="__main__":
    if len(sys.argv)==3 and sys.argv[1] in ("apply","cleanup"):
        {"apply":apply,"cleanup":cleanup}[sys.argv[1]](sys.argv[2])
    elif len(sys.argv)==4 and sys.argv[1]=="score":
        score(sys.argv[2],sys.argv[3])
    else:
        raise ValueError("E39E_ONLY_FIXED_APPLY_SCORE_CLEANUP")
