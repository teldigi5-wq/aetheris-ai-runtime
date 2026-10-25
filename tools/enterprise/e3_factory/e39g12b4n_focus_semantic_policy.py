#!/usr/bin/env python3
"""B4N: bounded, trusted-fixture-only focus CSS candidate admission.

This is NOT general agent editing authority. A candidate is merely structurally
eligible; independently observed browser behavior determines test acceptance.
Only the pinned B4I synthetic HTML, one exact declaration, and fixed case IDs
are accepted. Intended to run exclusively via network-none locked worker.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import re
import sys

HERE=Path(__file__).resolve().parent
REPO=HERE.parents[2]
SOURCE=HERE/"e39g12b4i/fixtures/focus-negative.html"
ROOT=REPO/"target/e3-factory/e39g12b4n"
ORIGINAL_SHA="385d3fac74d775586bb389beb3068e6e34a7fb0e08f8d854c80cb55b433b290b"
OLD=".skip:focus{top:-120px;}"
CASES=frozenset(("zero","six","eight","sixteen","thirtytwo","malicious"))
PROPOSAL_KEYS=frozenset(("caseId","operation","sourceSha256","old","new"))
ALLOWED_RULE=re.compile(r"^\.skip:focus\{top:(0|[1-9][0-9]?)px;\}$")
TEST_CLASS="E39G12B4N_SYNTHETIC_BOUNDED_CSS_REVIEW_NOT_AI"


def sha(data:bytes)->str:
    return hashlib.sha256(data).hexdigest()


def unique(pairs):
    result={}
    for key,value in pairs:
        if key in result:
            raise ValueError("B4N_DUPLICATE_FIELD")
        result[key]=value
    return result


def checked(path:Path,limit:int)->bytes:
    if path.is_symlink() or not path.is_file() or not 0<path.stat().st_size<=limit:
        raise ValueError("B4N_UNSAFE_SOURCE_OR_RECEIPT")
    return path.read_bytes()


def original()->bytes:
    raw=checked(SOURCE,5000)
    if sha(raw)!=ORIGINAL_SHA or raw.count(OLD.encode())!=1:
        raise ValueError("B4N_ORIGINAL_FIXTURE_DRIFT")
    return raw


def inspect(case:str,proposal:dict,source:bytes)->tuple[bytes|None,str,int|None]:
    """Only prepare one safe-bounded stylesheet variant; not an approval."""
    if case not in CASES:
        raise ValueError("B4N_CASE_NOT_ALLOWLISTED")
    if type(proposal) is not dict or set(proposal)!=PROPOSAL_KEYS:
        return None,"SCHEMA_REJECTED",None
    if (proposal["caseId"]!=case or proposal["operation"]!="replace_one"
        or proposal["sourceSha256"]!=sha(source)
        or proposal["old"]!=OLD):
        return None,"PROVENANCE_OR_IDENTITY_REJECTED",None
    replacement=proposal["new"]
    if type(replacement) is not str or len(replacement)>70:
        return None,"RULE_FORMAT_REJECTED",None
    match=ALLOWED_RULE.fullmatch(replacement)
    if match is None:
        return None,"RULE_FORMAT_REJECTED",None
    top=int(match.group(1))
    if not 0<=top<=64:
        return None,"RULE_OUT_OF_RANGE",None
    if source.count(OLD.encode())!=1:
        return None,"SOURCE_NOT_UNIQUE",None
    final=source.replace(OLD.encode(),replacement.encode(),1)
    if (len(final)>5000 or
        final.replace(replacement.encode(),OLD.encode(),1)!=source or
        sha(final)==sha(source)):
        raise ValueError("B4N_PATCH_DELTA_DRIFT")
    return final,"STRUCTURAL_ONLY_BROWSER_REVIEW_REQUIRED",top


def apply(case:str)->None:
    if case not in CASES or ROOT.is_symlink() or not ROOT.is_dir():
        raise ValueError("B4N_CASE_OR_ROOT_DENIED")
    if (ROOT/"incoming").is_symlink() or (ROOT/"results").is_symlink():
        raise ValueError("B4N_SYMLINKED_SUBDIRECTORY")
    location=ROOT/"incoming"/(case+".json")
    proposal=json.loads(checked(location,1200),object_pairs_hook=unique)
    source=original()
    new_source,category,top=inspect(case,proposal,source)
    target=ROOT/"results"/case
    if target.exists() or target.is_symlink() or not target.parent.is_dir():
        raise ValueError("B4N_REPLAY_OR_RESULT_PARENT_DENIED")
    target.mkdir(mode=0o700)
    if new_source is not None:
        (target/"index.html").write_bytes(new_source)
    report={
        "evidenceClass":TEST_CLASS,"caseId":case,
        "outcome":category,"candidateTopPixels":top,
        "sourceBeforeSha256":sha(source),
        "sourceAfterSha256":sha(new_source) if new_source else None,
        "structurallyEligible":new_source is not None,
        "semanticBrowserVerified":False,"patchApprovedForProduction":False,
        "modelCallsPerformed":0,"modelAuthoredPatch":False,
        "independentCuratorVerified":False,"arbitraryHtmlPermitted":False,
        "ownerApprovalGranted":False,"stage45Go":False,
    }
    (target/"report.json").write_text(json.dumps(report,sort_keys=True,indent=2)+"\n")
    print("B4N_NETWORK_NONE_STRUCTURAL_CSS_PROBE",case,category)


if __name__=="__main__":
    if len(sys.argv)!=3 or sys.argv[1]!="probe":
        raise ValueError("B4N_EXACT_FIXED_PROBE_ONLY")
    apply(sys.argv[2])
