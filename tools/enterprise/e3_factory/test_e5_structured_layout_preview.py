"""E5 finite-token layout compiler: no arbitrary model code, no publishing."""
import hashlib
import io
import json
import os
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock

import e5_reference_preview as ref
import e5_structured_layout_preview as layout

BRIEF = {"version": 1, "brandLabel": "Prototype Atelier",
         "requiredTerms": ["quiet", "motion"],
         "forbiddenTerms": ["certified"]}
COPY = {"version": 1, "brandLabel": "Prototype Atelier",
        "headlineLines": ["Quiet materials.", "Purposeful motion."]}
CHOICE = {"version": 1, "composition": "gallery",
          "palette": "paper", "typography": "editorial-serif"}


class StructuredLayoutPreviewTests(unittest.TestCase):
    def test_layout_variants_are_distinct_reproducible_review_only(self):
        sample = set()
        for composition in sorted(layout.OPTIONS["composition"]):
            proposal = {**CHOICE, "composition": composition}
            first, manifest = layout.build(BRIEF, COPY, proposal)
            second, copy_manifest = layout.build(BRIEF, COPY, proposal)
            self.assertEqual(first, second)
            self.assertEqual(manifest, copy_manifest)
            self.assertFalse(manifest["siteCodeModelAuthored"])
            self.assertFalse(manifest["independentModelRunVerified"])
            self.assertFalse(manifest["independentAestheticReviewPassed"])
            self.assertFalse(manifest["ownerApproved"])
            self.assertFalse(manifest["deploymentAuthorized"])
            self.assertFalse(manifest["productionReady"])
            self.assertEqual(manifest["runtimeReleaseStatus"], "NO_GO")
            with zipfile.ZipFile(io.BytesIO(first)) as archive:
                self.assertIsNone(archive.testzip())
                self.assertEqual(set(archive.namelist()), set(ref.FILES) | {
                    "e5-layout.css", "layout-proposal.json", "copy-proposal.json",
                    "preview-manifest.json",
                })
                page = archive.read("index.html").decode()
                self.assertIn('data-e5-composition="' + composition + '"', page)
                self.assertIn('data-e5-palette="paper"', page)
                self.assertIn("Prototype Atelier / UNAPPROVED DRAFT", page)
                self.assertIn('href="./e5-layout.css"', page)
                self.assertEqual(archive.read("scene.js"),
                                 (ref.REFERENCE / "scene.js").read_bytes())
                self.assertEqual(archive.read("styles.css"),
                                 (ref.REFERENCE / "styles.css").read_bytes())
                self.assertEqual(archive.read("e5-layout.css"),
                                 layout.TRUSTED_CSS.encode("utf-8"))
                self.assertEqual(json.loads(archive.read("layout-proposal.json")),
                                 proposal)
                self.assertEqual(json.loads(archive.read("preview-manifest.json")),
                                 manifest)
                for entry in manifest["files"]:
                    self.assertEqual(
                        hashlib.sha256(archive.read(entry["path"])).hexdigest(),
                        entry["sha256"])
                    self.assertEqual(len(archive.read(entry["path"])),
                                     entry["bytes"])
            sample.add(hashlib.sha256(first).hexdigest())
        self.assertEqual(len(sample), 3, "Token choices must result in distinct previews")

    def test_palette_and_typography_are_finite_and_not_raw_css(self):
        proposals = [
            {**CHOICE, "palette": "red; background:url(https://bad.invalid)"},
            {**CHOICE, "composition": 'gallery" onclick="alert(1)'},
            {**CHOICE, "typography": "url(javascript:alert(1))"},
            {**CHOICE, "palette": "paper", "css": "body{display:none}"},
            {**CHOICE, "version": True},
            {**CHOICE, "composition": False},
            {**CHOICE, "palette": None},
            ["gallery", "paper", "serif"],
        ]
        for candidate in proposals:
            with self.subTest(candidate=candidate), self.assertRaises(ValueError):
                layout.read_layout(json.dumps(candidate).encode("utf-8"))
        duplicates = (b'{"version":1,"composition":"gallery",'
                      b'"palette":"paper","palette":"charcoal",'
                      b'"typography":"editorial-serif"}')
        with self.assertRaises(ValueError):
            layout.read_layout(duplicates)
        with self.assertRaises(ValueError):
            layout.read_layout(b' ' * (layout.MAX_LAYOUT_BYTES + 1))
        with self.assertRaises(ValueError):
            layout.read_layout(b"\xff")

    def test_parent_brief_copy_invariants_still_reject_bad_candidates(self):
        for copy in [
            {**COPY, "brandLabel": "Unapproved Impostor"},
            {**COPY, "headlineLines": ["Certified materials.", "Purposeful motion."]},
            {**COPY, "headlineLines": ["Empty materials.", "No movement."]},
            {**COPY, "headlineLines": ["Quiet materials.", "<script>motion</script>"]},
        ]:
            with self.subTest(copy=copy), self.assertRaises(ValueError):
                layout.build(BRIEF, copy, CHOICE)

    def test_reference_script_and_styles_drift_are_not_hidden(self):
        # Layout compilation must rebuild the existing vetted copy preview and
        # assert the reference executable code is unchanged byte for byte.
        with tempfile.TemporaryDirectory() as tmp:
            import shutil
            directory = Path(tmp)
            for name in ref.FILES:
                shutil.copyfile(ref.REFERENCE / name, directory / name)
            (directory / "scene.js").write_text(
                (directory / "scene.js").read_text() + "\nconsole.log('new');")
            with mock.patch.object(ref, "REFERENCE", directory):
                # Parent validator only allows bounded inert reference changes.
                with self.assertRaises(ValueError):
                    layout.build(BRIEF, COPY, CHOICE)

    def test_compiler_has_no_model_network_deployment_or_custom_code_execution(self):
        script = Path(layout.__file__).read_text()
        for forbidden in ("requests.post(", "subprocess.run(", "os.system(",
                          "eval(", "exec(", "model.generate(", "curl ",
                          "https://vercel", "child_process", "script.innerHTML"):
            self.assertNotIn(forbidden, script)
        self.assertNotIn("@import ", layout.TRUSTED_CSS)
        self.assertNotIn("url(", layout.TRUSTED_CSS)
        self.assertIn("prefers-reduced-motion", layout.TRUSTED_CSS)
        self.assertIn("grid-template-columns", layout.TRUSTED_CSS)

    def test_local_cli_refuses_preexisting_preview_and_never_overwrites(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            b = folder / "creative-brief.json"
            c = folder / "copy-proposal.json"
            l = folder / "layout-proposal.json"
            out = folder / "structured-layout-review.zip"
            for path, value in ((b, BRIEF), (c, COPY), (l, CHOICE)):
                path.write_text(json.dumps(value))
            out.write_bytes(b"unchanged")
            with (mock.patch.object(layout, "FOLDER", folder),
                  mock.patch.object(layout, "BRIEF", b),
                  mock.patch.object(layout, "COPY", c),
                  mock.patch.object(layout, "LAYOUT", l),
                  mock.patch.object(layout, "OUTPUT", out),
                  mock.patch("sys.argv", ["e5_structured_layout_preview.py"])):
                self.assertEqual(layout.main(), 2)
            self.assertEqual(out.read_bytes(), b"unchanged")

    def test_local_cli_creates_only_one_private_offline_archive(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            b = folder / "creative-brief.json"
            c = folder / "copy-proposal.json"
            l = folder / "layout-proposal.json"
            out = folder / "structured-layout-review.zip"
            for path, value in ((b, BRIEF), (c, COPY), (l, CHOICE)):
                path.write_text(json.dumps(value))
            with (mock.patch.object(layout, "FOLDER", folder),
                  mock.patch.object(layout, "BRIEF", b),
                  mock.patch.object(layout, "COPY", c),
                  mock.patch.object(layout, "LAYOUT", l),
                  mock.patch.object(layout, "OUTPUT", out),
                  mock.patch("sys.argv", ["e5_structured_layout_preview.py"])):
                self.assertEqual(layout.main(), 0)
            self.assertTrue(out.is_file())
            self.assertEqual(out.stat().st_mode & 0o777, 0o600)
            with zipfile.ZipFile(out) as zip_file:
                self.assertEqual(zip_file.testzip(), None)


if __name__ == "__main__":
    unittest.main()
