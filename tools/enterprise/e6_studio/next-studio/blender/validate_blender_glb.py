"""Dependency-free structural validation for E6 Blender-exported research GLB."""
from __future__ import annotations
import hashlib
import json
import struct

MAX_GLB_BYTES = 2_000_000
MAX_VERTICES = 8_000


def inspect_glb(raw: bytes) -> dict:
    if len(raw) < 40 or len(raw) > MAX_GLB_BYTES:
        raise ValueError("E6_BLENDER_SIZE_DENIED")
    magic, version, length = struct.unpack_from("<4sII", raw)
    if (magic, version, length) != (b"glTF", 2, len(raw)):
        raise ValueError("E6_BLENDER_HEADER_INVALID")
    offset = 12
    chunks = []
    while offset < len(raw):
        if offset + 8 > len(raw):
            raise ValueError("E6_BLENDER_CHUNK_TRUNCATED")
        chunk_length, chunk_type = struct.unpack_from("<I4s", raw, offset)
        offset += 8
        if chunk_length % 4 or offset + chunk_length > len(raw):
            raise ValueError("E6_BLENDER_CHUNK_SIZE_INVALID")
        chunks.append((chunk_type, raw[offset:offset + chunk_length]))
        offset += chunk_length
    if len(chunks) != 2 or chunks[0][0] != b"JSON" or chunks[1][0] != b"BIN\x00":
        raise ValueError("E6_BLENDER_CHUNKS_INVALID")
    try:
        model = json.loads(chunks[0][1].rstrip(b" "))
    except (UnicodeError, ValueError) as error:
        raise ValueError("E6_BLENDER_JSON_INVALID") from error
    if model.get("asset", {}).get("version") != "2.0":
        raise ValueError("E6_BLENDER_GLTF_VERSION_INVALID")
    buffers = model.get("buffers", [])
    if len(buffers) != 1 or buffers[0].get("uri") or buffers[0].get("byteLength", 0) > len(chunks[1][1]):
        raise ValueError("E6_BLENDER_EXTERNAL_BUFFER_DENIED")
    if model.get("extensionsRequired") or model.get("images"):
        raise ValueError("E6_BLENDER_UNREVIEWED_ASSET_EXTENSION_OR_IMAGE")
    meshes = model.get("meshes", [])
    materials = model.get("materials", [])
    accessors = model.get("accessors", [])
    if not meshes or len(meshes) > 12 or not materials or len(materials) > 6:
        raise ValueError("E6_BLENDER_MESH_MATERIAL_BUDGET")
    vertex_count = 0
    for mesh in meshes:
        for primitive in mesh.get("primitives", []):
            if primitive.get("mode", 4) != 4 or "POSITION" not in primitive.get("attributes", {}):
                raise ValueError("E6_BLENDER_PRIMITIVE_DENIED")
            pos = primitive["attributes"]["POSITION"]
            if not isinstance(pos, int) or pos < 0 or pos >= len(accessors):
                raise ValueError("E6_BLENDER_ACCESSOR_INVALID")
            vertex_count += accessors[pos].get("count", 0)
            mat_idx = primitive.get("material", -1)
            if not isinstance(mat_idx, int) or not 0 <= mat_idx < len(materials):
                raise ValueError("E6_BLENDER_MISSING_MATERIAL")
    if not 1 <= vertex_count <= MAX_VERTICES or not all("pbrMetallicRoughness" in m for m in materials):
        raise ValueError("E6_BLENDER_BUDGET_OR_PBR_INVALID")
    if model.get("animations") or model.get("skins") or model.get("cameras"):
        raise ValueError("E6_BLENDER_UNREVIEWED_CONTENT")
    return {"format": "GLB_2_BLENDER_RESEARCH", "sha256": hashlib.sha256(raw).hexdigest(),
            "bytes": len(raw), "meshCount": len(meshes), "materialCount": len(materials),
            "vertexCount": vertex_count, "externalUris": False,
            "assetApproved": False, "stage45Go": False}
