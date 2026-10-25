#!/usr/bin/env python3
"""B4K: B4J-reviewed fixed public-site patch from independently received model text.

MUST run only inside run_locked_worker.sh e39g12b4k-apply <focus|motion>.
Even a valid model quote gets no arbitrary edit authority. Rejected proposals
produce evidence but never a patched HTML file.
"""
from __future__ import annotations
import json
from pathlib import Path
import sys
import e39g12b4j_typed_control_worker as strict

ROOT=strict.REPO/"target/e3-factory/e39g12b4k"
CASES=("focus","motion")


def apply(case: str) -> None:
    if case not in CASES or ROOT.is_symlink() or not ROOT.is_dir():
        raise ValueError("B4K_UNREVIEWED_CASE_OR_WORKSPACE")
    proposal_path=ROOT/"incoming-proposals"/(case+".json")
    if proposal_path.is_symlink() or not proposal_path.is_file():
        raise ValueError("B4K_NO_FORMAT_ELIGIBLE_MODEL_PROPOSAL")
    admission=ROOT/"admission"/(case+".json")
    raw_report=strict.checked(admission,1800)
    pre=json.loads(raw_report,object_pairs_hook=strict.pairs_unique)
    if (set(pre)!={"case","formatEligible","reason","modelResponseSha256",
                   "ownerApprovalGranted","policyAccepted","modelRepairVerified","stage45Go"}
        or pre["case"]!=case or pre["formatEligible"] is not True
        or pre["reason"]!="B4K_FORMAT_ELIGIBLE_UNTRUSTED"
        or pre["ownerApprovalGranted"] is not False
        or pre["policyAccepted"] is not False or pre["stage45Go"] is not False):
        raise ValueError("B4K_ADMISSION_CANNOT_GRANT_AUTHORITY")
    raw=strict.checked(proposal_path,1500)
    candidate=json.loads(raw,object_pairs_hook=strict.pairs_unique)
    original=strict.checked(strict.FIX/(case+"-negative.html"),5000)
    golden=strict.checked(strict.FIX/(case+"-positive.html"),5000)
    reason="UNCLASSIFIED"
    applied=False
    try:
        final=strict.review(case,candidate,original,golden)
        applied=True
    except (ValueError,TypeError,UnicodeError) as exc:
        reason=str(exc)
        if not reason.startswith("B4J_"):
            raise
    target=ROOT/"repairs"/case
    if target.exists() or target.is_symlink():
        raise ValueError("B4K_REPAIR_REPLAY")
    parent=target.parent
    if parent.is_symlink() or not parent.is_dir():
        raise ValueError("B4K_REPAIR_PARENT_SCOPE")
    target.mkdir(mode=0o700)
    if applied:
        (target/"index.html").write_bytes(final)
    report={
        "evidenceClass":"E39G12B4K_ISOLATED_FIXED_POLICY_MODEL_PROPOSAL",
        "case":case,
        "policyOutcome":"POLICY_APPLIED" if applied else "POLICY_REJECTED",
        "reason":"EXACT_REVIEWED_CSS_ONLY" if applied else reason,
        "sourceBeforeSha256":strict.sha(original),
        "sourceAfterSha256":strict.sha(final) if applied else strict.sha(original),
        "modelResponseSha256Claim":pre["modelResponseSha256"],
        "reviewedReferenceMatches":applied,
        "networkNoneWorkerRequired":True,
        "modelProvenanceCryptographicallyAttested":False,
        "modelTextExecutedAsScript":False,
        "arbitraryHtmlAuthorized":False,
        "independentCuratorVerified":False,
        "browserVerifiedModelRepair":False,
        "ownerApprovalGranted":False,
        "productionAuthorized":False,"stage45Go":False,
    }
    (target/"policy-report.json").write_text(
        json.dumps(report,sort_keys=True,indent=2)+"\n",encoding="utf-8")
    print("B4K_FIXED_TRUSTED_SOURCE_POLICY",case,report["policyOutcome"],report["reason"])


if __name__=="__main__":
    if len(sys.argv)!=3 or sys.argv[1]!="apply":
        raise ValueError("B4K_FIXED_APPLY_ONLY")
    apply(sys.argv[2])
