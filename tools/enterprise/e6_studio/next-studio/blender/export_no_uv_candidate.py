#!/usr/bin/env python3
"""Opt-in, additive Blender export without UV attributes for an untextured model.

Consumes only the immediately preceding CI-generated and SHA-verified owned .blend
source. Never replaces the approved research GLB or edits any website asset.
"""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from validate_blender_glb import inspect_glb

ROOT = Path(__file__).resolve().parents[5]
OUTPUT = ROOT / "target" / "e6-studio" / "blender-proof"


def main() -> None:
    import bpy

    source = OUTPUT / "aetheris-command-module.blend"
    manifest = OUTPUT / "aetheris-command-module.manifest.json"
    candidate = OUTPUT / "aetheris-command-module-no-uv.glb"

    for path in (ROOT / "target", ROOT / "target" / "e6-studio", OUTPUT, source, manifest, candidate):
        if path.is_symlink():
            raise SystemExit("E6_OPT_SYMLINK_DENIED")
    if not source.is_file() or not manifest.is_file() or candidate.exists():
        raise SystemExit("E6_OPT_MISSING_OR_OVERWRITE_DENIED")

    proof = json.loads(manifest.read_text(encoding="utf-8"))
    if (
        proof.get("stage45Go") is not False
        or proof.get("assetApproved") is not False
        or proof.get("blenderSourceSha256") != hashlib.sha256(source.read_bytes()).hexdigest()
    ):
        raise SystemExit("E6_OPT_SOURCE_PROVENANCE_DENIED")

    bpy.ops.wm.open_mainfile(filepath=str(source), load_ui=False)
    bpy.ops.export_scene.gltf(
        filepath=str(candidate),
        export_format="GLB",
        export_apply=True,
        export_normals=True,
        export_texcoords=False,
        export_materials="EXPORT",
        export_cameras=False,
        export_lights=False,
        export_animations=False,
        export_yup=True,
        check_existing=True,
    )
    result = inspect_glb(candidate.read_bytes())
    assert result["stage45Go"] is False
    print("E6_OPT_NO_UV_CANDIDATE_EXPORTED " + json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
