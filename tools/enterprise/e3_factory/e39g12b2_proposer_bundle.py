#!/usr/bin/env python3
"""E3.9G-12B2 model-facing ZIP contains broken site HTML ONLY and public faults.

This producer runs in evaluator CI. The eventual Ollama process must run
in a DIFFERENT job without source checkout, receiving ONLY this ZIP.
The ZIP itself is NOT a secret or a blinded benchmark: all examples
are public synthetic; the developer-authored reference source is
explicitly excluded from the exported bytes.
"""
from __future__ import annotations
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import sys
import zipfile

import e39g12b_two_site_corpus as source

BUNDLE_NAME="e39g12b2-proposer-broken-only.zip"
PUBLIC_FAULTS={
    "studynest":("offscreen keyboard skip link","horizontal overflow on mobile"),
    "cityline":("ticker ignores reduced motion","station input rendered as HTML"),
}
EXPLICIT_EXPORTS=("sites/studynest/index.html","sites/cityline/index.html",
                  "proposer-manifest.json")


def sha(raw:bytes)->str: return hashlib.sha256(raw).hexdigest()


def pack()->dict:
    base=source.BASE
    if (base.is_symlink() or (base/"sites").is_symlink()
        or not (base/"corpus-manifest.json").is_file()
        or (base/BUNDLE_NAME).exists() or (base/BUNDLE_NAME).is_symlink()):
        raise ValueError("E39G12B2_MISSING_OR_DUPLICATE_EVALUATOR_CORPUS")
    original=source.checked(base/"corpus-manifest.json",7000)
    manifest=json.loads(original)
    if (manifest.get("evidenceClass")!="E39G12B_TWO_DISTINCT_PUBLIC_SYNTHETIC_WEBSITES"
        or manifest.get("noModelPromptsOrCalls") is not True
        or set(manifest.get("sites",{}))!=set(PUBLIC_FAULTS)):
        raise ValueError("E39G12B2_CORPUS_NOT_CERTIFIED")
    entries={}
    rejects=[]
    for site in PUBLIC_FAULTS:
        broken_path=base/"sites"/site/"broken"/"index.html"
        ref_path=base/"sites"/site/"reference"/"index.html"
        broken=source.checked(broken_path,14000)
        reference=source.checked(ref_path,14000)
        hashes=manifest["sites"][site]["variants"]
        if (sha(broken)!=hashes["broken"]["sourceSha256"]
            or sha(reference)!=hashes["reference"]["sourceSha256"]
            or broken==reference):
            raise ValueError("E39G12B2_SOURCE_DIGEST_OR_GOLD_DRIFT")
        entries["sites/"+site+"/index.html"]=broken
        rejects.append((site,reference))
    public_manifest={
        "evidenceClass":"E39G12B2_MODEL_FACING_ONLY_BROKEN_PUBLIC_SYNTHETIC",
        "purpose":"read-only adviser input, NOT execution authority",
        "sites":{site:{"htmlPath":"sites/"+site+"/index.html",
            "sha256":sha(entries["sites/"+site+"/index.html"]),
            "faultObservations":PUBLIC_FAULTS[site]}
            for site in PUBLIC_FAULTS},
        "referenceVariantsIncluded":False,
        "goldenReplacementIncluded":False,
        "modelCalls":0,"modelPatchApplied":False,
        "ownerApprovalGranted":False,"productionPublished":False,
        "externallyCurated":False,"blindHeldOut":False,
    }
    entries["proposer-manifest.json"]=(
        json.dumps(public_manifest,sort_keys=True,separators=(",",":"))+"\n").encode()
    assert tuple(sorted(entries))==tuple(sorted(EXPLICIT_EXPORTS))
    data=io.BytesIO()
    with zipfile.ZipFile(data,"w",compression=zipfile.ZIP_DEFLATED,compresslevel=9) as arc:
        for name in sorted(entries):
            assert str(PurePosixPath(name))==name and ".." not in PurePosixPath(name).parts
            info=zipfile.ZipInfo(name,date_time=(2020,1,1,0,0,0))
            info.compress_type=zipfile.ZIP_DEFLATED
            info.external_attr=0o100600<<16
            arc.writestr(info,entries[name],compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
    blob=data.getvalue()
    if len(blob)>20000:raise ValueError("E39G12B2_OVERSIZE_PROPOSER_ARCHIVE")
    with zipfile.ZipFile(io.BytesIO(blob)) as arc:
        if sorted(arc.namelist())!=sorted(EXPLICIT_EXPORTS):
            raise ValueError("E39G12B2_GOLD_ENTRY_OR_UNREVIEWED_FILE")
        for site,reference in rejects:
            # Full reference bytes must not be present in exported files.
            if any(reference==arc.read(name) for name in arc.namelist()):
                raise ValueError("E39G12B2_REFERENCE_LEAK")
            if arc.read("sites/"+site+"/index.html")==reference:
                raise ValueError("E39G12B2_REFERENCE_EXPOSED")
        if any(("reference" in name or "gold" in name)
               for name in arc.namelist()):
            raise ValueError("E39G12B2_UNSAFE_MODEL_ARCHIVE_NAME")
    dest=base/BUNDLE_NAME
    with dest.open("xb") as fp:fp.write(blob)
    receipt={
        "evidenceClass":"E39G12B2_EXPLICIT_BROKEN_ONLY_ARCHIVE",
        "sha256":sha(blob),"archiveBytes":len(blob),
        "exactPaths":sorted(entries),
        "sourceSiteHashes":{site:sha(entries["sites/"+site+"/index.html"])
                            for site in PUBLIC_FAULTS},
        "modelCalls":0,"goldReferenceExported":False,
        "sourceRepositoryCheckoutRequiredForConsumer":False,
        "externallyCurated":False,"ownerApprovalGranted":False,
        "productionPublished":False,
    }
    (base/"proposer-export-receipt.json").write_text(
        json.dumps(receipt,sort_keys=True,indent=2)+"\n")
    print("E39G12B2_BROKEN_ONLY_ZIP_VERIFIED",json.dumps({
        "paths":receipt["exactPaths"],"sha256":receipt["sha256"],
        "gold":False,"modelCalls":0},sort_keys=True))
    return receipt


if __name__=="__main__":
    if len(sys.argv)!=2 or sys.argv[1]!="pack":
        raise ValueError("E39G12B2_FIXED_PACK_ONLY")
    pack()
