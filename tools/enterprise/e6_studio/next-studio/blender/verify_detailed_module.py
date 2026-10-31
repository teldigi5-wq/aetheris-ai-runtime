#!/usr/bin/env python3
"""Fail-closed evidence for the E6 enhanced original Blender command module.

No visual acceptance, GPU performance, commercial design rights or deployment
is inferred from these geometry/PBR/provenance gates.
"""
from __future__ import annotations

import hashlib
import json
import struct
from pathlib import Path

from validate_blender_glb import inspect_glb
from compare_glb_variants import describe

ROOT = Path(__file__).resolve().parents[5]
BASE = ROOT / "target/e6-studio/blender-proof"
DETAILS = {
    "Governance bezel",
    "Runtime core",
    "Side spine",
    "Lower signature bus",
}
MATERIAL_NAMES = {
    "Brushed alloy", "Graphite inset", "Machined connectors",
    "Governance indicator", "Smoked ceramic core", "Machined warm bronze",
}


def inspect_design(raw: bytes) -> dict:
    inspect_glb(raw)
    length, kind = struct.unpack_from("<I4s", raw, 12)
    if kind != b"JSON":
        raise ValueError("E6_DETAIL_BAD_GLB_CHUNKS")
    model = json.loads(raw[20:20 + length].rstrip(b" "))
    meshes = model["meshes"]
    materials = model["materials"]
    if len(meshes) != 12 or len(materials) != 6:
        raise ValueError("E6_DETAIL_MISSING_DESIGN_PARTS")
    # Blender glTF mesh datablocks use Cube/Torus/Cylinder names; authored
    # part names are preserved on scene graph nodes, not mesh data objects.
    names = [x.get("name", "") for x in model.get("nodes", [])]
    mats = {x.get("name", "") for x in materials}
    if not all(any(fragment in name for name in names) for fragment in DETAILS):
        raise ValueError("E6_DETAIL_MISSING_GOVERNANCE_PART")
    if not MATERIAL_NAMES.issubset(mats):
        raise ValueError("E6_DETAIL_MISSING_PBR_FINISH")
    if model.get("textures") or model.get("images") or model.get("extensionsUsed") or model.get("extensionsRequired"):
        raise ValueError("E6_DETAIL_UNREVIEWED_MEDIA_OR_EXTENSION")
    data = describe(raw)
    if not 756 < data["triangleCount"] <= 5500:
        raise ValueError("E6_DETAIL_TRIANGLE_BUDGET")
    if not 1512 < data["validation"]["vertexCount"] <= 8000:
        raise ValueError("E6_DETAIL_VERTEX_BUDGET")
    return {
        "kind": "E6_OWNED_DETAILED_CONCEPT_GEOMETRY",
        "meshCount": len(meshes),
        "materialCount": len(materials),
        "triangleCount": data["triangleCount"],
        "vertexCount": data["validation"]["vertexCount"],
        "names": names,
        "materialNames": sorted(mats),
        "rawBytes": len(raw),
        "sha256": hashlib.sha256(raw).hexdigest(),
        "visualApproval": False,
        "physicalGpuMeasured": False,
        "ownerApproved": False,
        "stage45Go": False,
    }


def main() -> None:
    original = BASE / "aetheris-command-module.glb"
    optimized = BASE / "aetheris-command-module-no-uv.glb"
    comp = BASE / "asset-size-comparison.json"
    manifest = BASE / "aetheris-command-module.manifest.json"
    report = BASE / "enhanced-module-detail.json"
    source = Path(__file__).with_name("author_command_module.py")
    blend = BASE / "aetheris-command-module.blend"
    if any(path.is_symlink() for path in (original, optimized, comp, manifest, report, source, blend)):
        raise ValueError("E6_DETAIL_SYMLINK_DENIED")
    if report.exists():
        raise ValueError("E6_DETAIL_NO_EVIDENCE_OVERWRITE")
    provenance = json.loads(manifest.read_text(encoding="utf-8"))
    comparison = json.loads(comp.read_text(encoding="utf-8"))
    raw = original.read_bytes()
    candidate = optimized.read_bytes()
    if (provenance.get("sha256") != hashlib.sha256(raw).hexdigest()
        or provenance.get("blenderSourceSha256") != hashlib.sha256(blend.read_bytes()).hexdigest()
        or provenance.get("authoringScriptSha256") != hashlib.sha256(source.read_bytes()).hexdigest()
        or comparison.get("originalSha256") != hashlib.sha256(raw).hexdigest()
        or comparison.get("candidateSha256") != hashlib.sha256(candidate).hexdigest()
        or comparison.get("trianglePositionNormalDataEqual") is not True
        or comparison.get("geometryAndMaterialMetadataEqual") is not True
        or comparison.get("candidateUVAttributeSets") != 0
        or comparison.get("stage45Go") is not False
        or provenance.get("stage45Go") is not False):
        raise ValueError("E6_DETAIL_PROVENANCE_OR_PARITY_DENIED")
    before, after = inspect_design(raw), inspect_design(candidate)
    for field in ("meshCount", "materialCount", "triangleCount", "names", "materialNames"):
        if before[field] != after[field]:
            raise ValueError("E6_DETAIL_CANDIDATE_DIFFERED")
    # Vertex stream lengths may shrink after removing unused UV seam splits;
    # canonical ordered position+normal triangle data is verified separately.
    if after["vertexCount"] > before["vertexCount"]:
        raise ValueError("E6_DETAIL_CANDIDATE_NEW_VERTICES")
    before.update({
        "optimizedSha256": after["sha256"],
        "optimizedBytes": after["rawBytes"],
        "optimizedExportVertexCount": after["vertexCount"],
        "unusedUvSeamVertexCopiesRemoved": before["vertexCount"] - after["vertexCount"],
        "rawBytesSavedByOmittingUnusedUV": len(raw)-len(candidate),
        "provenanceVerified": True,
        "equalTrianglePositionsNormals": True,
        "unreviewedTextures": False,
        "candidateAdoptedByProduction": False,
    })
    if before["rawBytesSavedByOmittingUnusedUV"] <= 0:
        raise ValueError("E6_DETAIL_NO_OPTIMIZATION")
    report.write_text(json.dumps(before, indent=2, sort_keys=True)+"\n", encoding="utf-8")
    print("E6_DETAILED_ORIGINAL_BLENDER_CONCEPT_PROOF_PASS",json.dumps(before,sort_keys=True))


if __name__ == "__main__":
    main()
