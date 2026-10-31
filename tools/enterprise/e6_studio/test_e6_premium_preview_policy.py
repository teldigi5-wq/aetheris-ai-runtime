"""Offline proof and tamper controls for the build-flagged E6 Blender preview."""
from __future__ import annotations

import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).parent
APP = ROOT / "next-studio"
sys.path.insert(0, str(APP / "scripts"))
from stage_blender_preview import verified_stage
import stage_blender_preview as stage
from test_e6_blender_source import tiny_glb


class OptInBlenderPreviewPolicyTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.evidence = self.root / "target/e6-studio/blender-proof"
        self.evidence.mkdir(parents=True)
        self.dest = self.root / "app/public/e6-blender-preview.glb"
        self.dest.parent.mkdir(parents=True)
        self.proof = self.root / "target/e6-studio/blender-preview-stage.json"
        self.patches = [
            mock.patch.object(stage, "ROOT", self.root),
            mock.patch.object(stage, "EVIDENCE", self.evidence),
            mock.patch.object(stage, "DEST", self.dest),
            mock.patch.object(stage, "PROOF", self.proof),
        ]
        for p in self.patches:
            p.start()
            self.addCleanup(p.stop)
        self.optimized = tiny_glb()
        self.original = self.optimized + b"four"
        self.blend = b"synthetic-test-only-not-a-real-blend"
        def digest(value):
            return hashlib.sha256(value).hexdigest()
        (self.evidence / "aetheris-command-module.glb").write_bytes(self.original)
        (self.evidence / "aetheris-command-module-no-uv.glb").write_bytes(self.optimized)
        (self.evidence / "aetheris-command-module.blend").write_bytes(self.blend)
        (self.evidence / "aetheris-command-module.manifest.json").write_text(json.dumps({
            "blenderSourceSha256": digest(self.blend),
            "authoringScriptSha256": digest((stage.BLENDER / "author_command_module.py").read_bytes()),
            "sha256": digest(self.original),
            "assetApproved": False,
            "stage45Go": False,
        }))
        (self.evidence / "asset-size-comparison.json").write_text(json.dumps({
            "kind": "E6_BLENDER_NO_UV_OFFLINE_SIZE_COMPARISON",
            "originalSha256": digest(self.original),
            "candidateSha256": digest(self.optimized),
            "trianglePositionNormalDataEqual": True,
            "geometryAndMaterialMetadataEqual": True,
            "semanticPrimitiveContractsEqual": True,
            "candidateVertexCount": 3,
            "candidateUVAttributeSets": 0,
            "meshCount": 1,
            "materialCount": 1,
            "vertexCount": 3,
            "assetAdoptedByR3F": False,
            "stage45Go": False,
        }))

    def test_stages_consistent_fixture_without_release_approval(self):
        result = verified_stage()
        self.assertEqual(self.dest.read_bytes(), self.optimized)
        self.assertEqual(result["kind"], "E6_BLENDER_OPT_IN_RESEARCH_PREVIEW_ONLY")
        self.assertFalse(result["ownerApproved"])
        self.assertFalse(result["physicalGpuVerified"])
        self.assertFalse(result["stage45Go"])
        self.assertFalse(result["productionDeployed"])
        self.assertTrue(result["normalRootAssetUntouched"])

    def test_reject_edited_glb_bytes(self):
        candidate = self.evidence / "aetheris-command-module-no-uv.glb"
        candidate.write_bytes(candidate.read_bytes() + b"tamper")
        with self.assertRaisesRegex(ValueError, "PROVENANCE_OR_GEOMETRY"):
            verified_stage()
        self.assertFalse(self.dest.exists())

    def test_reject_tampered_blend_source(self):
        source = self.evidence / "aetheris-command-module.blend"
        source.write_bytes(source.read_bytes() + b"modified")
        with self.assertRaisesRegex(ValueError, "PROVENANCE_OR_GEOMETRY"):
            verified_stage()
        self.assertFalse(self.dest.exists())

    def test_reject_overwrite(self):
        self.dest.write_bytes(b"original-do-not-touch")
        with self.assertRaisesRegex(ValueError, "REFUSE_REPLACE"):
            verified_stage()
        self.assertEqual(self.dest.read_bytes(), b"original-do-not-touch")

    def test_reject_symlinked_destination(self):
        victim = self.root / "victim"
        victim.write_bytes(b"keep")
        self.dest.symlink_to(victim)
        with self.assertRaisesRegex(ValueError, "SYMLINK_DENIED"):
            verified_stage()
        self.assertEqual(victim.read_bytes(), b"keep")

    def test_only_explicit_build_flag_enables_asset_route(self):
        route = (APP / "app/blender-preview/page.tsx").read_text()
        scene = (APP / "components/Scene3D.tsx").read_text()
        story = (APP / "components/CinematicExperience.tsx").read_text()
        self.assertIn('NEXT_PUBLIC_E6_BLENDER_PREVIEW !== "1"', route)
        self.assertIn('assetVariant="blender"', route)
        self.assertIn('"/e6-blender-preview.glb"', scene)
        self.assertIn('"/research-panel.glb"', scene)
        self.assertIn('assetVariant="fixture"', story)
        self.assertIn('data-asset-variant={assetVariant}', story)
        self.assertNotIn("fetch(", route)
        self.assertNotIn("localStorage", route)
        self.assertNotIn("http://", route)
        self.assertNotIn("https://", route)


if __name__ == "__main__":
    unittest.main()
