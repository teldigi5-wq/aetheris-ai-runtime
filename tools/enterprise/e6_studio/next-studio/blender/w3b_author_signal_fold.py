#!/usr/bin/env python3
"""E6 W3-B: first-party procedural Blender design exploration for fictional
Arcloom / Signal Fold. Generates a real .blend and self-contained GLB.

All shapes, colors and dimensions below are new human-authored research
concepts. This is NOT an existing product, licensed third-party content,
photogrammetry, a physical prototype, real-model creative proof, or release art.
Blender 4.0 only, fixed local target, no network, no user code, fail on overwrite.
"""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
OUTPUT = ROOT / "target/e6-studio/w3b-signal-fold"
sys.path.insert(0, str(HERE))
from validate_blender_glb import inspect_glb  # noqa: E402

FILE = "arcloom-signal-fold"
VERSION = "W3B_SIGNAL_FOLD_CONCEPT_V1"

def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()

def source_paths():
    return (OUTPUT / (FILE + ".blend"), OUTPUT / (FILE + ".glb"),
            OUTPUT / (FILE + ".manifest.json"))

def preflight():
    for d in (ROOT / "target", ROOT / "target/e6-studio", OUTPUT):
        if d.is_symlink():
            raise ValueError("E6_W3B_SYMLINK_OUTPUT_DENIED")
    if any(p.exists() or p.is_symlink() for p in source_paths()):
        raise ValueError("E6_W3B_OUTPUT_ALREADY_EXISTS")
    OUTPUT.mkdir(parents=True, exist_ok=True)

def build():
    import bpy
    if not (4, 0, 0) <= tuple(bpy.app.version) < (4, 1, 0):
        raise ValueError("E6_W3B_BLENDER_VERSION_NOT_REVIEWED")
    preflight()
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    # Existing unused factory materials are harmless; do not call a Blender
    # collection.clear() API that is absent on the reviewed 4.0.2 build.

    def material(name, rgba, metallic, roughness):
        m = bpy.data.materials.new(name=name)
        m.use_nodes = True
        p = m.node_tree.nodes.get("Principled BSDF")
        p.inputs["Base Color"].default_value = rgba
        p.inputs["Metallic"].default_value = metallic
        p.inputs["Roughness"].default_value = roughness
        m.diffuse_color = rgba
        return m

    # Restrained graphite, engineered silver, porcelain, vermilion, elastomer.
    titanium = material("Signal / bead-blasted titanium", (.37,.41,.43,1), .82,.37)
    graphite = material("Signal / graphite anodized shell", (.035,.047,.055,1), .7,.33)
    chalk = material("Signal / warm porcelain base", (.77,.75,.69,1), .11,.28)
    vermilion = material("Signal / vermilion lacquer detail", (.51,.055,.04,1), .25,.21)
    rubber = material("Signal / matte anti-slip insert", (.015,.017,.02,1), .01,.82)

    def plate(name, loc, dims, mat, bevel, y_angle=0.0):
        bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
        obj = bpy.context.object
        obj.name = name
        obj.dimensions = dims
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
        obj.rotation_euler[1] = y_angle
        obj.data.materials.append(mat)
        mod = obj.modifiers.new("Soft machined edge", type="BEVEL")
        mod.width = bevel
        mod.segments = 3
        bpy.ops.object.modifier_apply(modifier=mod.name)
        normal = obj.modifiers.new("Weighted face normals", type="WEIGHTED_NORMAL")
        bpy.ops.object.modifier_apply(modifier=normal.name)
        return obj

    # A seven-part invented tabletop fold. Each component is mechanically
    # coherent, but has no real manufacturability/load-rating claims.
    plate("01 / Porcelain foundation", (0,0,-.10), (2.22,1.27,.16), chalk,.072)
    plate("02 / Folded alloy lower wing", (-.44,0,.10), (1.33,1.04,.105), titanium,.048)
    plate("03 / Inclined graphite fold", (.18,0,.40), (1.07,1.00,.088),
          graphite,.039,-.71)
    plate("04 / Upper alloy wing", (.73,0,.67), (.84,.97,.074), titanium,.036)
    plate("05 / Lacquered signature line", (.74,-.385,.727), (.59,.027,.014),
          vermilion,.006)
    plate("06 / Inlaid porcelain index", (-.60,-.32,.164), (.42,.09,.016),
          chalk,.007)
    plate("07 / Elastomer anti-slip underside", (0,0,-.189), (1.8,.91,.029),
          rubber,.011)

    meshes = [o for o in bpy.context.scene.objects if o.type == "MESH"]
    names = sorted(m.name for m in bpy.data.materials if m.users)
    if len(meshes) != 7 or len(names) != 5:
        raise ValueError("E6_W3B_DESIGN_COMPOSITION_INVALID")
    for o in meshes:
        if not o.data.materials:
            raise ValueError("E6_W3B_UNMATERIALIZED_MESH")
    bpy.context.scene.render.resolution_x = 1600
    bpy.context.scene.render.resolution_y = 1000
    blend, glb, manifest = source_paths()
    bpy.ops.wm.save_as_mainfile(filepath=str(blend), compress=True)
    bpy.ops.export_scene.gltf(
        filepath=str(glb), export_format="GLB", export_apply=True,
        export_normals=True, export_texcoords=False,
        export_materials="EXPORT", export_cameras=False,
        export_lights=False, export_animations=False,
        export_yup=True, check_existing=True)
    raw = glb.read_bytes()
    result = inspect_glb(raw)
    if result["meshCount"] != 7 or result["materialCount"] != 5 or result["vertexCount"] > 8000:
        raise ValueError("E6_W3B_VALIDATED_MESH_BUDGET_FAILED")
    evidence = {
        "kind": VERSION, "site": "Arcloom Objects", "fictionalStudy": "Signal Fold",
        "assetClass": "human-authored-procedural-Blender-design-study",
        "licenseBasis": "first-party-source-subject-to-owner-review",
        "sourceScriptSha256": sha(Path(__file__).read_bytes()),
        "blenderProjectSha256": sha(blend.read_bytes()),
        "glbSha256": sha(raw), "glbBytes": len(raw),
        "meshCount": result["meshCount"],
        "materialCount": result["materialCount"],
        "vertexCount": result["vertexCount"],
        "materialNames": names, "blenderVersion": ".".join(map(str,bpy.app.version)),
        "sourceLocked": True, "externalMedia": False,
        "unverifiedExtensions": False, "modelGenerated": False,
        "physicalPrototype": False, "clientApproved": False,
        "visualApproved": False, "licenseIndependentlyCertified": False,
        "mobileGpuMeasured": False, "productionAsset": False,
        "ownerApproved": False, "stage45Go": False, "releaseGo": False}
    manifest.write_text(json.dumps(evidence,indent=2,sort_keys=True)+"\n",encoding="utf-8")
    print("E6_W3B_BRAND_SPECIFIC_BLENDER_RESEARCH_PASS "+json.dumps(evidence,sort_keys=True))

if __name__ == "__main__":
    # Blender includes its own reviewed startup flags in sys.argv. A separate
    # -- sentinel denotes untrusted script arguments and is always rejected.
    if "--" in sys.argv:
        raise SystemExit("E6_W3B_NO_ARBITRARY_INPUT_ALLOWED")
    build()
