"""Adversarial B4G curator separation and sealed-reference integrity tests.

Synthetic local cases are NOT independent benchmark evidence. No network/model.
"""
from __future__ import annotations

import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile

import e39g12b4g_curator_boundary as b4g


def fixture(root: Path, *, site_count=2) -> None:
    (root / "cases").mkdir(parents=True)
    cases = []
    for index in range(4):
        cid = f"case-{index:02d}"
        site = "sample-one" if index < 2 or site_count == 1 else "sample-two"
        folder = root / "cases" / cid
        folder.mkdir()
        broken = (
            "<!doctype html><html><body><main><h1>"
            f"Fixture {index}</h1><p>broken-{index}</p>"
            "</main></body></html>"
        )
        reference = broken.replace(f"broken-{index}", f"corrected-{index}")
        (folder / "broken.html").write_text(broken, encoding="utf-8")
        (folder / "reference.html").write_text(reference, encoding="utf-8")
        cases.append({
            "id": cid, "site": site, "fault": "mobile-layout",
            "defectDescription": "Synthetic mobile layout defect for isolated test.",
            "sourceLicense": "CC0-1.0"
        })
    (root / "manifest.json").write_text(json.dumps({
        "schemaVersion": 1, "independentCuratorDeclared": True, "cases": cases
    }), encoding="utf-8")


