#!/usr/bin/env python3
"""W4-B: genuine CPU-rendered Blender still of the independently source-verified
W3-B Signal Fold concept. No external images, media, texture links or models.

This is a real render of the research object, NOT a product photograph,
human-approved artwork, video render, or physical GPU benchmark.
"""
from __future__ import annotations
import hashlib
import json
import math
import sys
from pathlib import Path

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[4]
SOURCE=ROOT/"target/e6-studio/w3b-signal-fold/arcloom-signal-fold.blend"
OUTPUT=ROOT/"target/e6-studio/w4b-signal-fold"
IMAGE=OUTPUT/"signal-fold-still.png"
PROOF=OUTPUT/"signal-fold-still.provenance.json"

def fail(reason):
    raise ValueError("E6_W4B_"+reason)

def main():
    import bpy
    from mathutils import Vector
    if not (4,0,0)<=tuple(bpy.app.version)<(4,1,0):
        fail("UNREVIEWED_BLENDER")
    if SOURCE.is_symlink() or not SOURCE.is_file():
        fail("SOURCE_BLEND_UNTRUSTED")
    for d in (ROOT/"target",ROOT/"target/e6-studio",OUTPUT):
        if d.is_symlink():fail("OUTPUT_SYMLINK")
    if IMAGE.exists() or IMAGE.is_symlink() or PROOF.exists() or PROOF.is_symlink():
        fail("NO_OVERWRITE")
    # Source is the independently verified W3-B .blend; never re-author the
    # product here. All scene lights, camera and background are new research setup.
    bpy.ops.wm.open_mainfile(filepath=str(SOURCE))
    mesh=[o for o in bpy.context.scene.objects if o.type=="MESH"]
    if len(mesh)!=7:fail("SOURCE_OBJECT_COUNT")
    names=[o.name for o in mesh]
    if not any("Folded alloy lower wing" in n for n in names):
        fail("SOURCE_BRAND_OBJECT_MISSING")
    if not any("Inclined graphite fold" in n for n in names):
        fail("SOURCE_BRAND_OBJECT_MISSING")
    scene=bpy.context.scene

    def material(name,color,roughness):
        mat=bpy.data.materials.new(name)
        mat.use_nodes=True
        bsdf=mat.node_tree.nodes.get("Principled BSDF")
        bsdf.inputs["Base Color"].default_value=(*color,1)
        bsdf.inputs["Roughness"].default_value=roughness
        return mat
    # The plinth is a separate scene prop, NOT part of the original seven-piece GLB.
    bpy.ops.mesh.primitive_cylinder_add(vertices=96,radius=1.43,depth=.13,location=(0,0,-.29))
    plinth=bpy.context.object
    plinth.name="W4B / photography-only limestone plinth"
    plinth.data.materials.append(material("W4B limestone studio base",(.48,.45,.41),.8))
    bevel=plinth.modifiers.new("W4B polished lip",type="BEVEL")
    bevel.width=.045;bevel.segments=3
    bpy.ops.object.modifier_apply(modifier=bevel.name)
    bpy.ops.mesh.primitive_plane_add(size=200,location=(0,0,-.37))
    floor=bpy.context.object
    floor.name="W4B / photography-only floor"
    floor.data.materials.append(material("W4B neutral studio floor",(.62,.58,.53),.92))
    bpy.ops.object.camera_add(location=(3.5,-5.1,3.55))
    camera=bpy.context.object
    direction=Vector((0,0,.22))-camera.location
    camera.rotation_euler=direction.to_track_quat("-Z","Y").to_euler()
    camera.data.type="ORTHO"
    camera.data.ortho_scale=4.9
    scene.camera=camera

    def area(name,location,energy,shape,size,color):
        bpy.ops.object.light_add(type="AREA",location=location)
        obj=bpy.context.object
        obj.name=name
        obj.data.energy=energy
        obj.data.shape=shape
        obj.data.size=size
        obj.data.color=color
        direction=Vector((0,0,.25))-obj.location
        obj.rotation_euler=direction.to_track_quat("-Z","Y").to_euler()
    area("W4B large warm key",(-3,-4,6),900,"DISK",5,(1,.90,.78))
    area("W4B restrained cool fill",(4,1,4),580,"DISK",4,(.82,.89,1))
    area("W4B sharp rim",(1,3,5),1050,"DISK",2,(1,.86,.78))
    world=bpy.data.worlds.new("W4B bounded warm studio world")
    world.use_nodes=True
    world.node_tree.nodes.get("Background").inputs["Color"].default_value=(.34,.32,.30,1)
    world.node_tree.nodes.get("Background").inputs["Strength"].default_value=.65
    scene.world=world
    scene.render.engine="CYCLES"
    scene.cycles.device="CPU"
    scene.cycles.samples=16
    scene.render.resolution_x=800
    scene.render.resolution_y=900
    scene.render.resolution_percentage=100
    scene.render.image_settings.file_format="PNG"
    scene.render.image_settings.color_mode="RGBA"
    scene.render.film_transparent=False
    scene.view_settings.view_transform="AgX"
    OUTPUT.mkdir(parents=True,exist_ok=True)
    scene.render.filepath=str(IMAGE)
    bpy.ops.render.render(write_still=True)
    raw=IMAGE.read_bytes()
    if not raw.startswith(b"\x89PNG\r\n\x1a\n") or len(raw)<15000:
        fail("EMPTY_OR_NON_PNG_RENDER")
    author=Path(__file__).read_bytes()
    proof={
      "kind":"E6_W4B_ORIGINAL_BLENDER_STILL_V1",
      "asset":"signal-fold-still.png",
      "brand":"Arcloom Objects",
      "fictionalStudy":"Signal Fold",
      "sourceBlenderSha256":hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
      "renderScriptSha256":hashlib.sha256(author).hexdigest(),
      "imageSha256":hashlib.sha256(raw).hexdigest(),
      "imageBytes":len(raw),
      "renderEngine":"BLENDER_CYCLES_CPU_NOT_PHYSICAL_GPU",
      "resolution":[800,900],
      "samples":16,
      "firstPartyModel":True,
      "externalImages":False,
      "productPhotograph":False,
      "modelAuthored":False,
      "independentArtDirectionApproved":False,
      "physicalGpuMeasured":False,
      "ownerApproved":False,
      "releaseGo":False,
      "stage45Go":False
    }
    PROOF.write_text(json.dumps(proof,sort_keys=True,indent=2)+"\n",encoding="utf-8")
    print("E6_W4B_CPU_ORIGINAL_STILL_CREATED "+json.dumps(proof,sort_keys=True))

if __name__=="__main__":
    if "--" in sys.argv:fail("NO_USER_SCRIPT_ARGS")
    main()
