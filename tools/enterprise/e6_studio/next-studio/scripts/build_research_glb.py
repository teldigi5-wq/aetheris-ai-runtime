#!/usr/bin/env python3
"""E6 research only: build a deterministic ORIGINAL glTF -> GLB for R3F.

Does not ingest third-party content or claim Blender/Meshopt compression.
Source geometry is the exact earlier human-authored glTF fixture.
No arbitrary paths, external downloads, network or paid software.
"""
from __future__ import annotations

import base64
import hashlib
import json
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UPSTREAM = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(UPSTREAM))
from generate_reference_gltf import expected_bytes, validate  # noqa: E402

TARGET = ROOT / "public" / "research-panel.glb"
MANIFEST = ROOT / "public" / "research-panel-manifest.json"
MAX_BYTES = 4096


def build() -> bytes:
    source = expected_bytes()
    info = validate(source)
    assert info["vertexCount"] == 36 and info["externalUris"] is False
    gltf = json.loads(source)
    original_uri = gltf["buffers"][0].pop("uri")
    assert original_uri.startswith("data:application/octet-stream;base64,")
    payload = base64.b64decode(original_uri.split(",", 1)[1], validate=True)
    assert len(payload) == 864
    gltf["materials"] = [{
        "name": "OwnedResearchMetal",
        "pbrMetallicRoughness": {
            "baseColorFactor": [0.52, 0.58, 0.59, 1.0],
            "metallicFactor": 0.64,
            "roughnessFactor": 0.34,
        },
        "doubleSided": False,
    }]
    gltf["meshes"][0]["primitives"][0]["material"] = 0
    json_raw = json.dumps(gltf, sort_keys=True, separators=(",", ":")).encode()
    json_padded = json_raw + b" " * ((-len(json_raw)) % 4)
    binary_padded = payload + b"\x00" * ((-len(payload)) % 4)
    length = 12 + 8 + len(json_padded) + 8 + len(binary_padded)
    assert length <= MAX_BYTES
    return (
        struct.pack("<4sII", b"glTF", 2, length)
        + struct.pack("<I4s", len(json_padded), b"JSON")
        + json_padded
        + struct.pack("<I4s", len(binary_padded), b"BIN\x00")
        + binary_padded
    )


def verify(raw: bytes) -> dict:
    if len(raw) > MAX_BYTES or len(raw) < 32:
        raise ValueError("E6_NEXT_GLTF_BYTES_DENIED")
    if raw != build():
        raise ValueError("E6_NEXT_GLTF_NOT_PINNED_OR_MODIFIED")
    magic,version,declared = struct.unpack_from("<4sII", raw)
    if (magic,version,declared) != (b"glTF",2,len(raw)):
        raise ValueError("E6_NEXT_GLTF_HEADER")
    return {
        "format": "E6_OWNED_GLB_2_RESEARCH_ONLY",
        "sha256": hashlib.sha256(raw).hexdigest(),
        "bytes": len(raw),
        "sourceVertices": 36,
        "blenderExported": False,
        "dracoOrMeshoptCompressed": False,
        "sourceAuthoredByModel": False,
        "assetLicensingCertified": False,
        "ownerApproved": False,
        "stage45Go": False,
    }


def main() -> None:
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    data = build()
    proof = verify(data)
    if TARGET.exists() or TARGET.is_symlink():
        if TARGET.is_symlink() or TARGET.read_bytes() != data:
            raise SystemExit("E6_NEXT_GLB_NO_REPLACE")
    else:
        TARGET.write_bytes(data)
    manifest = (json.dumps(proof, sort_keys=True, separators=(",", ":")) + "\n").encode()
    if MANIFEST.exists() or MANIFEST.is_symlink():
        if MANIFEST.is_symlink() or MANIFEST.read_bytes() != manifest:
            raise SystemExit("E6_NEXT_GLB_MANIFEST_NO_REPLACE")
    else:
        MANIFEST.write_bytes(manifest)
    print(json.dumps(proof, sort_keys=True))


if __name__ == "__main__":
    main()
