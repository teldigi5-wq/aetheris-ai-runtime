"""Synthetic positive/negative controls for E6 offline asset-size comparison."""
import copy
import json
import struct
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "next-studio" / "blender"))
from compare_glb_variants import compare, describe


def sample(with_uv=True, pbr=None, vertices=3, name="Module"):
    attrs = {"POSITION": 0, "NORMAL": 1}
    if with_uv:
        attrs["TEXCOORD_0"] = 2
    doc = {
        "asset": {"version": "2.0"},
        "buffers": [{"byteLength": 48}],
        "bufferViews": [{"buffer": 0, "byteOffset": 0, "byteLength": 48}],
        "accessors": [
            {"count": vertices, "type": "VEC3", "componentType": 5126,
             "min": [0, 0, 0], "max": [1, 1, 1]},
            {"count": vertices, "type": "VEC3", "componentType": 5126},
            {"count": 3, "type": "VEC2", "componentType": 5126},
        ],
        "meshes": [{"name": name, "primitives": [
            {"attributes": attrs, "material": 0, "mode": 4}
        ]}],
        "materials": [{"name": "Alloy", "pbrMetallicRoughness": pbr or {
            "metallicFactor": 0.8, "roughnessFactor": 0.3}}],
        "nodes": [{"mesh": 0, "name": "Mesh Root"}],
    }
    js = json.dumps(doc, separators=(",", ":")).encode()
    js += b" " * ((-len(js)) % 4)
    blob = b"\x00" * 48
    size = 12 + 8 + len(js) + 8 + len(blob)
    return (struct.pack("<4sII", b"glTF", 2, size)
            + struct.pack("<I4s", len(js), b"JSON") + js
            + struct.pack("<I4s", len(blob), b"BIN\x00") + blob)


class GLBOptimizationPolicyTests(unittest.TestCase):
    def test_remove_only_unused_uv_and_measurable_byte_savings(self):
        result = compare(sample(), sample(with_uv=False))
        self.assertGreater(result["rawBytesSaved"], 0)
        self.assertEqual(result["originalUVAttributeSets"], 1)
        self.assertEqual(result["candidateUVAttributeSets"], 0)
        self.assertTrue(result["geometryAndMaterialMetadataEqual"])
        self.assertTrue(result["trianglePositionNormalDataEqual"])
        self.assertEqual(len(result["trianglePositionNormalSha256"]), 64)
        self.assertFalse(result["realGpuBenchmarked"])
        self.assertFalse(result["visualParityVerified"])
        self.assertFalse(result["stage45Go"])

    def test_refuse_modified_material(self):
        with self.assertRaisesRegex(ValueError, "MATERIAL_CHANGED"):
            compare(sample(), sample(with_uv=False,
                                     pbr={"metallicFactor": 0.1, "roughnessFactor": 0.5}))

    def test_refuse_changed_position_accessors(self):
        with self.assertRaisesRegex(ValueError, "MATERIAL_CHANGED"):
            compare(sample(), sample(with_uv=False, vertices=6))

    def test_reject_geometry_payload_modified_without_metadata_change(self):
        altered = bytearray(sample(with_uv=False))
        json_size = struct.unpack_from("<I", altered, 12)[0]
        binary_start = 20 + json_size + 8
        struct.pack_into("<f", altered, binary_start + 12, 0.5)
        with self.assertRaisesRegex(ValueError, "TRIANGLE_DATA_CHANGED"):
            compare(sample(), bytes(altered))

    def test_refuse_unremoved_uv(self):
        with self.assertRaisesRegex(ValueError, "UV_REMOVAL"):
            compare(sample(), sample())

    def test_refuse_external_source(self):
        source = sample()
        mutated = bytearray(source)
        mutated[0:4] = b"BAD!"
        with self.assertRaises(ValueError):
            describe(bytes(mutated))

    def test_refuse_texture_references(self):
        with self.assertRaisesRegex(ValueError, "TEXTURE_REFERENCES"):
            describe(sample(with_uv=False, pbr={"baseColorTexture": {"index": 0}}))


if __name__ == "__main__":
    unittest.main()
