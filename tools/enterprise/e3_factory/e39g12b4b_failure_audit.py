#!/usr/bin/env python3
"""E3.9G-12B4B offline forensic replay of FOUR authentic 7B model responses.

This is evaluator-only code. It compares historical SHA256-bound replies with
the UNCHANGED E3.9G-12B2 typed policy. It never normalizes, executes, rewrites
or grants source changes, and never counts historical attempts as new calls.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import sys

import e39g12b_two_site_corpus as corpus
import e39g12b2_typed_policy as frozen

HERE=Path(__file__).resolve().parent
EVIDENCE=HERE/"e39g12b4b_historical_responses.json"
EXPECTED={
 "cityline--reduced-motion":"PARTIAL_TARGET_DECLARATION",
 "cityline--safe-untrusted-text":"UNRELATED_SOURCE_TEXT",
 "studynest--keyboard-focus-visibility":"UNRELATED_SOURCE_TEXT",
 "studynest--responsive-mobile-overflow":"PARTIAL_TARGET_DECLARATION",
}


def sha(raw:bytes)->str:return hashlib.sha256(raw).hexdigest()


def replay()->dict:
    if EVIDENCE.is_symlink() or not EVIDENCE.is_file() or EVIDENCE.stat().st_size>12000:
        raise ValueError("E39G12B4B_REPLAY_EVIDENCE_UNSAFE")
    data=json.loads(EVIDENCE.read_text(encoding="utf-8"))
    if (data.get("evidenceClass")!="E39G12B4B_READ_ONLY_REPLAY_OF_EXACT_HEAD_7B_ARTIFACT"
        or data.get("sourceCommit")!="2dfcf776f9a818c4f3bffb12366a19d5bbab7b4a"
        or data.get("sourceWorkflowRun")!=39527354798
        or data.get("sourceArtifactId")!=12314135670
        or data.get("modelTag")!="qwen2.5-coder:7b"
        or data.get("replayModelCalls")!=0
        or data.get("realHistoricalCompletions")!=4
        or data.get("ownerApproved") is not False
        or data.get("productionPublished") is not False
        or set(data.get("trials",{}))!=set(EXPECTED)):
        raise ValueError("E39G12B4B_HISTORICAL_EVIDENCE_IDENTITY_DRIFT")
    rows={}
    for token,expectedClass in EXPECTED.items():
        site,fault=token.split("--",1)
        entry=data["trials"][token]
        source=corpus.checked(corpus.HERE/site/"index.html",14000)
        if (entry.get("sourceSha256")!=sha(source)
            or type(entry.get("generatedTokens")) is not int
            or not 1<=entry["generatedTokens"]<=220
            or type(entry.get("rawModelResponse")) is not str
            or sha(entry["rawModelResponse"].encode("utf-8"))!=entry.get("rawResponseSha256")):
            raise ValueError("E39G12B4B_HISTORICAL_RAW_HASH_DRIFT_"+token)
        parsed=frozen.decode_json(entry["rawModelResponse"])
        if (parsed.get("site")!=site or parsed.get("fault")!=fault
            or parsed.get("sourceSha256")!=sha(source)
            or parsed.get("operation")!="replace_one"):
            raise ValueError("E39G12B4B_JSON_SCOPE_DRIFT_"+token)
        old=parsed["old"];target,kind=frozen.RULES[(site,fault)]
        # Security gate is immutable; only classify real observed refusal.
        try:
            frozen.validate(parsed,source)
        except ValueError as err:
            policyReason=str(err)
        else:
            raise ValueError("E39G12B4B_PRIOR_GATE_ACCEPTED_UNEXPECTEDLY")
        occurrences=source.count(old.encode("utf-8"))
        if old==target:
            classification="EXACT_EXPECTED_DECLARATION"
        elif old in target and occurrences>1:
            classification="AMBIGUOUS_FRAGMENT_OF_TARGET"
        elif old in target:
            classification="PARTIAL_TARGET_DECLARATION"
        elif occurrences:
            classification="UNRELATED_SOURCE_TEXT"
        else:
            classification="SOURCE_NOT_FOUND"
        if classification!=expectedClass or policyReason!="E39G12B2_OLD_NOT_UNIQUE_AUTHORIZED_LITERAL":
            raise ValueError("E39G12B4B_UNEXPECTED_HISTORICAL_REJECTION_"+token)
        rows[token]={"site":site,"fault":fault,"modelResponseSha256":entry["rawResponseSha256"],
            "sourceSha256":sha(source),"formatAndScopeValid":True,
            "selectedExactReviewedDeclaration":False,
            "oldOccurrenceCount":occurrences,
            "classification":classification,"frozenPolicyReason":policyReason,
            "modelBrowserVerifiedRepair":False,
            "newSourceWritten":False}
    summary={"evidenceClass":"E39G12B4B_SHA_BOUND_HISTORICAL_FAILURE_TAXONOMY",
             "sourceArtifactId":12314135670,"sourceExactHead":data["sourceCommit"],
             "modelTag":data["modelTag"],
             "historicalModelCompletions":4,"newModelCalls":0,
             "wellFormedInScopeProposalJson":sum(x["formatAndScopeValid"] for x in rows.values()),
             "exactReviewedOldDeclarationMatches":0,
             "rejectedWrongSourceTarget":4,"policyApproved":0,
             "realAfterBrowserViewports":0,"realModelBrowserVerifiedRepairs":0,
             "rejectionsByClass":{kind:sum(x["classification"]==kind for x in rows.values())
                                  for kind in sorted(set(EXPECTED.values()))},
             "perCase":rows,"policyWeakened":False,"newPromptsSent":False,
             "externallyCurated":False,"stage45Go":False,
             "ownerApprovalGranted":False,"productionPublished":False}
    print("E39G12B4B_REPLAY_REJECTION_TAXONOMY",json.dumps({
        k:v for k,v in summary.items() if k not in ("perCase",)},sort_keys=True))
    return summary


if __name__=="__main__":
    if len(sys.argv)!=2 or sys.argv[1]!="audit":
        raise ValueError("E39G12B4B_OFFLINE_AUDIT_ONLY")
    report=replay()
    print(json.dumps(report,indent=2,sort_keys=True))
