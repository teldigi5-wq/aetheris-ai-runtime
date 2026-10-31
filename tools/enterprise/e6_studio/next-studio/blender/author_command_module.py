#!/usr/bin/env python3
"""Original parametric Aetheris command-module research object for Blender 4.0.

Blender itself writes .blend source and .glb asset under CI evidence/ only.
No imported artwork, assets, textures, models or external network calls.
Not a hand-sculpted production hero, final brand artwork or GPU benchmark.
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

    if not (4, 0, 0) <= tuple(bpy.app.version) < (5, 0, 0):
        raise SystemExit("E6_BLENDER_UNREVIEWED_VERSION")
    for path in (ROOT / "target", ROOT / "target" / "e6-studio", OUTPUT):
        if path.is_symlink():
            raise SystemExit("E6_BLENDER_SYMLINK_PATH_DENIED")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    source = OUTPUT / "aetheris-command-module.blend"
    exported = OUTPUT / "aetheris-command-module.glb"
    manifest = OUTPUT / "aetheris-command-module.manifest.json"
    if any(path.exists() or path.is_symlink() for path in (source, exported, manifest)):
        raise SystemExit("E6_BLENDER_NO_REPLACE")

    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)

    def material(name: str, rgba: tuple, metal: float, rough: float):
        mat = bpy.data.materials.new(name=name)
        mat.use_nodes = True
        bsdf = mat.node_tree.nodes.get("Principled BSDF")
        bsdf.inputs["Base Color"].default_value = rgba
        bsdf.inputs["Metallic"].default_value = metal
        bsdf.inputs["Roughness"].default_value = rough
        mat.diffuse_color = rgba
        return mat

    alloy = material("Brushed alloy", (0.29, 0.33, 0.34, 1), 0.85, 0.31)
    graphite = material("Graphite inset", (0.025, 0.033, 0.038, 1), 0.4, 0.48)
    silver = material("Machined connectors", (0.64, 0.67, 0.66, 1), 0.78, 0.22)
    amber = material("Governance indicator", (0.56, 0.29, 0.12, 1), 0.35, 0.35)
    # Additional owned PBR palette; no procedural texture nodes or outside media.
    ceramic = material("Smoked ceramic core", (0.045, 0.09, 0.11, 1), 0.18, 0.26)
    bronze = material("Machined warm bronze", (0.58, 0.39, 0.22, 1), 0.81, 0.28)

    def part(name: str, position: tuple, dimensions: tuple, mat, bevel: float):
        bpy.ops.mesh.primitive_cube_add(size=1, location=position)
        obj = bpy.context.object
        obj.name = name
        obj.dimensions = dimensions
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
        obj.data.materials.append(mat)
        modifier = obj.modifiers.new(name="Precision edge bevel", type="BEVEL")
        modifier.width = bevel
        modifier.segments = 2
        bpy.ops.object.modifier_apply(modifier=modifier.name)
        return obj

    part("Chassis — CNC alloy shell", (0, 0, 0), (2.3, 1.34, 0.17), alloy, 0.055)
    part("Inset — governed runtime", (0, -0.02, 0.1), (1.95, 1.02, 0.052), graphite, 0.027)
    part("Status rail — authorized", (-0.57, -0.3, 0.138), (0.55, 0.045, 0.013), amber, 0.004)
    part("Status rail — reviewed", (0.13, -0.3, 0.138), (0.55, 0.045, 0.013), silver, 0.004)
    part("Status rail — isolated", (0.66, -0.3, 0.138), (0.28, 0.045, 0.013), silver, 0.004)
    part("Inset connector left", (-1.0, 0.23, 0.143), (0.04, 0.24, 0.014), silver, 0.003)
    part("Inset connector right", (1.0, 0.23, 0.143), (0.04, 0.24, 0.014), silver, 0.003)
    # E6 concept art pass: one non-functional governance dial with tactile machined
    # bezel, ceramic insert, two retainer spines, and a lower bus. 12 meshes max.
    # These are visual metaphors, NOT actual device features or agent indicators.
    bpy.ops.mesh.primitive_torus_add(
        major_segments=32, minor_segments=8,
        location=(0.0, 0.11, 0.165),
        major_radius=0.235, minor_radius=0.017,
    )
    bezel = bpy.context.object
    bezel.name = "Governance bezel — warm anodized ring"
    bezel.data.materials.append(bronze)
    for polygon in bezel.data.polygons:
        polygon.use_smooth = True

    bpy.ops.mesh.primitive_cylinder_add(
        vertices=40, radius=0.167, depth=0.028,
        location=(0.0, 0.11, 0.144),
    )
    core = bpy.context.object
    core.name = "Runtime core — smoked ceramic"
    core.data.materials.append(ceramic)
    core_bevel = core.modifiers.new(name="Ceramic lip rounding", type="BEVEL")
    core_bevel.width = 0.008
    core_bevel.segments = 2
    bpy.ops.object.modifier_apply(modifier=core_bevel.name)
    for polygon in core.data.polygons:
        polygon.use_smooth = polygon.area < 0.015

    part("Side spine — left", (-0.87, 0.0, 0.148),
         (0.026, 0.64, 0.013), silver, 0.003)
    part("Side spine — right", (0.87, 0.0, 0.148),
         (0.026, 0.64, 0.013), silver, 0.003)
    part("Lower signature bus — nonfunctional", (0.0, -0.45, 0.145),
         (1.35, 0.023, 0.01), bronze, 0.003)

    assert len([obj for obj in bpy.context.scene.objects if obj.type == "MESH"]) == 12
    assert len(bpy.data.materials) == 6


    bpy.context.scene.render.resolution_x = 1280
    bpy.context.scene.render.resolution_y = 720
    bpy.ops.wm.save_as_mainfile(filepath=str(source), compress=True)
    bpy.ops.export_scene.gltf(
        filepath=str(exported), export_format="GLB", export_apply=True,
        export_normals=True, export_texcoords=True, export_materials="EXPORT",
        export_cameras=False, export_lights=False, export_animations=False,
        export_yup=True, check_existing=True,
    )
    proof = inspect_glb(exported.read_bytes())
    proof.update({
        "blenderVersion": ".".join(str(n) for n in bpy.app.version),
        "blenderSourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "authoringScriptSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        "authorship": "Original procedural Blender source created for this E6 research proof",
        "assetApproved": False,
        "ownerApproved": False,
        "licensingCertified": False,
        "productionReady": False,
    })
    manifest.write_text(json.dumps(proof, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print("E6_BLENDER_RESEARCH_EXPORT_VALIDATED " + json.dumps(proof, sort_keys=True))


if __name__ == "__main__":
    main()
