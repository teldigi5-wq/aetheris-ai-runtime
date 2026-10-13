#!/usr/bin/env python3
"""Quarantined creative research catalog. Zero network, installs or execution."""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PINNED = {
    "three-js": ("mrdoob/three.js","3d-rendering","MIT"),
    "react-three-fiber": ("pmndrs/react-three-fiber","3d-rendering","MIT"),
    "motion": ("motiondivision/motion","motion-ui","MIT"),
    "gsap": ("greensock/GSAP","motion-ui","NOASSERTION"),
    "storybook": ("storybookjs/storybook","ui-ux-components","MIT"),
    "tailwind": ("tailwindlabs/tailwindcss","ui-ux-components","MIT"),
    "axe-core": ("dequelabs/axe-core","accessibility","MPL-2.0"),
    "playwright": ("microsoft/playwright","accessibility","Apache-2.0"),
    "blender": ("blender/blender","creative-3d-authoring","NOASSERTION"),
    "remotion": ("remotion-dev/remotion","motion-video","NOASSERTION"),
    "figma-samples": ("figma/plugin-samples","design-integration","MIT"),
    "ffmpeg-wasm": ("ffmpegwasm/ffmpeg.wasm","motion-video","MIT"),
}
SCHEMA = {"schemaVersion","catalogKind","snapshotDate","status","ownerApprovalRequired",
          "physicalPcStatus","automaticInstallation","automaticCodeExecution",
          "externalNetworkFetch","paidProvidersEnabled","policyAuthority","sources"}
ENTRY = {"id","repository","url","category","purpose","reportedGithubLicense","licenseApproval",
         "dependencyAndAssetLicensesReviewed","sourceSha","installationStatus","trust",
         "activation","allowedNetworkHosts","requestedCredentials","automaticDownload","executionEnabled"}

def validate(root=ROOT):
    errors=[]
    p=root/"creative-studio"/"sources.json"
    if p.is_symlink() or not p.is_file() or p.stat().st_size>50000:
        return ["creative research catalog missing or oversized"]
    try:
        d=json.loads(p.read_text(encoding="utf-8"))
    except (ValueError,OSError,UnicodeError):
        return ["creative catalog unreadable"]
    if not isinstance(d,dict) or set(d)!=SCHEMA:
        return ["creative catalog schema mismatch"]
    if type(d["schemaVersion"]) is not int or d["schemaVersion"]!=1 or d["catalogKind"]!="AETHERIS_CREATIVE_STUDIO_RESEARCH":
        errors.append("schema identification drift")
    if d["status"]!="STATIC_RESEARCH_ONLY" or d["physicalPcStatus"]!="BLOCKED_PENDING_HARDWARE":
        errors.append("unverified runtime or hardware promotion")
    if d["ownerApprovalRequired"] is not True or d["policyAuthority"]!="existing deterministic owner policy and MCP grants":
        errors.append("owner authority changed")
    for key in ("automaticInstallation","automaticCodeExecution","externalNetworkFetch","paidProvidersEnabled"):
        if d[key] is not False:
            errors.append(f"automatic authority denied: {key}")
    sources=d.get("sources")
    if not isinstance(sources,list) or len(sources)!=len(PINNED):
        return errors+["unapproved or missing candidate count"]
    seen=set()
    for entry in sources:
        if not isinstance(entry,dict) or set(entry)!=ENTRY:
            errors.append("malformed candidate entry")
            continue
        name=entry["id"]
        if not isinstance(name,str) or name not in PINNED:
            errors.append("candidate outside exact curated allowlist")
            continue
        if name in seen:
            errors.append("duplicate candidate")
        seen.add(name)
        repo,cat,license_label=PINNED[name]
        if (entry["repository"],entry["category"],entry["reportedGithubLicense"])!=(repo,cat,license_label):
            errors.append(f"{name}: metadata/substitution mismatch")
        if entry["url"]!="https://github.com/"+repo:
            errors.append(f"{name}: URL substitution")
        if entry["sourceSha"] is not None or entry["licenseApproval"]!="NOT_REVIEWED":
            errors.append(f"{name}: unverified revision or license trust")
        if entry["installationStatus"]!="NOT_INSTALLED" or entry["trust"]!="UNTRUSTED_EXTERNAL" or entry["activation"]!="BLOCKED_REVIEW_ONLY":
            errors.append(f"{name}: external code trust escalation")
        for boolean in ("dependencyAndAssetLicensesReviewed","automaticDownload","executionEnabled"):
            if entry[boolean] is not False:
                errors.append(f"{name}: forbidden activation flag {boolean}")
        if entry["allowedNetworkHosts"]!=[] or entry["requestedCredentials"]!=[]:
            errors.append(f"{name}: unauthorized network/credential grant")
        if not isinstance(entry["purpose"],str) or not 10<=len(entry["purpose"])<=240:
            errors.append(f"{name}: purpose invalid")
    if seen!=set(PINNED):
        errors.append("missing source allowlist entries")
    for p in (root/"creative-studio").rglob("*"):
        if p.is_symlink():
            errors.append("symlink under creative catalog")
        elif p.is_file() and p.relative_to(root/"creative-studio").as_posix() not in {"README.md","sources.json"}:
            errors.append("unapproved executable creative catalog file")
    return errors

if __name__=="__main__":
    issues=validate()
    if issues:
        for issue in issues:
            print("FAIL:",issue,file=sys.stderr)
        raise SystemExit(1)
    print("12 Creative Studio research candidates: reference-only; no activation")