class CuratorBoundaryTest(unittest.TestCase):
    def test_split_broken_only_from_private_reference_and_verify_commitment(self):
        with tempfile.TemporaryDirectory() as d:
            base = Path(d)
            root = base / "curator"
            root.mkdir()
            fixture(root)
            public = base / "public-model-only.zip"
            b4g.stage(root, public)
            b4g.verify(root, public)
            with ZipFile(public) as z:
                self.assertEqual(z.namelist(), ["prompts.json"])
                packet = b4g.parse(z.read("prompts.json"))
            self.assertEqual(len(packet["cases"]), 4)
            self.assertFalse(packet["referenceSolutionsIncluded"])
            self.assertFalse(packet["independentCuratorVerified"])
            self.assertFalse(packet["genuinelyBlindOutcomeVerified"])
            self.assertFalse(packet["ownerApprovalGranted"])
            self.assertFalse(packet["productionAuthorized"])
            self.assertEqual(packet["modelCallsPerformed"], 0)
            self.assertNotIn("corrected-0", json.dumps(packet))
            self.assertEqual(
                {x["site"] for x in packet["cases"]},
                {"sample-one", "sample-two"}
            )
            seal = b4g.parse((root / b4g.PRIVATE_NAME).read_bytes())
            self.assertNotIn("saltHex", packet)
            self.assertNotIn(seal["saltHex"], repr(packet))
            self.assertEqual(packet["sealedReferenceCommitment"], seal["commitment"])
            with self.assertRaisesRegex(ValueError, "B4G_PUBLIC_FILE_REPLAY"):
                b4g.stage(root, public)

    def test_changed_reference_rejects_old_commitment(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d) / "curator"
            root.mkdir()
            fixture(root)
            output = Path(d) / "model.zip"
            b4g.stage(root, output)
            ref = root / "cases" / "case-01" / "reference.html"
            ref.write_text(ref.read_text().replace("corrected", "tampered"))
            with self.assertRaisesRegex(ValueError, "B4G_PRIVATE_EVIDENCE_DRIFT"):
                b4g.verify(root, output)

    def test_changed_public_zip_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d) / "curator"
            root.mkdir()
            fixture(root)
            output = Path(d) / "model.zip"
            b4g.stage(root, output)
            with ZipFile(output, "a") as z:
                z.writestr("reference.html", "SHOULD NEVER BE SENT")
            with self.assertRaisesRegex(ValueError, "B4G_COMMITMENT_MISMATCH"):
                b4g.verify(root, output)

    def test_public_zip_decompression_limit_even_with_rewritten_digest(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d) / "curator"
            root.mkdir()
            fixture(root)
            output = Path(d) / "model.zip"
            b4g.stage(root, output)
            compressed = io.BytesIO()
            with ZipFile(compressed, "w") as archive:
                archive.writestr("prompts.json", "a" * (b4g.MAX_TOTAL + 20000),
                                 compress_type=8)
            poisoned = compressed.getvalue()
            self.assertLess(len(poisoned), b4g.MAX_TOTAL)
            output.write_bytes(poisoned)
            seal_path = root / b4g.PRIVATE_NAME
            seal = json.loads(seal_path.read_text(encoding="utf-8"))
            # Even a hypothetically corrupt local digest cannot bypass the
            # independent public ZIP member uncompressed-size budget.
            seal["publicBundleSha256"] = b4g.digest(poisoned)
            seal_path.write_text(json.dumps(seal), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "B4G_MODEL_ZIP_DECOMPRESSION_LIMIT"):
                b4g.verify(root, output)

    def test_symlinked_case_source_denied(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d) / "curator"
            root.mkdir()
            fixture(root)
            source = root / "cases" / "case-00" / "broken.html"
            source.unlink()
            try:
                source.symlink_to(root / "cases" / "case-00" / "reference.html")
            except (OSError, NotImplementedError):
                self.skipTest("Symlink creation not available on this host")
            with self.assertRaisesRegex(ValueError, "B4G_UNSAFE_OR_MISSING_FILE"):
                b4g.stage(root, Path(d) / "model.zip")

    def test_same_source_reference_denied(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d) / "curator"
            root.mkdir()
            fixture(root)
            folder = root / "cases" / "case-00"
            (folder / "reference.html").write_bytes((folder / "broken.html").read_bytes())
            with self.assertRaisesRegex(ValueError, "B4G_MISSING_REFERENCE_DIFFERENCE"):
                b4g.stage(root, Path(d) / "model.zip")

    def test_one_site_corpus_denied(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d) / "curator"
            root.mkdir()
            fixture(root, site_count=1)
            with self.assertRaisesRegex(ValueError, "B4G_NEED_DISTINCT_WEBSITES"):
                b4g.stage(root, Path(d) / "model.zip")

    def test_missing_independent_curator_declaration_denied(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d) / "curator"
            root.mkdir()
            fixture(root)
            p = root / "manifest.json"
            item = json.loads(p.read_text(encoding="utf-8"))
            item["independentCuratorDeclared"] = False
            p.write_text(json.dumps(item))
            with self.assertRaisesRegex(ValueError, "B4G_REQUIRE_CURATOR_DECLARATION"):
                b4g.stage(root, Path(d) / "model.zip")

    def test_reject_duplicate_id_and_traversal(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d) / "curator"
            root.mkdir()
            fixture(root)
            p = root / "manifest.json"
            obj = json.loads(p.read_text(encoding="utf-8"))
            obj["cases"][1]["id"] = obj["cases"][0]["id"]
            p.write_text(json.dumps(obj))
            with self.assertRaisesRegex(ValueError, "B4G_DUPLICATE_CASE"):
                b4g.stage(root, Path(d) / "model.zip")
            obj["cases"][1]["id"] = "../escaped"
            p.write_text(json.dumps(obj))
            with self.assertRaisesRegex(ValueError, "B4G_CASE_IDENTIFIER"):
                b4g.stage(root, Path(d) / "model.zip")

    def test_public_bundle_cannot_be_written_inside_curator(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d) / "curator"
            root.mkdir()
            fixture(root)
            with self.assertRaisesRegex(ValueError, "B4G_NO_MODEL_ZIP_INSIDE_CURATOR"):
                b4g.stage(root, root / "public.zip")

    def test_duplicate_json_keys_denied(self):
        with self.assertRaisesRegex(ValueError, "B4G_DUPLICATE_JSON_KEY"):
            b4g.parse(b'{"cases":[],"cases":[]}')


if __name__ == "__main__":
    unittest.main()
