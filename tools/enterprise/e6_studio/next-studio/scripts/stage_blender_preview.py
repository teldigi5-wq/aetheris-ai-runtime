#!/usr/bin/env python3
"""Stage the verified Blender-only research GLB into an isolated Next static preview.

No network, no external assets, no arbitrary input paths, no silent overwrite.
This only runs on explicit research CI. Default builds do not include the binary.
"""
from __future__ import annotations

import hashlib
import json
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
APP = HERE.parent
ROOT = HERE.parents[4]
BLENDER = APP / "blender"
EVIDENCE = ROOT / "target" / "e6-studio" / "blender-proof"
DEST = APP / "public" / "e6-blender-preview.glb"
PROOF = ROOT / "target" / "e6-studio" / "blender-preview-stage.json"
sys.path.insert(0, str(BLENDER))
from validate_blender_glb import inspect_glb


def verified_stage() -> dict:
    if any(p.is_symlink() for p in (
        ROOT / "target", ROOT / "target" / "e6-studio", EVIDENCE,
        APP / "public", DEST, PROOF
    )):
        raise ValueError("E6_PREVIEW_SYMLINK_DENIED")
    if DEST.exists() or PROOF.exists():
        raise ValueError("E6_PREVIEW_REFUSE_REPLACE")
    baseline = EVIDENCE / "aetheris-command-module.glb"
    optimized = EVIDENCE / "aetheris-command-module-no-uv.glb"
    blend = EVIDENCE / "aetheris-command-module.blend"
    manifest = EVIDENCE / "aetheris-command-module.manifest.json"
    report_path = EVIDENCE / "asset-size-comparison.json"
    for path in (baseline, optimized, blend, manifest, report_path):
        if path.is_symlink() or not path.is_file():
            raise ValueError("E6_PREVIEW_EVIDENCE_MISSING_OR_SYMLINK")
    data = json.loads(manifest.read_text(encoding="utf-8"))
    report = json.loads(report_path.read_text(encoding="utf-8"))
    original_bytes = baseline.read_bytes()
    optimized_bytes = optimized.read_bytes()
    original_sha = hashlib.sha256(original_bytes).hexdigest()
    optimized_sha = hashlib.sha256(optimized_bytes).hexdigest()
    author_sha = hashlib.sha256((BLENDER / "author_command_module.py").read_bytes()).hexdigest()
    if (data.get("blenderSourceSha256") != hashlib.sha256(blend.read_bytes()).hexdigest()
        or data.get("authoringScriptSha256") != author_sha
        or data.get("sha256") != original_sha
        or data.get("assetApproved") is not False
        or data.get("stage45Go") is not False
        or report.get("kind") != "E6_BLENDER_NO_UV_OFFLINE_SIZE_COMPARISON"
        or report.get("candidateSha256") != optimized_sha
        or report.get("originalSha256") != original_sha
        or report.get("trianglePositionNormalDataEqual") is not True
        or report.get("geometryAndMaterialMetadataEqual") is not True
        or report.get("semanticPrimitiveContractsEqual") is not True
        or not isinstance(report.get("candidateVertexCount"), int)
        or not 1 <= report["candidateVertexCount"] <= report.get("vertexCount", 0)
        or report.get("candidateUVAttributeSets") != 0
        or len(optimized_bytes) >= len(original_bytes)
        or report.get("assetAdoptedByR3F") is not False
        or report.get("stage45Go") is not False):
        raise ValueError("E6_PREVIEW_PROVENANCE_OR_GEOMETRY_DENIED")
    geo = inspect_glb(optimized_bytes)
    if (geo["vertexCount"] != report["candidateVertexCount"]
        or geo["materialCount"] != report["materialCount"]
        or geo["meshCount"] != report["meshCount"]):
        raise ValueError("E6_PREVIEW_ASSET_REPORT_DISAGREES")
    DEST.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(optimized, DEST)
    if hashlib.sha256(DEST.read_bytes()).hexdigest() != optimized_sha:
        DEST.unlink(missing_ok=True)
        raise ValueError("E6_PREVIEW_STAGE_DIGEST_MISMATCH")
    proof = {
        "kind": "E6_BLENDER_OPT_IN_RESEARCH_PREVIEW_ONLY",
        "sourceBlenderSha256": data["blenderSourceSha256"],
        "authoringScriptSha256": author_sha,
        "originalGlbSha256": original_sha,
        "previewGlbSha256": optimized_sha,
        "previewGlbBytes": len(optimized_bytes),
        "sourceMeshCount": geo["meshCount"],
        "sourceMaterialCount": geo["materialCount"],
        "sourceVertexCount": geo["vertexCount"],
        "previewRoute": "/blender-preview/",
        "normalRootAssetUntouched": True,
        "candidateOnlyForExplicitBuild": True,
        "humanVisualApproval": False,
        "physicalGpuVerified": False,
        "productionDeployed": False,
        "ownerApproved": False,
        "stage45Go": False,
    }
    PROOF.write_text(json.dumps(proof, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return proof


if __name__ == "__main__":
    result = verified_stage()
    print("E6_BLENDER_SOURCE_VALIDATED_OPT_IN_PREVIEW_ONLY",json.dumps(result,sort_keys=True))
