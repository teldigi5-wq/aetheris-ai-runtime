#!/usr/bin/env python3
"""E3.9G-12B4B independent existing-source locator, NO GOLD ANSWERS.

Consumes only the *three* explicitly reviewed B2 broken-source ZIP members.
Does NOT import evaluator, patch policy or trusted solution fixtures.
Emits exact observed source spans, no replacement value, instructions, model
role, evaluator rules, repair policy, or execution capability.
"""
from __future__ import annotations
import hashlib
import io
import json
from pathlib import Path
import re
import sys
import zipfile

HERE=Path(__file__).resolve().parent
SOURCE_ZIP=HERE.parents[3]/"target"/"e3-factory"/"e39g12b"/"e39g12b2-proposer-broken-only.zip"
# repo root: tools/enterprise/e3_factory -> parents[3] is repo root
OUTPUT_ZIP=HERE.parents[3]/"target"/"e3-factory"/"e39g12b4b"/"broken-source-and-observed-declaration-windows.zip"
INPUTS=("proposer-manifest.json","sites/cityline/index.html","sites/studynest/index.html")
OUTPUTS=("proposer-manifest.json","sites/cityline/index.html",
         "sites/studynest/index.html","observed-declarations.json")
FAULTS={
 "studynest":("keyboard-focus-visibility","responsive-mobile-overflow"),
 "cityline":("reduced-motion","safe-untrusted-text")
}


def sha(raw:bytes)->str:return hashlib.sha256(raw).hexdigest()


def one(pattern:str,text:str,label:str)->str:
    matches=list(re.finditer(pattern,text))
    if len(matches)!=1:raise ValueError("E39G12B4B_SOURCE_WINDOW_NOT_UNIQUE_"+label)
    result=matches[0].group(1) if matches[0].lastindex else matches[0].group(0)
    if not 1<=len(result)<=180 or "\n" in result or "\r" in result:
        raise ValueError("E39G12B4B_SOURCE_WINDOW_UNBOUNDED_"+label)
    if text.count(result)!=1:
        raise ValueError("E39G12B4B_SOURCE_WINDOW_AMBIGUOUS_"+label)
    return result


def extract(site:str,fault:str,broken:bytes)->str:
    if site not in FAULTS or fault not in FAULTS[site]:
        raise ValueError("E39G12B4B_UNREVIEWED_SITE_OR_FAULT")
    text=broken.decode("utf-8","strict")
    if len(text)>14000:
        raise ValueError("E39G12B4B_SOURCE_TOO_LONG")
    if (site,fault)==("studynest","keyboard-focus-visibility"):
        return one(r"\.skip:focus\{[^{}\n]{1,160}\}",text,"skip")
    if (site,fault)==("studynest","responsive-mobile-overflow"):
        anchor="@media(max-width:680px)"
        if text.count(anchor)!=1:raise ValueError("E39G12B4B_MOBILE_MEDIA_AMBIGUOUS")
        return one(r"\.schedule-grid\{([^{}\n]{1,160})\}",
                   text[text.index(anchor):],"responsive")
    if (site,fault)==("cityline","reduced-motion"):
        return one(r"@media\(prefers-reduced-motion:reduce\)\{\.ticker\{[^{}\n]{1,160}\}\}",
                   text,"motion")
    if (site,fault)==("cityline","safe-untrusted-text"):
        return one(r"function render\(name\)\{(output\.[A-Za-z]{3,24}\s*=\s*[^;\n]{1,130};)\}",
                   text,"literal")
    raise ValueError("E39G12B4B_NO_SOURCE_PATTERN")


