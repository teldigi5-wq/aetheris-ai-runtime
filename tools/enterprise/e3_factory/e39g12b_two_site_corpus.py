#!/usr/bin/env python3
"""E3.9G-12B1 test-only two-site corpus. Golden reference NEVER model input.

Source sites independently written (different DOM, content and behavior).
Materialize two defective sources plus two trusted *evaluator-only* controls
under target/. No privileged actions or network. Not independently curated or
concealed; no model call, policy-approval or model repair occurs at this stage.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys

import worker

HERE=worker.HERE/"fixtures"/"e39g12b"
BASE=worker.REPO/"target"/"e3-factory"/"e39g12b"
SITES={
  "studynest":{
    "substitutions":(
      (".skip:focus{top:-120px;}",".skip:focus{top:12px;}"),
      ("grid-template-columns:1fr;min-width:960px;","grid-template-columns:1fr;min-width:0;"),
    ),
    "faults":("keyboard-focus-visibility","responsive-mobile-overflow")
  },
  "cityline":{
    "substitutions":(
      ("@media(prefers-reduced-motion:reduce){.ticker{animation:floatline 4s linear infinite;}}",
       "@media(prefers-reduced-motion:reduce){.ticker{animation:none;}}"),
      ("output.innerHTML = 'Station: '+name;","output.textContent = 'Station: '+name;"),
    ),
    "faults":("reduced-motion","safe-untrusted-text")
  }
}


def sha(raw:bytes)->str:
    return hashlib.sha256(raw).hexdigest()


def checked(path:Path,max_bytes:int)->bytes:
    if path.is_symlink() or not path.is_file() or not 0<path.stat().st_size<=max_bytes:
        raise ValueError("E39G12B_SOURCE_OR_FILE_UNSAFE")
    return path.read_bytes()


def corpus()->dict:
    if HERE.is_symlink() or BASE.is_symlink() or BASE.exists() or (BASE.parent).is_symlink():
        raise ValueError("E39G12B_CORPUS_EXISTS_OR_SYMLINK")
    originals={}
    for name,entry in SITES.items():
        src=checked(HERE/name/"index.html",14000)
        text=src.decode("utf-8")
        if len(text)>13000 or "<!doctype html>" not in text or not text.rstrip().endswith("</html>"):
            raise ValueError("E39G12B_INVALID_SOURCE")
        ref=text
        for old,new in entry["substitutions"]:
            if ref.count(old)!=1 or new in ref:
                raise ValueError("E39G12B_CONTROL_FIX_DRIFT_OR_AMBIGUOUS")
            ref=ref.replace(old,new,1)
        if ref==text:
            raise ValueError("E39G12B_CONTROL_DID_NOT_CHANGE_ANYTHING")
        originals[name]={"broken":src,"reference":ref.encode("utf-8")}
    if len({sha(v["broken"]) for v in originals.values()})!=2:
        raise ValueError("E39G12B_SITES_ARE_NOT_DISTINCT")
    BASE.mkdir(parents=True,mode=0o700)
    assets=BASE/"sites"
    assets.mkdir(mode=0o700)
    manifest={"evidenceClass":"E39G12B_TWO_DISTINCT_PUBLIC_SYNTHETIC_WEBSITES",
        "version":1,"sourceRepoOnly":True,"sites":{},
        "noModelPromptsOrCalls":True,"noModelRepairCredit":True,
        "externallyCurated":False,"blindHeldOut":False,
        "ownerAuthenticated":False,"ownerApprovalGranted":False,
        "productionPublished":False}
    for name,variants in originals.items():
        record={"faults":SITES[name]["faults"],"variants":{}}
        for variant,content in variants.items():
            dest=assets/name/variant
            dest.mkdir(parents=True,mode=0o700)
            with (dest/"index.html").open("xb") as fp:
                fp.write(content)
            record["variants"][variant]={"sourceSha256":sha(content),
                "lengthBytes":len(content),"referenceOnly":variant=="reference"}
        manifest["sites"][name]=record
    with (BASE/"corpus-manifest.json").open("x") as fp:
        json.dump(manifest,fp,sort_keys=True,indent=2);fp.write("\n")
    print("E39G12B_NETWORK_NONE_DISTINCT_SITE_CONTROL_CORPUS",json.dumps({
        "sites":list(manifest["sites"]),"variants":["broken","reference"],
        "modelCalls":0,"ownerApproval":False},sort_keys=True))
    return manifest


if __name__=="__main__":
    if len(sys.argv)!=2 or sys.argv[1]!="seed":
        raise ValueError("E39G12B_ONLY_FIXED_SEED")
    corpus()
