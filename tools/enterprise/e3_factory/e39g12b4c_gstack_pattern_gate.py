#!/usr/bin/env python3
"""E3.9G-12B4C: read-only, immutable gstack pattern inventory and cinematic QA policy.

Gstack files are external lower-trust data. No install, subprocess, model,
browser daemon, network, writes, owner action, or imported upstream execution.
A claimed 'luxury' site is NOT verified just because JSON or a model says so.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import re
import sys

HERE=Path(__file__).resolve().parent
INDEX=HERE/"e39g12b4c_reviewed_gstack_patterns.json"
UPSTREAM="https://github.com/garrytan/gstack"
COMMIT="b616d6c53dd8fc0d8db210444b44a131b553b83e"
PATHS={
 "design-consultation/SKILL.md":"design-system",
 "plan-design-review/SKILL.md":"design-plan",
 "design-shotgun/SKILL.md":"variant-comparison",
 "plan-eng-review/SKILL.md":"technical-review",
 "design-review/SKILL.md":"visual-audit",
 "qa/SKILL.md":"browser-functional-qa",
 "review/SKILL.md":"pre-merge-review",
 "guard/SKILL.md":"destructive-command-guard",
 "freeze/SKILL.md":"write-scope-freeze",
 "careful/SKILL.md":"destructive-command-review",
 "browse/src/browser-skills.ts":"deterministic-browser-fixture",
 "docs/designs/BROWSER_SKILLS_V1.md":"browser-skill-capability-boundary",
}
PHASES={"visual-brief","architecture","browser-qa","code-review","safety"}
DENY_FLAGS={"autoActivate","toolExecutionGranted","modelPermissionsGranted",
            "ownerApprovalGranted","productionDeploymentGranted","stage45Go"}
EVIDENCE_FIELDS={"evidenceClass","site","viewport","width","height",
    "sourceSha256","screenshots","scroll","motion","webgl","ui","errors",
    "independentVerifier","ownerApprovalGranted","productionPublished","modelCalls"}


def unique(pairs):
    out={}
    for name,val in pairs:
        if name in out:raise ValueError("E39G12B4C_DUPLICATE_JSON_KEY")
        out[name]=val
    return out


def safe_load(path:Path,max_size=26000):
    if path.is_symlink() or not path.is_file() or not 0<path.stat().st_size<=max_size:
        raise ValueError("E39G12B4C_FILE_MISSING_OR_UNSAFE")
    return json.loads(path.read_text("utf-8"),object_pairs_hook=unique)


def validate_index(index:dict)->dict:
    if type(index) is not dict or index.get("schemaVersion")!=1:
        raise ValueError("E39G12B4C_UNREVIEWED_MANIFEST_SCHEMA")
    if (index.get("upstream")!=UPSTREAM or index.get("upstreamCommit")!=COMMIT
        or index.get("license")!="MIT"
        or index.get("mode")!="read-only-external-pattern-index"
        or index.get("allSkillsReadOnly") is not True):
        raise ValueError("E39G12B4C_UNPINNED_UPSTREAM_OR_TRUST")
    for flag in DENY_FLAGS:
        if index.get(flag) is not False:
            raise ValueError("E39G12B4C_FORBIDDEN_ROOT_AUTHORITY_"+flag)
    patterns=index.get("patterns")
    if type(patterns) is not list or len(patterns)!=len(PATHS):
        raise ValueError("E39G12B4C_INCOMPLETE_PATTERN_SET")
    found={}
    for item in patterns:
        if type(item) is not dict or set(item)!={"path","upstreamBlobSha1","pattern",
                "phase","mode","autoActivate","executionAllowed"}:
            raise ValueError("E39G12B4C_PATTERN_SCHEMA_DRIFT")
        path=item["path"]
        if (path not in PATHS or path in found or item["pattern"]!=PATHS[path]
            or type(item["upstreamBlobSha1"]) is not str
            or not re.fullmatch(r"[0-9a-f]{40}",item["upstreamBlobSha1"])
            or item["phase"] not in PHASES or item["mode"]!="read-only-pattern-reference"
            or item["autoActivate"] is not False or item["executionAllowed"] is not False):
            raise ValueError("E39G12B4C_UNSAFE_OR_DUPLICATED_SOURCE")
        found[path]=item["phase"]
    if set(found)!=set(PATHS):
        raise ValueError("E39G12B4C_SOURCE_MANIFEST_INCOMPLETE")
    branches=index.get("branchesSampled")
    if (type(branches) is not list or len(branches)!=6
        or len(set(branches))!=6 or index.get("branchListingCount")!=536
        or index.get("reviewedMainTreeItems")!=4249):
        raise ValueError("E39G12B4C_RESEARCH_PROVENANCE_DRIFT")
    disallowed=index.get("unsafeIntegrationRejected")
    if type(disallowed) is not list or len(disallowed)<5:
        raise ValueError("E39G12B4C_NO_UNTRUSTED_EXECUTION_DENYLIST")
    result={"evidenceClass":"E39G12B4C_PINNED_EXTERNAL_READ_ONLY_REFERENCES",
        "upstream":UPSTREAM,"upstreamCommit":COMMIT,"patterns":len(found),
        "phases":sorted(set(found.values())),"license":"MIT",
        "branchInventoryWasCompletelyCodeReviewed":False,
        "thirdPartyCodeInstalled":False,"thirdPartyCodeExecuted":False,
        "modelCalls":0,"ownerApprovalGranted":False,"stage45Go":False}
    return result


def evaluate_cinematic(observations:list[dict])->dict:
    """Assess asserted browser receipts as DATA only, NEVER certify release.

    Only a trusted future independent browser harness can upgrade to PASS.
    This guard never claims actual 3D performance from caller-owned JSON.
    """
    if type(observations) is not list or len(observations)!=2:
        raise ValueError("E39G12B4C_EXACT_TWO_VIEWPORT_OBSERVATIONS")
    seen={}
    for item in observations:
        if type(item) is not dict or set(item)!=EVIDENCE_FIELDS:
            raise ValueError("E39G12B4C_EVIDENCE_SCHEMA_WRONG")
        vp=item["viewport"]
        if vp not in ("mobile","desktop") or vp in seen:
            raise ValueError("E39G12B4C_UNREVIEWED_VIEWPORT")
        if (item["width"],item["height"])!=({"mobile":(390,844),"desktop":(1280,800)}[vp]):
            raise ValueError("E39G12B4C_VIEWPORT_DIMENSIONS_DRIFT")
        if item["ownerApprovalGranted"] is not False or item["productionPublished"] is not False:
            raise ValueError("E39G12B4C_FORGED_OWNER_OR_DEPLOYMENT")
        if item["modelCalls"]!=0 or item["independentVerifier"] is not False:
            raise ValueError("E39G12B4C_UNSUPPORTED_ATTESTATION")
        if not isinstance(item["sourceSha256"],str) or not re.fullmatch(r"[0-9a-f]{64}",item["sourceSha256"]):
            raise ValueError("E39G12B4C_SOURCE_HASH_INVALID")
        seen[vp]=item
    if seen["mobile"]["site"]!=seen["desktop"]["site"]:
        raise ValueError("E39G12B4C_CROSS_PROJECT_MISMATCH")
    missing=[]
    requirements={
        "3d-authenticity":"real browser GPU/WebGL rendered scene + credible asset provenance",
        "scroll-kinematics":"position-dependent scene/camera transforms sampled in actual Chromium",
        "motion-accessibility":"tested prefers-reduced-motion mode and keyboard fallback",
        "device-responsiveness":"tested mobile/desktop geometry without overflow",
        "visual-quality":"independent real before/after visual QA by competent reviewer",
        "performance":"measured performance budgets and no browser crashes",
        "security":"CSP, dependency review and independently checked no untrusted script sinks",
        "model-capability":"successful original model-authored accepted fixes across held-out sites",
    }
    for gate,reason in requirements.items():
        missing.append({"gate":gate,"requiredEvidence":reason})
    return {"evidenceClass":"E39G12B4C_CINEMATIC_LUXURY_UNVERIFIED_PREVIEW",
        "site":seen["mobile"]["site"],"observedViewports":2,
        "requirements":requirements,"unverifiedGates":missing,
        "autonomousCinematicWebsiteCapabilityDemonstrated":False,
        "independentSiteQualityCertified":False,"readyToDeploy":False,
        "stage45Go":False,
        "reason":"Untrusted JSON observations cannot attest live 3D/scroll/browser behavior."}


def main():
    if len(sys.argv)!=2 or sys.argv[1]!="validate":
        raise ValueError("E39G12B4C_READ_ONLY_VALIDATE_ONLY")
    print("E39G12B4C_GSTACK_READ_ONLY_CATALOG_VALID",
          json.dumps(validate_index(safe_load(INDEX)),sort_keys=True))


if __name__=="__main__":main()
