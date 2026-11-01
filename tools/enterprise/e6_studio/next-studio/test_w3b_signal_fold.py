"""W3-B tests use a real previously exported Blender artifact, never mock it.
Checks preserve W3-A and block permission forging, source tampering and overwrite.
"""
import copy
import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock
sys.path.insert(0,str(Path(__file__).resolve().parent))
import w3b_verify_signal_fold as proof
import w3b_stage_signal_fold as stage

class SignalFoldProofTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.inputs=proof.load()
    def test_brand_specific_real_blender_geometry_source_bound(self):
        p=proof.validate(*self.inputs)
        self.assertEqual(p["meshCount"],7)
        self.assertEqual(p["materialCount"],5)
        self.assertEqual(p["glbSha256"],hashlib.sha256(self.inputs[2]).hexdigest())
        self.assertEqual(p["fictionalStudy"],"Signal Fold")
        self.assertTrue(p["sourceAndGeometryIntegrityVerified"])
    def test_no_unverified_release_model_or_physical_claims(self):
        p=proof.validate(*self.inputs)
        for key in ("modelGenerated","visualApproved","licenseIndependentlyCertified",
                    "physicalPrototype","physicalGpuMeasured","ownerApproved",
                    "deploymentAuthorized","releaseGo","stage45Go"):
            self.assertIs(p[key],False,key)
    def test_source_mutation_rejected(self):
        inp=list(copy.deepcopy(self.inputs))
        inp[0]+=b"\n# attacker mutation"
        with self.assertRaisesRegex(ValueError,"SOURCE_OR_GEOMETRY"):
            proof.validate(*inp)
    def test_glb_tamper_denied(self):
        inp=list(copy.deepcopy(self.inputs))
        bad=bytearray(inp[2]);bad[-8]^=1
        inp[2]=bytes(bad)
        with self.assertRaises(ValueError):
            proof.validate(*inp)
    def test_forced_approval_denied(self):
        inp=list(copy.deepcopy(self.inputs))
        inp[3]["ownerApproved"]=True
        with self.assertRaisesRegex(ValueError,"INCOMPLETE_OR_FORGED"):
            proof.validate(*inp)
    def test_material_manifest_mismatch_denied(self):
        inp=list(copy.deepcopy(self.inputs))
        inp[3]["materialNames"]=["fake-licensed"]
        with self.assertRaisesRegex(ValueError,"MATERIAL_MISMATCH"):
            proof.validate(*inp)
    def test_reject_symlinked_source_assets(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); (root/"safe.glb").write_bytes(b"test")
            (root/"bad.glb").symlink_to(root/"safe.glb")
            with self.assertRaisesRegex(ValueError,"UNTRUSTED_FILE"):
                proof.regular(root/"bad.glb",100)
    def test_website_isolated_stage_and_no_overwrite(self):
        original=(stage.SOURCE/stage.DETAIL).read_bytes()
        original_evidence=(stage.SOURCE/"assets/w3a-provenance.json").read_bytes()
        with tempfile.TemporaryDirectory() as tmp:
            destination=Path(tmp)/"output"
            with mock.patch.object(stage,"DEST",destination):
                result=stage.stage()
                self.assertEqual(result["w3aPreviewUnchanged"],True)
                self.assertIs(result["renderedInSite"],False)
                site=(destination/stage.DETAIL).read_text()
                self.assertIn("Original concept geometry",site)
                self.assertIn("not a manufactured",site)
                self.assertIn('href="/assets/arcloom-signal-fold.glb"',site)
                actual=(destination/"assets/arcloom-signal-fold.glb").read_bytes()
                self.assertEqual(actual,self.inputs[2])
                with self.assertRaisesRegex(ValueError,"NO_OVERWRITE"):
                    stage.stage()
        self.assertEqual(original,(stage.SOURCE/stage.DETAIL).read_bytes())
        self.assertEqual(original_evidence,(stage.SOURCE/"assets/w3a-provenance.json").read_bytes())
    def test_w3b_staging_refuses_forged_parent_evidence(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)/"source"
            root.mkdir()
            (root/"assets").mkdir()
            (root/"assets/w3a-provenance.json").write_text('{"releaseGo":true}')
            with mock.patch.object(stage,"SOURCE",root),mock.patch.object(stage,"DEST",Path(tmp)/"new"):
                with self.assertRaisesRegex(ValueError,"INVALID_W3A_EVIDENCE"):
                    stage.stage()
    def test_no_external_media_or_unreviewed_gpu_authority_in_stage(self):
        output=proof.validate(*self.inputs)
        self.assertEqual(output["externalMedia"],False)
        self.assertEqual(output["sourceClaim"],"FIRST_PARTY_REPO_SCRIPT_OWNER_REVIEW_PENDING")
        self.assertLess(output["glbBytes"],2_000_000)
        self.assertGreater(output["vertexCount"],0)

if __name__=="__main__":
    unittest.main()
