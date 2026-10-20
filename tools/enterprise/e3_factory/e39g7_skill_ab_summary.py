#!/usr/bin/env python3
"""E3.9G-7 zero-inflation summary for 2 cases x 2 arms x one REAL model.

Never claim browser-confirmed repair; this is strictly policy eligibility.
"""
from __future__ import annotations
import json
import sys
from pathlib import Path

import e39g7_skill_ab_adviser as trial
import worker


def summarize(model: str) -> dict:
    if model not in trial.MODEL_TAGS: raise ValueError("E39G7_MODEL_NOT_APPROVED")
    cases=[]
    for case in ("top-120","opacity-zero"):
        folder=worker.REPO/"target/e3-factory/e39d"/case
        path=folder/("e39g7-"+model.replace(":","-")+"-score.json")
        if path.is_symlink() or not path.is_file() or path.stat().st_size>7500:
            raise ValueError("E39G7_CASE_SCORE_ABSENT")
        record=json.loads(path.read_text())
        if (record.get("evidenceClass")!="E39G7_REAL_MODEL_TWO_ARM_POLICY_ELIGIBILITY_ONLY"
                or record.get("modelTag")!=model or record.get("caseId")!=case
                or record.get("realModelCalls")!=2
                or set(record.get("arms",{}))!=set(trial.ARMS)
                or record.get("patchApplied") is not False
                or record.get("browserAfterModelRepairVerified") is not False):
            raise ValueError("E39G7_SCORE_NOT_MATCHING_REAL_TRIAL")
        cases.append(record)
    arm_counts={arm:{
       "attempts":2,
       "policyEligible":sum(int(x["arms"][arm]["policyEligible"]) for x in cases),
       "abstained":sum(x["arms"][arm]["outcome"]=="ABSTAIN" for x in cases),
       "rejected":sum(x["arms"][arm]["outcome"]=="POLICY_REJECTED" for x in cases),
       "invalid":sum(x["arms"][arm]["outcome"]=="INVALID" for x in cases)
    } for arm in trial.ARMS}
    report={"evidenceClass":"E39G7_TWO_REAL_MODEL_CASES_SKILL_AB_NOT_GENERALIZABLE",
            "modelTag":model,"cases":len(cases),"realModelCalls":4,
            "browserBeforeScreens":4,
            "arms":arm_counts,
            "modelPatchChromiumVerifiedCount":0,
            "modelPatchBrowserAfterTestsRun":0,
            "syntheticInDistributionCases":True,
            "externallyCuratedSealed":False,
            "skillCausedPerformanceImprovementProven":False,
            "noOwnerAccess":True,"noModelExecutionAuthority":True,
            "productionReleased":False,
            "perCase":[{"caseId":x["caseId"],
                        "baseline":x["arms"]["baseline"]["outcome"],
                        "skillContext":x["arms"]["skill-context"]["outcome"]}
                        for x in cases]}
    out=worker.REPO/"target/e3-factory/e39d"/(
        "e39g7-summary-"+model.replace(":","-")+".json")
    if out.exists() or out.is_symlink(): raise ValueError("E39G7_SUMMARY_ALREADY_WRITTEN")
    out.write_text(json.dumps(report,indent=2,sort_keys=True)+"\n")
    print("E39G7_REAL_LOCAL_OLLAMA_AB_COUNTS",json.dumps(report,sort_keys=True))
    return report


if __name__=="__main__":
    if len(sys.argv)!=2: raise ValueError("E39G7_EXACT_MODEL_ARG")
    summarize(sys.argv[1])
