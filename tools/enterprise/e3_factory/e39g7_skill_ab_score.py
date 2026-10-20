#!/usr/bin/env python3
"""Independent E3.9G-7 model-advice scorer: deterministic CSS eligibility only.

NO model calls; NO CSS writes; browser-after outcome is NOT evaluated here.
Model proposer NEVER imports this file. Genuine before-Chromium + strict
policy can assess eligible draft, not a real browser-verified repair.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import sys

import e39d_heldout_focus_evaluator as suite
import e39e_model_policy as css_policy
import e39e_real_model_adviser as original
import e39g7_skill_ab_adviser as ab

EVIDENCE="E39G7_REAL_MODEL_TWO_ARM_POLICY_ELIGIBILITY_ONLY"


def read_record(path: Path) -> dict:
    if path.is_symlink() or not path.is_file() or path.stat().st_size>7000:
        raise ValueError("E39G7_MISSING_OR_OVERSIZED_MODEL_RECEIPT")
    def no_duplicate(pairs):
        result={}
        for k,v in pairs:
            if k in result: raise ValueError("E39G7_DUPLICATE_RECEIPT_FIELDS")
            result[k]=v
        return result
    return json.loads(path.read_text(encoding="utf-8"),object_pairs_hook=no_duplicate)


def score(model: str,case: str) -> dict:
    if model not in ab.MODEL_TAGS or case not in ab.CASE_IDS:
        raise ValueError("E39G7_UNSUPPORTED_CASE_OR_MODEL")
    source,canonical=css_policy.snapshot(case)
    folder,original_source,typed,css=original.inputs(case)
    if original_source!=source:
        raise ValueError("E39G7_SOURCE_BETWEEN_SCORER_AND_MODEL_DRIFT")
    text,skill_receipts=ab.pinned_skill_context()
    before={viewport:ab.sha((folder/(viewport+"-before.json")).read_bytes())
            for viewport in ("mobile","desktop")}
    results={}
    for arm in ab.ARMS:
        record=read_record(folder/("e39g7-"+model.replace(":","-")+"-"+arm+".json"))
        if (record.get("evidenceClass")!="E39G7_REAL_SKILL_VS_BASELINE_OLLAMA_REPLY"
                or record.get("arm")!=arm or record.get("modelTag")!=model
                or record.get("caseId")!=case
                or record.get("realModelCalls")!=1
                or record.get("actualModelCompletion") is not True
                or record.get("sourceSha256")!=ab.sha(source)
                or record.get("browserBeforeSha256")!=before
                or record.get("promptSha256")!=ab.sha(
                    ab.prompt_arm(case,typed,css,arm,text).encode())
                or record.get("skillSources")!=(skill_receipts if arm=="skill-context" else [])
                or record.get("skillExcerptIncluded")!=(arm=="skill-context")
                or record.get("modelTextExecuted") is not False
                or record.get("websiteEdited") is not False
                or record.get("ownerApprovalGranted") is not False
                or record.get("productionPublished") is not False
                or record.get("chromiumVerifiedRepair") is not False
                or record.get("externallySealedBenchmark") is not False
                or type(record.get("selfReportedGeneratedTokens")) is not int
                or record["selfReportedGeneratedTokens"]<=0):
            raise ValueError("E39G7_UNBOUND_MODEL_OR_SKILL_RECEIPT")
        raw=record.get("rawModelResponse")
        if type(raw) is not str or record.get("rawResponseSha256")!=ab.sha(raw.encode()):
            raise ValueError("E39G7_MODEL_REPLY_DIGEST_MISMATCH")
        classification=record.get("responseClass")
        if classification not in ("ABSTAIN","INVALID_MODEL_RESPONSE","SELECTOR_PROPOSAL"):
            raise ValueError("E39G7_MODEL_RESPONSE_CLASS_UNKNOWN")
        outcome="INELIGIBLE"
        reason="E39G7_NOT_EVALUATED"
        try:
            proposal=css_policy.parse_response(raw)
        except ValueError as exc:
            if classification=="ABSTAIN":
                if original.strict_json(raw)!={"operation":"abstain"}:
                    raise ValueError("E39G7_FALSE_ABSTAIN_CLASS") from exc
                outcome="ABSTAIN"
                reason="E39G7_MODEL_ABSTAIN"
            elif classification=="INVALID_MODEL_RESPONSE":
                try:
                    original.strict_json(raw)
                except ValueError:
                    outcome="INVALID"
                    reason="E39G7_INVALID_UNTRUSTED_MODEL_REPLY"
                else:
                    raise ValueError("E39G7_WRONG_MODEL_CLASS_FOR_VALID_JSON") from exc
            else:
                raise ValueError("E39G7_MODEL_CLASS_SHAPE_CONTRADICTION") from exc
        else:
            if classification!="SELECTOR_PROPOSAL":
                raise ValueError("E39G7_MODEL_CLASS_PROPOSAL_CONTRADICTION")
            try:
                # Reuse unchanged, independently reviewed CSS source policy.
                amended=css_policy.validate_candidate(proposal,source,canonical)
            except ValueError as exc:
                if not str(exc).startswith("E39E_"):
                    raise
                outcome="POLICY_REJECTED"
                reason=str(exc)
            else:
                if amended==source or amended==canonical and case not in ab.CASE_IDS:
                    raise ValueError("E39G7_UNSAFE_AMENDMENT")
                outcome="POLICY_ELIGIBLE_NOT_BROWSER_TESTED"
                reason="E39G7_INDEPENDENT_STATIC_POLICY_ONLY"
        results[arm]={"arm":arm,"outcome":outcome,"reason":reason,
                      "modelResponseSha256":record["rawResponseSha256"],
                      "promptSha256":record["promptSha256"],
                      "generatedTokens":record["selfReportedGeneratedTokens"],
                      "policyEligible":outcome=="POLICY_ELIGIBLE_NOT_BROWSER_TESTED",
                      "chromiumVerifiedRepair":False}
    report={"evidenceClass":EVIDENCE,"modelTag":model,"caseId":case,
            "realModelCalls":2,"browserBeforeScreens":2,
            "arms":results,
            "skillBenefitNotAssumed":True,
            "modelUsedExactPinnedSkillExcerpts":True,
            "patchApplied":False,"browserAfterModelRepairVerified":False,
            "generalizationCertified":False,"ownerApprovalGranted":False,
            "productionPublished":False,"externallyCuratedSealedCorpus":False}
    file=folder/("e39g7-"+model.replace(":","-")+"-score.json")
    if file.exists() or file.is_symlink():
        raise ValueError("E39G7_ALREADY_SCORED")
    with file.open("x",encoding="utf-8") as output:
        json.dump(report,output,sort_keys=True,indent=2);output.write("\n")
    print("E39G7_GENUINE_SKILL_AB_STATIC_SCORE",model,case,
          [(arm,rec["outcome"]) for arm,rec in results.items()])
    return report


if __name__=="__main__":
    if len(sys.argv)!=3:
        raise ValueError("E39G7_EXACT_TWO_ARGUMENTS")
    score(sys.argv[1],sys.argv[2])
