#!/usr/bin/env python3
"""Four KNOWN PUBLIC control websites, genuine-model smoke only (not held-out).
Emit only B4G-compatible broken sources. Separately classify B4H raw answers.
No references, model calls, HTML execution, patch acceptance, or browser credit.
"""
from __future__ import annotations
from collections import Counter
import hashlib
import json
from pathlib import Path
import sys
import e39g12b4g_curator_boundary as b4g
import e39g12b4h_proposal_admission as b4h

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]/"target/e3-factory/e39g-public-smoke"
ZIP=ROOT/"model.zip"
RECEIPT=ROOT/"model-receipt.json"
SCORE=ROOT/"public-score.json"
# Git blob digests fix the exact source bytes; they are NOT golden repair answers.
CASES=(
  ("cityline-public","cityline","defensive-html",
   "A station query is assigned to innerHTML. Propose a safe minimal correction without giving arbitrary script execution or tool permissions.",
   "fixtures/e39g12b/cityline/index.html","82bafe1f0c489daf3fc62503edf84c357646636c"),
  ("focus-public","b4i-controls","keyboard-focus",
   "The skip link remains offscreen on keyboard focus, so keyboard users cannot see the focused link. Correct its defective focus CSS.",
   "e39g12b4i/fixtures/focus-negative.html","63d3c2da9c7ac13c0640e06487e12b6094c031ea"),
  ("motion-public","b4i-controls","reduced-motion",
   "The ticker keeps animating even when prefers-reduced-motion is reduce. Propose a minimal targeted CSS correction.",
   "e39g12b4i/fixtures/motion-negative.html","6f3633b45d3b3779f4d13a0db30819d6763958ae"),
  ("studynest-public","studynest","mobile-overflow",
   "On narrow mobile screens the course grid enforces a 960px min-width and causes horizontal overflow. Preserve desktop layout in a minimal safe repair.",
   "fixtures/e39g12b/studynest/index.html","bde27aeba6b89c0a7f0d6f3a2fe2827c480a2e24"),
)

def sha_git_blob(data):
    return hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()

def packet():
    entries=[]
    for cid,site,fault,description,file,expected in CASES:
        p=HERE/file
        # Windows Git checkout may convert LF to CRLF even though the
        # immutable repository blob is LF. Canonicalize text then insist
        # the canonical bytes match the exact original Git object SHA-1.
        # This does NOT permit any other source or semantic change.
        source=b4g.checked(p,b4g.MAX_HTML).replace(b"\r\n",b"\n")
        if sha_git_blob(source)!=expected:
            raise ValueError("PUBLIC_SMOKE_PINNED_FIXTURE_DRIFT")
        text=source.decode("utf-8","strict")
        if "<!doctype html>" not in text.lower() or "</html>" not in text.lower():
            raise ValueError("PUBLIC_SMOKE_INVALID_SOURCE")
        entries.append({
           "id":cid,"site":site,"fault":fault,
           "defectDescription":description,"sourceLicense":"repository-authored-research",
           "brokenHtml":text,"brokenSha256":b4g.digest(source),
        })
    if len(entries)!=4 or len({x["site"] for x in entries})!=3:
        raise ValueError("PUBLIC_SMOKE_CORPUS_DRIFT")
    # Public dummy marker, NOT a curator seal or private reference commitment.
    commitment=b4g.digest(b"E39G_PUBLIC_DEVELOPER_CONTROL_NO_PRIVATE_EVALUATOR_SEAL_V1")
    return b4g.packet_of(entries,commitment)

def expected_zip():
    return b4g.public_zip(packet())

def pack():
    if ROOT.is_symlink() or ZIP.is_symlink() or ZIP.exists():
        raise ValueError("PUBLIC_SMOKE_ZIP_REPLAY")
    ROOT.mkdir(parents=True,exist_ok=True)
    ZIP.write_bytes(expected_zip())
    print("E39G_FOUR_KNOWN_PUBLIC_SOURCES_NO_PRIVATE_REFERENCE",
          json.dumps({"count":4,"sites":3,"zipSha256":b4g.digest(ZIP.read_bytes()),
                      "realModelCalls":0,"genuinelyHeldOut":False},sort_keys=True))

