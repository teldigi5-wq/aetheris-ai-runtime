#!/usr/bin/env python3
"""W3-B: stage first-party, source-verified Signal Fold concept asset into a new
write-once local website. W3-A and W2-C previews remain byte-for-byte intact.
This route DOES NOT render GLB (rendering belongs to W4).
"""
from __future__ import annotations
import json
import shutil
import sys
from pathlib import Path

HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE))
from w3a_stage_provenance import ensure_original_files
from w3b_verify_signal_fold import validate,load

SOURCE=HERE/"target/e6-w3a-arcloom"
DEST=HERE/"target/e6-w3b-arcloom"
DETAIL="studies/signal-fold/index.html"

def fail(message):
    raise ValueError("E6_W3B_"+message)

def stage():
    if DEST.exists() or DEST.is_symlink():
        fail("NO_OVERWRITE")
    if SOURCE.is_symlink() or not SOURCE.is_dir():
        fail("INVALID_W3A_SOURCE")
    ensure_original_files(SOURCE)
    prev=SOURCE/"assets/w3a-provenance.json"
    if prev.is_symlink() or not prev.is_file():
        fail("MISSING_W3A_PROVENANCE")
    prior=json.loads(prev.read_bytes())
    if (prior.get("kind")!="E6_W3A_VERIFIED_LOCAL_BLENDER_PROVENANCE_V1" or
        prior.get("releaseGo") is not False or
        prior.get("stage45Go") is not False):
        fail("INVALID_W3A_EVIDENCE")
    source,blend,glb,manifest=load()
    proof=validate(source,blend,glb,manifest)
    original=SOURCE/DETAIL
    if original.is_symlink() or not original.is_file():
        fail("MISSING_CASE_STUDY")
    html=original.read_text(encoding="utf-8")
    if (html.count("</main>")!=1 or
        html.count('data-w2c-study="signal-fold"')!=1 or
        '<meta name="robots" content="noindex,nofollow">' not in html):
        fail("UNEXPECTED_PAGE_SHAPE")
    shutil.copytree(SOURCE,DEST,symlinks=False)
    asset_dir=DEST/"assets"
    (asset_dir/"arcloom-signal-fold.glb").write_bytes(glb)
    (asset_dir/"w3b-signal-fold-provenance.json").write_text(
        json.dumps(proof,sort_keys=True,indent=2)+"\n",encoding="utf-8")
    markup=(
       '<aside class="signal-fold-proof" aria-label="Original 3D research asset">'
       '<h2>Original concept geometry</h2>'
       '<p>A separate human-authored Blender study of the fictional Signal Fold '
       'object is available for research. It is not a manufactured or independently '
       'approved product. Browser 3D rendering and camera animation belong to W4.</p>'
       '<p><a href="/assets/arcloom-signal-fold.glb" download>'
       'Download first-party concept GLB</a> '
       '<a href="/assets/w3b-signal-fold-provenance.json" download>'
       'Inspect source integrity evidence</a></p></aside>')
    (DEST/DETAIL).write_text(html.replace("</main>",markup+"</main>"),encoding="utf-8")
    proof["previewRoot"]="target/e6-w3b-arcloom"
    proof["w3aPreviewUnchanged"]=True
    proof["renderedInSite"]=False
    return proof

if __name__=="__main__":
    if len(sys.argv)!=1:
        fail("NO_ARBITRARY_ARGUMENTS")
    print("E6_W3B_BRAND_ASSET_STAGED_NO_WEBGL "+
          json.dumps(stage(),sort_keys=True))