def produce(raw_bundle:bytes)->tuple[bytes,dict]:
    if type(raw_bundle) is not bytes or len(raw_bundle)>26000:
        raise ValueError("E39G12B4B_OVERSIZE_INPUT_ZIP")
    with zipfile.ZipFile(io.BytesIO(raw_bundle)) as source:
        if sorted(source.namelist())!=sorted(INPUTS):
            raise ValueError("E39G12B4B_UNEXPECTED_INPUT_MEMBER")
        entries={name:source.read(name) for name in INPUTS}
    manifest=json.loads(entries["proposer-manifest.json"])
    if (manifest.get("evidenceClass")!="E39G12B2_MODEL_FACING_ONLY_BROKEN_PUBLIC_SYNTHETIC"
        or manifest.get("referenceVariantsIncluded") is not False
        or manifest.get("goldenReplacementIncluded") is not False
        or set(manifest.get("sites",{}))!=set(FAULTS)):
        raise ValueError("E39G12B4B_UNVERIFIED_BROKEN_ONLY_BUNDLE")
    observations={}
    for site,faults in FAULTS.items():
        broken=entries["sites/"+site+"/index.html"]
        if (len(broken)>14000 or sha(broken)!=manifest["sites"][site]["sha256"]):
            raise ValueError("E39G12B4B_BROKEN_SOURCE_HASH_NOT_TRUSTED")
        for fault in faults:
            prior=extract(site,fault,broken)
            observations[site+"--"+fault]={
                "site":site,"fault":fault,"sourceSha256":sha(broken),
                "existingSourceSpan":prior,"existingSpanSha256":sha(prior.encode()),
                "hintType":"observed-existing-source-only",
                "proposedReplacement":None,
                "ownerApprovalGranted":False,
            }
    note={"evidenceClass":"E39G12B4B_SOURCE_SPANS_FROM_BROKEN_ONLY",
        "observations":observations,"modelCalls":0,
        "referenceInputsAvailable":False,"replacementAnswersProvided":False,
        "modelCodeExecuted":False,"productionPublished":False}
    entries["observed-declarations.json"]=(json.dumps(note,sort_keys=True,indent=2)+"\n").encode()
    buff=io.BytesIO()
    with zipfile.ZipFile(buff,"w",compression=zipfile.ZIP_DEFLATED) as target:
        for name in sorted(OUTPUTS):
            info=zipfile.ZipInfo(name,(2020,1,1,0,0,0))
            info.compress_type=zipfile.ZIP_DEFLATED
            info.external_attr=0o100600<<16
            target.writestr(info,entries[name])
    output=buff.getvalue()
    with zipfile.ZipFile(io.BytesIO(output)) as check:
        if sorted(check.namelist())!=sorted(OUTPUTS):
            raise ValueError("E39G12B4B_EXTRA_MODEL_INPUT")
        for name in INPUTS:
            if check.read(name)!=entries[name]:
                raise ValueError("E39G12B4B_PREEXISTING_BROKEN_INPUT_CHANGED")
        payload=json.loads(check.read("observed-declarations.json"))
        if len(payload.get("observations",{}))!=4:
            raise ValueError("E39G12B4B_NOT_FOUR_SOURCE_WINDOWS")
        for token,row in payload["observations"].items():
            if row["existingSourceSpan"].encode() not in entries["sites/"+row["site"]+"/index.html"]:
                raise ValueError("E39G12B4B_SOURCE_HINT_NOT_ORIGINAL")
            if row["proposedReplacement"] is not None:
                raise ValueError("E39G12B4B_ANSWER_LEAKED")
    return output,note


def pack()->None:
    if (SOURCE_ZIP.is_symlink() or not SOURCE_ZIP.is_file()
        or OUTPUT_ZIP.exists() or OUTPUT_ZIP.is_symlink()):
        raise ValueError("E39G12B4B_INPUT_MISSING_OR_OUTPUT_REPLAY")
    content,note=produce(SOURCE_ZIP.read_bytes())
    OUTPUT_ZIP.parent.mkdir(parents=True,exist_ok=True)
    with OUTPUT_ZIP.open("xb") as f:f.write(content)
    print("E39G12B4B_BROKEN_SOURCE_WINDOWS_ZIP_VERIFIED",json.dumps({
        "siteCount":2,"faultCount":len(note["observations"]),"modelCalls":0,
        "sha256":sha(content)},sort_keys=True))


if __name__=="__main__":
    if len(sys.argv)!=2 or sys.argv[1]!="pack":
        raise ValueError("E39G12B4B_FIXED_PACK_ONLY")
    pack()
