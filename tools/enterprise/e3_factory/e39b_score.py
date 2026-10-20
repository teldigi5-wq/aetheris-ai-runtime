#!/usr/bin/env python3
"""E3.9B independent model/browser scorer. NO source repair or code execution.

A refused/invalid model output is still a completed real inference trial.
A policy-accepted output is a correct fix ONLY if the independent browser and
canonical source both pass; no fixture-restoration credit is permitted.
"""
from __future__ import annotations
import hashlib
import json
import sys

import e39_keyboard_patch_gate as gate

ALLOWED = frozenset(("hermes3:3b", "granite4:3b"))
OUTCOMES = frozenset(("POLICY_ACCEPTED","POLICY_REJECTED","ABSTAIN","INVALID"))


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def score(outcome: str) -> dict:
    if outcome not in OUTCOMES:
        raise ValueError("E39B_OUTCOME_NOT_ALLOWED")
    info = json.loads(gate.checked(gate.DIR/"real-model-response.json",4600))
    seed = json.loads(gate.checked(gate.DIR/"seed.json",4096))
    before = json.loads(gate.checked(gate.DIR/"browser-before.json",4096))
    current = gate.checked(gate.STYLE,25_000)
    canonical = gate.checked(gate.worker.HERE/"styles.css",25_000)
    model=info.get("model")
    if (not isinstance(model,dict) or model.get("tag") not in ALLOWED
            or info.get("realModelCalls") != 1
            or type(info.get("selfReportedGeneratedTokens")) is not int
            or info["selfReportedGeneratedTokens"] <= 0
            or info.get("ownerApproved") is not False
            or info.get("modelOutputExecutedDirectly") is not False
            or info.get("productionPublished") is not False
            or info.get("sourceSha256") != seed["beforeSha256"]
            or info.get("browserBeforeSha256") != sha(
                gate.checked(gate.DIR/"browser-before.json",4096))
            or before.get("passed") is not False
            or before.get("failureCode") != "E39_FOCUSED_SKIP_LINK_OFFSCREEN"):
        raise ValueError("E39B_REAL_MODEL_OR_BROWSER_BEFORE_RECEIPT_INVALID")
    resp_class = info.get("responseClass")
    if (outcome=="ABSTAIN" and resp_class!="ABSTAIN"
            or outcome=="INVALID" and resp_class!="INVALID_MODEL_RESPONSE"
            or outcome in ("POLICY_ACCEPTED","POLICY_REJECTED")
            and resp_class!="TEXT_REPLACEMENT_PROPOSED"):
        raise ValueError("E39B_POLICY_LABEL_DOES_NOT_MATCH_MODEL_OUTPUT")
    passed=False
    after=None
    if outcome=="POLICY_ACCEPTED":
        receipt=json.loads(gate.checked(gate.DIR/"receipt.json",4096))
        after=json.loads(gate.checked(gate.DIR/"browser-after.json",4096))
        passed=bool(
            receipt.get("evidenceClass")==
                "E39B_INDEPENDENT_POLICY_ACCEPTED_MODEL_TEXT_BROWSER_PENDING"
            and receipt.get("modelTag")==model["tag"]
            and receipt.get("ownerApproved") is False
            and receipt.get("modelOutputExecutedDirectly") is False
            and receipt.get("afterSha256")==sha(current)
            and after.get("passed") is True
            and after.get("failureCode") is None
            and after.get("caseId")=="keyboard-skip-link"
            and after.get("focusReached") is True
            and after.get("visibleWhenFocused") is True
            and after.get("geometry",{}).get("top",-1)>=0
            and current==canonical
            and sha(current)==seed["expectedCanonicalSha256"])
    else:
        if current==canonical or sha(current)!=seed["beforeSha256"]:
            raise ValueError("E39B_REFUSED_OR_INVALID_TEXT_MUTATED_SITE")
        if (gate.DIR/"receipt.json").exists():
            raise ValueError("E39B_UNEXPECTED_PATCH_RECEIPT_ON_REFUSAL")
    result={
        "evidenceClass":"E39B_INDEPENDENT_REAL_INFERENCE_BROWSER_SCORE",
        "model":model, "caseId":"keyboard-skip-link",
        "realModelCalls":1,
        "generatedTokens":info["selfReportedGeneratedTokens"],
        "wallMs":info["wallMs"],"responseClass":resp_class,
        "policyOutcome":outcome,
        "policyAccepted":outcome=="POLICY_ACCEPTED",
        "chromiumVerifiedModelRepair":passed,
        "browserBeforeActuallyFailed":True,
        "browserAfter":after,
        "modelOutputExecutedDirectly":False,
        "ownerApprovalGranted":False,
        "productionPublished":False,
        "ownerPcUsed":False,
        "generalizationCertified":False,
        "provenanceCryptographicallyAttested":False,
    }
    target=gate.DIR/"e39b-score.json"
    if target.exists() or target.is_symlink():
        raise ValueError("E39B_SCORE_ALREADY_EXISTS")
    with target.open("x",encoding="utf-8") as handle:
        json.dump(result,handle,indent=2,sort_keys=True)
        handle.write("\n")
    print("E39B_REAL_LOCAL_MODEL_BROWSER_SCORE",
          model["tag"],"verified",str(passed),"policy",outcome,
          "tokens",result["generatedTokens"],"ms",result["wallMs"])
    return result


if __name__=="__main__":
    if len(sys.argv)!=2:
        raise ValueError("E39B_SCORE_ONLY_SINGLE_OUTCOME")
    score(sys.argv[1])
