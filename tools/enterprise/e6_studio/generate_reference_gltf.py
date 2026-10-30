#!/usr/bin/env python3
"""Generate/check one deterministic, self-contained glTF 2.0 research cube.

Human-authored geometry, NOT a Blender export, AI-generated mesh, asset
pipeline for untrusted files, third-party licensed model or customer website.
No external dependencies, downloads, model calls, arbitrary file paths, or
deployment. Generated file is ephemeral/ignored and must be recreated locally.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import math
import struct
from pathlib import Path

FOLDER = Path(__file__).resolve().parent / "gltf_lab"
FILE = FOLDER / "reference_mesh.gltf"
MAX_BYTES = 8192
FACES = (
    ((-1,-1,1),(1,-1,1),(1,1,1),(-1,1,1),(0,0,1)),
    ((1,-1,-1),(-1,-1,-1),(-1,1,-1),(1,1,-1),(0,0,-1)),
    ((1,-1,1),(1,-1,-1),(1,1,-1),(1,1,1),(1,0,0)),
    ((-1,-1,-1),(-1,-1,1),(-1,1,1),(-1,1,-1),(-1,0,0)),
    ((-1,1,1),(1,1,1),(1,1,-1),(-1,1,-1),(0,1,0)),
    ((-1,-1,-1),(1,-1,-1),(1,-1,1),(-1,-1,1),(0,-1,0)),
)


def canonical(obj: object) -> bytes:
    return (json.dumps(obj, sort_keys=True, separators=(",", ":"),
                       ensure_ascii=True) + "\n").encode("ascii")


def expected_bytes() -> bytes:
    scalars: list[float] = []
    for a,b,c,d,normal in FACES:
        for vertex in (a,b,c,a,c,d):
            scalars.extend(vertex)
            scalars.extend(normal)
    assert len(scalars) == 36 * 6
    binary = struct.pack("<216f", *scalars)
    assert len(binary) == 864
    uri = "data:application/octet-stream;base64," + base64.b64encode(binary).decode("ascii")
    gltf = {
        "asset": {"version": "2.0", "generator": "Aetheris E6 human-authored cube research fixture v1",
                  "extras": {"source": "ORIGINAL_OWNED_TEST_GEOMETRY_NO_THIRD_PARTY_ASSETS",
                             "blenderExported": False, "aiModelAuthored": False,
                             "productionApproved": False}},
        "scene": 0,
        "scenes": [{"nodes": [0]}],
        "nodes": [{"mesh": 0, "name": "HumanAuthoredPanelPrimitive"}],
        "meshes": [{"name": "HumanAuthoredCube",
                    "primitives": [{"attributes": {"POSITION": 0, "NORMAL": 1},
                                    "mode": 4}]}],
        "buffers": [{"byteLength": 864, "uri": uri}],
        "bufferViews": [{"buffer": 0, "byteOffset": 0, "byteLength": 864,
                         "byteStride": 24, "target": 34962}],
        "accessors": [
            {"bufferView": 0, "byteOffset": 0, "componentType": 5126,
             "count": 36, "type": "VEC3", "min": [-1,-1,-1], "max": [1,1,1]},
            {"bufferView": 0, "byteOffset": 12, "componentType": 5126,
             "count": 36, "type": "VEC3"},
        ],
    }
    return canonical(gltf)


def validate(raw: bytes) -> dict:
    if not raw or len(raw) > MAX_BYTES:
        raise ValueError("E6_GLTF_ASSET_SIZE_INVALID")
    try:
        parsed = json.loads(raw.decode("ascii"))
    except (ValueError, UnicodeError) as error:
        raise ValueError("E6_GLTF_INVALID_JSON") from error
    if not isinstance(parsed, dict) or set(parsed) != {
        "asset", "scene", "scenes", "nodes", "meshes", "buffers", "bufferViews", "accessors"
    }:
        raise ValueError("E6_GLTF_UNAPPROVED_SCHEMA")
    if parsed != json.loads(expected_bytes()):
        raise ValueError("E6_GLTF_NOT_PINNED_HUMAN_AUTHORED_REFERENCE")
    uri=parsed["buffers"][0]["uri"]
    if not uri.startswith("data:application/octet-stream;base64,"):
        raise ValueError("E6_GLTF_EXTERNAL_BUFFER_REFUSED")
    blob=base64.b64decode(uri.partition(",")[2], validate=True)
    if len(blob) != 864:
        raise ValueError("E6_GLTF_BUFFER_LENGTH_INVALID")
    numbers=struct.unpack("<216f", blob)
    if not all(math.isfinite(value) and -1 <= value <= 1 for value in numbers):
        raise ValueError("E6_GLTF_NONFINITE_OR_OOB_GEOMETRY")
    return {
        "format": "E6_GLTF2_OWNED_REFERENCE_FIXTURE",
        "sha256": hashlib.sha256(raw).hexdigest(),
        "vertexCount": 36,
        "interleavedBytes": 864,
        "localEmbeddedBuffer": True,
        "externalUris": False,
        "blenderExported": False,
        "modelAuthored": False,
        "licensedThirdPartyAssets": False,
        "ownerApproved": False,
        "stage45Go": False,
    }


def main() -> int:
    import sys
    parser=argparse.ArgumentParser(description=__doc__)
    group=parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--generate", action="store_true")
    group.add_argument("--verify", action="store_true")
    args=parser.parse_args()
    expected=expected_bytes()
    if args.generate:
        FOLDER.mkdir(parents=True, exist_ok=True)
        if FILE.is_symlink() or FILE.exists():
            raise SystemExit("E6_GLTF_NO_OVERWRITE_UNTRUSTED_OR_EXISTING_FILE")
        fd = __import__("os").open(FILE, __import__("os").O_WRONLY |
                        __import__("os").O_CREAT | __import__("os").O_EXCL |
                        getattr(__import__("os"), "O_NOFOLLOW", 0), 0o600)
        with __import__("os").fdopen(fd, "wb") as output:
            output.write(expected)
    if not FILE.is_file() or FILE.is_symlink():
        raise SystemExit("E6_GLTF_ASSET_NOT_REGULAR")
    metadata=validate(FILE.read_bytes())
    if FILE.read_bytes() != expected:
        raise SystemExit("E6_GLTF_CANONICAL_BYTES_MISMATCH")
    print(json.dumps(metadata, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
