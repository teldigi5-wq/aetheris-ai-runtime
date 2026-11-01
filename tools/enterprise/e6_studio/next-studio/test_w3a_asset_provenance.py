"""E6 W3-A provenance research tests; real exported Blender artifact is CI prerequisite."""
import copy
import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import w3a_stage_provenance as w3a


class W3AssetGateTests(unittest.TestCase):
    def test_asset_lab_clear_nonproduction_disclosure_no_remote_code(self):
        sample={"stagedGlbSha256":"a"*64}
        html=w3a.asset_lab_page(sample)
        self.assertIn("not a model of any Arcloom product",html)
        self.assertIn("No GPU scene",html)
        self.assertIn("noindex,nofollow",html)
        self.assertIn("Content-Security-Policy",html)
        self.assertIn('href="/assets/aetheris-command-module-no-uv.glb"',html)
        self.assertNotIn("https://",html)
        self.assertNotIn("<script",html)

    def test_file_reader_denies_symlink_and_oversized_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            (root/"safe.bin").write_bytes(b"asset")
            (root/"alias.bin").symlink_to(root/"safe.bin")
            self.assertEqual(w3a.read_regular(root,"safe.bin",limit=4),b"asset")
            with self.assertRaisesRegex(ValueError,"UNTRUSTED"):
                w3a.read_regular(root,"alias.bin")
            with self.assertRaisesRegex(ValueError,"FILE_BUDGET"):
                w3a.read_regular(root,"safe.bin",limit=2)

    def test_copy_source_preflight_denies_links(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            (root/"index.html").write_text("ok")
            (root/"link").symlink_to(root/"index.html")
            with self.assertRaisesRegex(ValueError,"UNTRUSTED_PREVIEW_ENTRY"):
                w3a.ensure_original_files(root)

    @classmethod
    def setUpClass(cls):
        root=w3a.ASSET_PROOF
        cls.contents=[
            w3a.read_regular(w3a.BLENDER,w3a.SCRIPT,limit=50_000),
            w3a.read_regular(root,w3a.BLEND,limit=5_000_000),
            w3a.read_regular(root,w3a.ORIGINAL),
            w3a.read_regular(root,w3a.CANDIDATE),
            w3a.read_evidence(root,w3a.METADATA),
            w3a.read_evidence(root,w3a.COMPARISON),
            w3a.read_evidence(root,w3a.DETAILS)
        ]

    def test_actual_blender_source_and_geometry_pass(self):
        result=w3a.validate_evidence(*self.contents)
        self.assertEqual(result["meshCount"],12)
        self.assertEqual(result["materialCount"],6)
        self.assertGreater(result["triangleCount"],756)
        self.assertLess(result["stagedBytes"],result["originalBytes"])
        self.assertEqual(result["stagedGlbSha256"],hashlib.sha256(self.contents[3]).hexdigest())
        for key in ["ownerApproved","releaseGo","humanVisualApproval",
                    "genuineModelAuthorshipProven","physicalGpuMeasured"]:
            self.assertIs(result[key],False,key)

    def test_tampered_asset_fails(self):
        things=copy.deepcopy(self.contents)
        raw=bytearray(things[3])
        raw[-4] ^= 0x01
        things[3]=bytes(raw)
        with self.assertRaises(ValueError):
            w3a.validate_evidence(*things)

    def test_forged_release_permission_denied(self):
        things=copy.deepcopy(self.contents)
        things[4]["ownerApproved"]=True
        with self.assertRaisesRegex(ValueError,"PREMATURE_AUTHORITY"):
            w3a.validate_evidence(*things)

    def test_untrusted_source_hash_and_geometry_claim_denied(self):
        things=copy.deepcopy(self.contents)
        things[4]["authoringScriptSha256"]="0"*64
        with self.assertRaisesRegex(ValueError,"HASH_OR_PARITY"):
            w3a.validate_evidence(*things)
        things=copy.deepcopy(self.contents)
        things[5]["trianglePositionNormalDataEqual"]=False
        with self.assertRaisesRegex(ValueError,"HASH_OR_PARITY"):
            w3a.validate_evidence(*things)

    def test_reject_repeat_stage_and_preserve_w2c(self):
        before=w3a.read_regular(w3a.W2C_PREVIEW,"build-evidence.json")
        with tempfile.TemporaryDirectory() as tmp:
            dest=Path(tmp)/"w3-preview"
            with mock.patch.object(w3a,"W3_PREVIEW",dest):
                result=w3a.stage()
                self.assertTrue(result["w2cOriginalUntouched"])
                self.assertEqual((dest/"assets"/w3a.CANDIDATE).read_bytes(),self.contents[3])
                self.assertIn("Asset provenance", (dest/"asset-lab/index.html").read_text())
                with self.assertRaisesRegex(ValueError,"NO_OVERWRITE"):
                    w3a.stage()
        after=w3a.read_regular(w3a.W2C_PREVIEW,"build-evidence.json")
        self.assertEqual(before,after)


if __name__=="__main__":
    unittest.main()
