#!/usr/bin/env python3
"""B4K independent fixed-site scoreboard, all two attempts counted, no model text."""
from __future__ import annotations
import json
from pathlib import Path
import sys
import e39g12b4k_two_real_model_trial as trial

ROOT=trial.ROOT
CASES=trial.CASES


def read(path, limit=15000):
    return trial.parse(trial.safe_read(path,limit),limit)


def score():
    receipt=trial.verify()
    entries=[]
    for case in CASES:
        admission=read(ROOT/"admission"/(case+".json"))
        if (admission["case"]!=case
            or admission["modelResponseSha256"]!=receipt["trials"][case]["rawResponseSha256"]
            or admission["ownerApprovalGranted"] is not False
            or admission["policyAccepted"] is not False
            or admission["stage45Go"] is not False):
            raise ValueError("B4K_ADMISSION_INTEGRITY")
        attempted={"case":case,"modelResponseSha256":admission["modelResponseSha256"],
                   "outcome":"FORMAT_REJECTED", "browserVerified":False,
                   "policyAccepted":False}
        folder=ROOT/"repairs"/case
        if admission["formatEligible"] is True:
            report=read(folder/"policy-report.json")
            if (report["case"]!=case or report["ownerApprovalGranted"] is not False
                or report["stage45Go"] is not False
                or report["sourceBeforeSha256"]!=trial.expected()["trials"][case]["sourceSha256"]
                or report["modelResponseSha256Claim"]!=admission["modelResponseSha256"]):
                raise ValueError("B4K_POLICY_REPORT_INTEGRITY")
            accepted=report["policyOutcome"]=="POLICY_APPLIED"
            if report["policyOutcome"] not in ("POLICY_APPLIED","POLICY_REJECTED"):
                raise ValueError("B4K_POLICY_OUTCOME_UNKNOWN")
            attempted["outcome"]="POLICY_ACCEPTED" if accepted else "POLICY_REJECTED"
            attempted["policyAccepted"]=accepted
            if accepted:
                browser=read(folder/"browser-report.json")
                if (browser["case"]!=case
                    or browser["verifiedViewports"]!=2
                    or len(browser["frames"])!=2
                    or {x["viewport"] for x in browser["frames"]}!={"mobile","desktop"}
                    or browser["checkedReviewedHtmlSha256"]!=report["sourceAfterSha256"]
                    or browser["productionAuthorized"] is not False
                    or browser["stage45Go"] is not False
                    or browser["error"] is not None
                    or any(x["realChromePassed"] is not True
                           or x["externalRequests"]!=0 for x in browser["frames"])):
                    raise ValueError("B4K_BROWSER_EVIDENCE_NOT_VERIFIED")
                attempted["browserVerified"]=True
                attempted["outcome"]="BROWSER_VERIFIED_PUBLIC_FIXTURE"
            elif (folder/"index.html").exists() or (folder/"browser-report.json").exists():
                raise ValueError("B4K_REJECTED_SOURCE_EXECUTED")
        elif folder.exists():
            raise ValueError("B4K_FORMAT_REJECTED_WAS_APPLIED")
        entries.append(attempted)
    approved=sum(x["policyAccepted"] for x in entries)
    verified=sum(x["browserVerified"] for x in entries)
    result={
        "evidenceClass":"E39G12B4L_QWEN7B_TARGETED_SOURCE_HINT_PUBLIC_TWO_SITE_SCORE",
        "promptTreatment":"B4L_EXACT_EXISTING_CSS_RULE_SOURCE_HINT_NO_REPLACEMENT",
        "previousB4KOriginalPromptPolicyAccepted":0,
        "previousB4KOriginalPromptBrowserVerified":0,
        "modelTag":trial.MODEL,
        "modelDigestSelfReported":receipt["modelDigestSelfReported"],
        "receiptSha256":trial.sha(trial.safe_read(trial.RECEIPT,12000)),
        "sourceBundleSha256":trial.sha(trial.safe_read(trial.PACKET,33000)),
        "modelCallsClaimed":2,"attemptsCounted":2,
        "policyAccepted":approved,"browserVerifiedRepairs":verified,
        "cases":entries,
        "publicKnownFixture":True,"genuinelyHeldOut":False,
        "independentCuratorVerified":False,
        "cryptographicallyAttestedModelProvenance":False,
        "productionAuthorized":False,"ownerApprovalGranted":False,
        "stage45Go":False,"autonomousCodingCertified":False,
    }
    destination=ROOT/"score.json"
    if destination.exists() or destination.is_symlink():
        raise ValueError("B4K_SCORE_REPLAY")
    with destination.open("x",encoding="utf-8") as f:
        json.dump(result,f,sort_keys=True,indent=2);f.write("\n")
    print("B4L_REAL_MODEL_SOURCE_HINT_PUBLIC_FIXTURE_SCORE",json.dumps({
        "attempts":2,"accepted":approved,"browserVerified":verified,
        "heldOut":False,"stage45Go":False
    },sort_keys=True))
    return result


if __name__=="__main__":
    if len(sys.argv)!=2 or sys.argv[1]!="score":
        raise ValueError("B4K_SCORE_ONLY")
    score()
