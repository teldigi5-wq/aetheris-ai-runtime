#!/usr/bin/env python3
"""Compare two actual GLBs from identical original Blender source, offline.

Raw and deterministic gzip sizes are *size estimates*, not network, GPU,
memory, frame-time, visual-parity or production-compression benchmarks.
"""
from __future__ import annotations

import gzip
import hashlib
import json
import struct
import sys
from pathlib import Path

from validate_blender_glb import inspect_glb

ROOT = Path(__file__).resolve().parents[5]
EVIDENCE = ROOT / "target" / "e6-studio" / "blender-proof"


def describe(raw: bytes) -> dict:
    validation = inspect_glb(raw)
    json_length, kind = struct.unpack_from("<I4s", raw, 12)
    if kind != b"JSON":
        raise ValueError("E6_OPT_JSON_CHUNK_MISSING")
    model = json.loads(raw[20:20 + json_length].rstrip(b" "))
    accessors = model["accessors"]
    meshes = model["meshes"]
    materials = model["materials"]

    if model.get("textures") or model.get("images"):
        raise ValueError("E6_OPT_TEXTURES_NOT_REVIEWED")
    for material in materials:
        pbr = material.get("pbrMetallicRoughness", {})
        if pbr.get("baseColorTexture") or pbr.get("metallicRoughnessTexture") or material.get("normalTexture"):
            raise ValueError("E6_OPT_TEXTURE_REFERENCES_NOT_REVIEWED")

    geometry = []
    texcoord_sets = 0
    triangles = 0
    for mesh in meshes:
        for primitive in mesh.get("primitives", []):
            attributes = primitive["attributes"]
            if "NORMAL" not in attributes:
                raise ValueError("E6_OPT_NORMALS_REQUIRED")
            vertex = accessors[attributes["POSITION"]]
            normal = accessors[attributes["NORMAL"]]
            if vertex.get("count") != normal.get("count"):
                raise ValueError("E6_OPT_POSITION_NORMAL_MISMATCH")
            index = accessors[primitive["indices"]] if "indices" in primitive else vertex
            count = index.get("count", 0)
            if count % 3:
                raise ValueError("E6_OPT_INCOMPLETE_TRIANGLES")
            triangles += count // 3
            texcoord_sets += sum(1 for attr in attributes if attr.startswith("TEXCOORD_"))
            geometry.append({
                "material": primitive["material"],
                "positions": vertex.get("count"),
                "positionBoundsMin": vertex.get("min"),
                "positionBoundsMax": vertex.get("max"),
                "normals": normal.get("count"),
                "triangleIndices": count,
                "indexComponentType": index.get("componentType"),
            })
    return {
        "validation": validation,
        "triangleCount": triangles,
        "uvAttributeSets": texcoord_sets,
        "geometry": geometry,
        "pbrMaterials": materials,
        "sceneNodes": model.get("nodes", []),
    }



def _triangle_stream(raw: bytes) -> list:
    """Decode all original POSITION+NORMAL triangle payloads, ignoring UVs.

    Intentionally strict: no sparse accessors, normalized data, unsupported
    component types or changes to triangle order are silently accepted.
    """
    js_len = struct.unpack_from("<I", raw, 12)[0]
    model = json.loads(raw[20:20 + js_len].rstrip(b" "))
    bin_header = 20 + js_len
    bin_len, bin_kind = struct.unpack_from("<I4s", raw, bin_header)
    if bin_kind != b"BIN\x00":
        raise ValueError("E6_OPT_BINARY_CHUNK_MISSING")
    payload = raw[bin_header + 8:bin_header + 8 + bin_len]
    views = model["bufferViews"]
    accessors = model["accessors"]

    def decode(number: int) -> list:
        accessor = accessors[number]
        if accessor.get("sparse") or accessor.get("normalized"):
            raise ValueError("E6_OPT_UNSUPPORTED_ACCESSOR")
        view = views[accessor["bufferView"]]
        if view.get("buffer") != 0:
            raise ValueError("E6_OPT_EXTERNAL_VIEW_DENIED")
        component = {5121: "B", 5123: "H", 5125: "I", 5126: "f"}.get(accessor["componentType"])
        count_per_value = {"SCALAR": 1, "VEC2": 2, "VEC3": 3}.get(accessor["type"])
        if not component or not count_per_value:
            raise ValueError("E6_OPT_UNSUPPORTED_COMPONENT")
        fmt = "<" + component * count_per_value
        width = struct.calcsize(fmt)
        stride = view.get("byteStride", width)
        count = accessor["count"]
        view_offset = view.get("byteOffset", 0)
        item_offset = accessor.get("byteOffset", 0)
        if (not isinstance(count, int) or count < 0 or stride < width
                or item_offset + max(count - 1, 0) * stride + (width if count else 0) > view["byteLength"]
                or view_offset + view["byteLength"] > len(payload)):
            raise ValueError("E6_OPT_ACCESSOR_BOUNDS")
        start = view_offset + item_offset
        return [struct.unpack_from(fmt, payload, start + i * stride) for i in range(count)]

    triangles = []
    for mesh_number, mesh in enumerate(model["meshes"]):
        for primitive in mesh["primitives"]:
            xyz = decode(primitive["attributes"]["POSITION"])
            normals = decode(primitive["attributes"]["NORMAL"])
            indices = [i[0] for i in decode(primitive["indices"])] if "indices" in primitive else list(range(len(xyz)))
            if len(xyz) != len(normals) or len(indices) % 3 or any(i >= len(xyz) for i in indices):
                raise ValueError("E6_OPT_INVALID_TRIANGLE_REFERENCES")
            for offset in range(0, len(indices), 3):
                triangles.append((mesh_number, primitive["material"], tuple(
                    (xyz[j], normals[j]) for j in indices[offset:offset + 3])))
    return triangles

