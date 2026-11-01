#!/usr/bin/env python3
"""E6 W3-B: structurally inspect first-party Signal Fold GLB and bind source hashes.
Trust boundary: this validates known first-party generator output only; it is
not an ingestion validator for arbitrary external 3D models or proof of rights.
"""
from __future__ import annotations
import hashlib
import json
import struct
import sys
from pathlib import Path

HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE/"blender"))
from validate_blender_glb import inspect_glb  # noqa: E402

ROOT=HERE.parents[3]
OUTPUT=ROOT/"target/e6-studio/w3b-signal-fold"
SCRIPT=HERE/"blender/w3b_author_signal_fold.py"
FILE="arcloom-signal-fold"
FLAGS=("modelGenerated","physicalPrototype","clientApproved","visualApproved",
       "licenseIndependentlyCertified","mobileGpuMeasured","productionAsset",
       "ownerApproved","stage45Go","releaseGo")

def fail(reason):
    raise ValueError("E6_W3B_"+reason)
def sha(raw):
    return hashlib.sha256(raw).hexdigest()
def regular(file,limit):
    if file.is_symlink() or not file.is_file() or file.stat().st_size>limit:
        fail("UNTRUSTED_FILE")
    raw=file.read_bytes()
    if not raw or len(raw)>limit:
        fail("INVALID_FILE_SIZE")
    return raw
def validate(script:bytes,blend:bytes,glb:bytes,manifest:dict):
    if not isinstance(manifest,dict):
        fail("MANIFEST_SHAPE")
    proof=inspect_glb(glb)
    if (manifest.get("kind")!="W3B_SIGNAL_FOLD_CONCEPT_V1" or
        manifest.get("fictionalStudy")!="Signal Fold" or
        manifest.get("site")!="Arcloom Objects" or
        manifest.get("licenseBasis")!="first-party-source-subject-to-owner-review" or
        manifest.get("sourceLocked") is not True or
        manifest.get("externalMedia") is not False or
        manifest.get("unverifiedExtensions") is not False or
        any(manifest.get(k) is not False for k in FLAGS)):
        fail("INCOMPLETE_OR_FORGED_EVIDENCE")
    if (manifest.get("sourceScriptSha256")!=sha(script) or
        manifest.get("blenderProjectSha256")!=sha(blend) or
        manifest.get("glbSha256")!=sha(glb) or
        manifest.get("glbBytes")!=len(glb) or
        manifest.get("meshCount")!=7 or
        manifest.get("materialCount")!=5 or
        manifest.get("vertexCount")!=proof["vertexCount"] or
        proof["meshCount"]!=7 or proof["materialCount"]!=5):
        fail("SOURCE_OR_GEOMETRY_MISMATCH")
    json_len,kind=struct.unpack_from("<I4s",glb,12)
    if kind!=b"JSON":
        fail("JSON_CHUNK")
    data=json.loads(glb[20:20+json_len].rstrip(b" "))
    if (data.get("images") or data.get("textures") or
        data.get("extensionsRequired") or data.get("extensionsUsed") or
        data.get("animations") or data.get("skins") or data.get("cameras") or
        len(data.get("nodes",[]))<7):
        fail("UNREVIEWED_MODEL_CONTENT")
    names=[n.get("name","") for n in data["nodes"]]
    for prefix in ("01 / Porcelain foundation","02 / Folded alloy lower wing",
                   "03 / Inclined graphite fold","04 / Upper alloy wing",
                   "05 / Lacquered signature line","06 / Inlaid porcelain index",
                   "07 / Elastomer anti-slip underside"):
        if not any(name.startswith(prefix) for name in names):
            fail("MISSING_BRAND_SPECIFIC_OBJECT")
    materials={m.get("name") for m in data["materials"]}
    expected={
        "Signal / bead-blasted titanium","Signal / graphite anodized shell",
        "Signal / warm porcelain base","Signal / vermilion lacquer detail",
        "Signal / matte anti-slip insert"}
    if materials!=expected or set(manifest.get("materialNames",[]))!=expected:
        fail("MATERIAL_MISMATCH")
    for mat in data["materials"]:
        pbr=mat.get("pbrMetallicRoughness",{})
        if (not 0<=pbr.get("metallicFactor",-1)<=1 or
            not 0<=pbr.get("roughnessFactor",-1)<=1 or
            pbr.get("baseColorTexture") or
            mat.get("normalTexture")):
            fail("MATERIAL_PHYSICAL_BOUNDS")
    return {
        "kind":"E6_W3B_SOURCE_VERIFIED_SIGNAL_FOLD_ASSET_V1",
        "brand":"Arcloom Objects","fictionalStudy":"Signal Fold",
        "sourceScriptSha256":sha(script),
        "blenderProjectSha256":sha(blend),
        "glbSha256":sha(glb),"glbBytes":len(glb),
        "meshCount":7,"materialCount":5,"vertexCount":proof["vertexCount"],
        "sourceAndGeometryIntegrityVerified":True,
        "sourceClaim":"FIRST_PARTY_REPO_SCRIPT_OWNER_REVIEW_PENDING",
        "modelGenerated":False,"externalMedia":False,"visualApproved":False,
        "licenseIndependentlyCertified":False,"physicalPrototype":False,
        "physicalGpuMeasured":False,"ownerApproved":False,
        "deploymentAuthorized":False,"releaseGo":False,"stage45Go":False
    }
def load():
    if OUTPUT.is_symlink() or not OUTPUT.is_dir():
        fail("SOURCE_DIRECTORY")
    source=regular(SCRIPT,100000)
    blend=regular(OUTPUT/(FILE+".blend"),5_000_000)
    glb=regular(OUTPUT/(FILE+".glb"),2_000_000)
    manifest=json.loads(regular(OUTPUT/(FILE+".manifest.json"),50_000))
    return source,blend,glb,manifest

if __name__=="__main__":
    if len(sys.argv)!=1:
        fail("NO_USER_ARGS")
    print("E6_W3B_REAL_BLENDER_PROVENANCE_PASS "+json.dumps(validate(*load()),sort_keys=True))