def score():
    if b4g.checked(ZIP,b4g.MAX_TOTAL)!=expected_zip():
        raise ValueError("PUBLIC_SMOKE_SOURCE_OR_PACKET_DRIFT")
    receipt=b4g.parse(b4g.checked(RECEIPT,b4h.MAX_RECEIPT))
    original=packet()
    cases={x["id"]:x for x in original["cases"]}
    if (set(receipt)!=b4h.RECEIPT_FIELDS
        or receipt["evidenceClass"]!=b4h.RECEIPT_CLASS
        or receipt["sourceBundleSha256"]!=b4g.digest(ZIP.read_bytes())
        or receipt["ownerApprovalGranted"] is not False
        or receipt["goldenReferenceAvailable"] is not False
        or receipt["modelOutputExecuted"] is not False
        or type(receipt["modelCallsSelfReported"]) is not int
        or receipt["modelCallsSelfReported"]!=4
        or type(receipt["modelTag"]) is not str
        or not b4h.MODEL_TAG.fullmatch(receipt["modelTag"])
        or type(receipt["trials"]) is not list
        or len(receipt["trials"])!=4):
        raise ValueError("PUBLIC_SMOKE_RECEIPT_CONTRACT")
    seen={}
    for row in receipt["trials"]:
        if type(row) is not dict or set(row)!=b4h.TRIAL_FIELDS:
            raise ValueError("PUBLIC_SMOKE_ROW_FIELDS")
        cid=row["caseId"]
        answer=row["rawModelResponse"]
        if (type(cid) is not str or cid not in cases or cid in seen
            or type(answer) is not str
            or not 0<len(answer.encode("utf-8"))<=b4h.MAX_RESPONSE_BYTES
            or row["sourceSha256"]!=cases[cid]["brokenSha256"]
            or row["rawResponseSha256"]!=b4g.digest(answer.encode("utf-8"))):
            raise ValueError("PUBLIC_SMOKE_UNTRUSTED_ROW_DRIFT")
        seen[cid]=answer
    if set(seen)!=set(cases):
        raise ValueError("PUBLIC_SMOKE_UNCOUNTED_ATTEMPT")
    outcomes=[b4h.inspect_response(seen[cid],cases[cid]) for cid in sorted(cases)]
    report={
        "evidenceClass":"E39G_PUBLIC_FOUR_SITE_MODEL_ONLY_STATIC_QUOTE_REVIEW",
        "sourceBundleSha256":b4g.digest(ZIP.read_bytes()),
        "modelReceiptSha256":b4g.digest(RECEIPT.read_bytes()),
        "modelTagUnattested":receipt["modelTag"],
        "attemptsCounted":4,
        "actualCallsFromReceiptSelfReportedNotIndependentlyAttested":4,
        "sitesCount":3,"knownPublicDeveloperFixtures":True,
        "genuinelyHeldOut":False,"independentCuratorVerified":False,
        "categories":dict(sorted(Counter(row["outcome"] for row in outcomes).items())),
        "results":outcomes,"staticQuoteOnly":True,
        "typedPolicyAccepted":0,"browserVerifiedRepairs":0,
        "ownerApprovalGranted":False,"productionAuthorized":False,
        "stage45Go":False,
    }
    if SCORE.is_symlink() or SCORE.exists():
        raise ValueError("PUBLIC_SMOKE_SCORE_REPLAY")
    SCORE.write_bytes(b4g.canonical(report))
    print("E39G_GENUINE_MODEL_PUBLIC_ONLY_STATIC_ADMISSION",
          json.dumps({"attempts":4,"categories":report["categories"],
                      "heldOut":False,"browserVerified":0,"stage45Go":False},
                     sort_keys=True))
    return report

if __name__=="__main__":
    if len(sys.argv)!=2 or sys.argv[1] not in ("pack","score"):
        raise ValueError("PUBLIC_SMOKE_FIXED_COMMAND_ONLY")
    (pack if sys.argv[1]=="pack" else score)()