def compare(original: bytes, candidate: bytes) -> dict:
    before = describe(original)
    after = describe(candidate)
    for key in ("geometry", "pbrMaterials", "sceneNodes", "triangleCount"):
        if before[key] != after[key]:
            raise ValueError(f"E6_OPT_GEOMETRY_OR_MATERIAL_CHANGED:{key}")
    original_triangles = _triangle_stream(original)
    candidate_triangles = _triangle_stream(candidate)
    if original_triangles != candidate_triangles:
        raise ValueError("E6_OPT_TRIANGLE_DATA_CHANGED")
    triangle_sha256 = hashlib.sha256(
        json.dumps(original_triangles, separators=(",", ":")).encode("utf-8")
    ).hexdigest()
    if before["uvAttributeSets"] == 0 or after["uvAttributeSets"] != 0:
        raise ValueError("E6_OPT_UV_REMOVAL_NOT_DEMONSTRATED")
    if len(candidate) >= len(original):
        raise ValueError("E6_OPT_NO_RAW_BYTE_SAVING")
    return {
        "kind": "E6_BLENDER_NO_UV_OFFLINE_SIZE_COMPARISON",
        "originalSha256": hashlib.sha256(original).hexdigest(),
        "candidateSha256": hashlib.sha256(candidate).hexdigest(),
        "originalRawBytes": len(original),
        "candidateRawBytes": len(candidate),
        "rawBytesSaved": len(original) - len(candidate),
        "rawSavingPercent": round(100 * (len(original) - len(candidate)) / len(original), 2),
        "originalGzip9Bytes": len(gzip.compress(original, compresslevel=9, mtime=0)),
        "candidateGzip9Bytes": len(gzip.compress(candidate, compresslevel=9, mtime=0)),
        "vertexCount": before["validation"]["vertexCount"],
        "triangleCount": before["triangleCount"],
        "meshCount": before["validation"]["meshCount"],
        "materialCount": before["validation"]["materialCount"],
        "originalUVAttributeSets": before["uvAttributeSets"],
        "candidateUVAttributeSets": after["uvAttributeSets"],
        "trianglePositionNormalDataEqual": True,
        "trianglePositionNormalSha256": triangle_sha256,
        "geometryAndMaterialMetadataEqual": True,
        "assetAdoptedByR3F": False,
        "visualParityVerified": False,
        "networkTransferMeasured": False,
        "realGpuBenchmarked": False,
        "ownerApproved": False,
        "stage45Go": False,
        "reviewStatus": "RESEARCH_ONLY",
    }


def main() -> None:
    if len(sys.argv) != 1:
        raise SystemExit("No arbitrary file paths accepted")
    first = EVIDENCE / "aetheris-command-module.glb"
    second = EVIDENCE / "aetheris-command-module-no-uv.glb"
    manifest = EVIDENCE / "aetheris-command-module.manifest.json"
    output = EVIDENCE / "asset-size-comparison.json"
    for path in (first, second, manifest, output):
        if path.is_symlink():
            raise SystemExit("E6_OPT_SYMLINK_DENIED")
    if output.exists():
        raise SystemExit("E6_OPT_REPORT_NO_REPLACE")
    source_proof = json.loads(manifest.read_text(encoding="utf-8"))
    original = first.read_bytes()
    candidate = second.read_bytes()
    if source_proof.get("sha256") != hashlib.sha256(original).hexdigest():
        raise ValueError("E6_OPT_ORIGINAL_SOURCE_MANIFEST_MISMATCH")
    report = compare(original, candidate)
    output.write_text(json.dumps(report, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print("E6_OPT_OFFLINE_SIZE_ONLY_PASS " + json.dumps(report, sort_keys=True))


if __name__ == "__main__":
    main()
