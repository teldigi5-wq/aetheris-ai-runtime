"""Offline negative tests for the E6 real-Blender asset structure."""
import json
import struct
import sys
import unittest
from pathlib import Path

BLENDER = Path(__file__).parent / "next-studio" / "blender"
sys.path.insert(0, str(BLENDER))
from validate_blender_glb import inspect_glb


def tiny_glb(source=None):
    source = source or {
        "asset": {"version": "2.0"}, "buffers": [{"byteLength": 12}],
        "accessors": [{"count": 3}],
        "meshes": [{"primitives": [{"attributes": {"POSITION": 0}, "material": 0}]}],
        "materials": [{"pbrMetallicRoughness": {"baseColorFactor": [1, 1, 1, 1]}}],
    }
    js = json.dumps(source, separators=(",", ":")).encode()
    js += b" " * ((-len(js)) % 4)
    binary = b"\0" * 12
    size = 12 + 8 + len(js) + 8 + len(binary)
    return (struct.pack("<4sII", b"glTF", 2, size) + struct.pack("<I4s", len(js), b"JSON") + js
            + struct.pack("<I4s", len(binary), b"BIN\0") + binary)


class BlenderAssetContractTests(unittest.TestCase):
    def test_valid_embedded_original_asset_structure(self):
        proof = inspect_glb(tiny_glb())
        self.assertEqual(proof["vertexCount"], 3)
        self.assertFalse(proof["stage45Go"])

    def test_denies_external_uri(self):
        asset = {"asset": {"version": "2.0"},
                 "buffers": [{"byteLength": 12, "uri": "https://evil.test/mesh.bin"}],
                 "accessors": [{"count": 3}],
                 "meshes": [{"primitives": [{"attributes": {"POSITION": 0}, "material": 0}]}],
                 "materials": [{"pbrMetallicRoughness": {}}]}
        with self.assertRaisesRegex(ValueError, "EXTERNAL_BUFFER"):
            inspect_glb(tiny_glb(asset))

    def test_denies_modified_header_and_truncation(self):
        original = tiny_glb()
        for candidate in (b"BAD!" + original[4:], original[:35], original + b"bad!"):
            with self.subTest(length=len(candidate)), self.assertRaises(ValueError):
                inspect_glb(candidate)

    def test_denies_unreviewed_images_or_extensions(self):
        for extra in ({"images": [{"uri": "data:image/png;base64,a"}]},
                      {"extensionsRequired": ["KHR_draco_mesh_compression"]}):
            data = {"asset": {"version": "2.0"}, "buffers": [{"byteLength": 12}],
                    "accessors": [{"count": 3}],
                    "meshes": [{"primitives": [{"attributes": {"POSITION": 0}, "material": 0}]}],
                    "materials": [{"pbrMetallicRoughness": {}}]}
            data.update(extra)
            with self.subTest(extra=extra), self.assertRaisesRegex(ValueError, "EXTENSION_OR_IMAGE"):
                inspect_glb(tiny_glb(data))

    def test_source_is_additive_blender_not_browser_autoreplace(self):
        source = (BLENDER / "author_command_module.py").read_text()
        self.assertIn("import bpy", source)
        self.assertIn("bpy.ops.wm.save_as_mainfile", source)
        self.assertIn("bpy.ops.export_scene.gltf", source)
        self.assertIn('"assetApproved": False', source)
        self.assertNotIn("urllib", source)
        self.assertNotIn("requests.", source)
        self.assertNotIn("research-panel.glb", source)


if __name__ == "__main__":
    unittest.main()
