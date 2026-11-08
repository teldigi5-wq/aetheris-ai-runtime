"""End-to-end E5 editorial site generation, no model/network/deploy."""
from __future__ import annotations

import hashlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest import mock
import zipfile

import e5_creative_brief_gate as gate
import e5_editorial_multipage as pages
import e5_structured_layout_preview as layout

BRIEF = {
    "version": 1, "brandLabel": "Prototype Atelier",
    "requiredTerms": ["quiet", "motion"], "forbiddenTerms": ["certified"]
}
COPY = {
    "version": 1, "brandLabel": "Prototype Atelier",
    "headlineLines": ["Quiet materials.", "Purposeful motion."]
}
LAYOUTS = [
    {"version": 1, "composition": "gallery", "palette": "paper",
     "typography": "editorial-serif"},
    {"version": 1, "composition": "centered", "palette": "charcoal",
     "typography": "modern-sans"}
]
CONTENT = {
    "version": 1,
    "brandLabel": "Prototype Atelier",
    "project": {
        "eyebrow": "Selected concept",
        "title": "Material and Motion",
        "summary": "A fictional editorial study of proportion, typography and intentional transitions.",
        "process": "The project explores a bounded visual vocabulary and keyboard-first navigation.",
        "result": "A reproducible static prototype for human evaluation, not a delivered client result."
    },
    "contact": {
        "headline": "Discuss a creative brief",
        "message": "This review page exposes a static mail link without forms or analytics.",
        "email": "studio@example.invalid"
    },
}


class ThreePageEditorialTests(unittest.TestCase):
    def test_exact_content_schema_and_canonical_determinism(self):
        accepted = pages.read_content(pages._canonical(CONTENT))
        self.assertEqual(accepted, CONTENT)
        a, manifest = pages.build(BRIEF, COPY, LAYOUTS[0], CONTENT)
        b, second = pages.build(BRIEF, COPY, LAYOUTS[0], CONTENT)
        self.assertEqual(a, b)
        self.assertEqual(manifest, second)
        self.assertFalse(manifest["ownerApproved"])
        self.assertFalse(manifest["siteCodeModelAuthored"])
        self.assertFalse(manifest["humanEditorialClaimsReviewed"])
        self.assertFalse(manifest["deploymentAuthorized"])
        self.assertFalse(manifest["stage45Go"])
        self.assertEqual(manifest["pagePaths"],
                         ["index.html", "work.html", "contact.html"])
        with zipfile.ZipFile(io.BytesIO(a)) as z:
            self.assertIsNone(z.testzip())
            self.assertEqual(set(z.namelist()), set(pages.ALLOWED_PARENT) |
                             {"work.html", "contact.html", "editorial.css"})
            self.assertEqual(json.loads(z.read("preview-manifest.json")), manifest)
            for item in manifest["files"]:
                data = z.read(item["path"])
                self.assertEqual(hashlib.sha256(data).hexdigest(), item["sha256"])
                self.assertEqual(len(data), item["bytes"])
            home = z.read("index.html").decode()
            work = z.read("work.html").decode()
            contact = z.read("contact.html").decode()
            self.assertIn('href="./work.html"', home)
            self.assertIn('href="./contact.html"', home)
            self.assertIn('rel="stylesheet" href="./editorial.css"', home)
            self.assertIn('aria-current="page"', work)
            self.assertIn("Material and Motion", work)
            self.assertIn('href="mailto:studio@example.invalid"', contact)
            self.assertIn('<main id="main"', work)
            self.assertIn('prefers-reduced-motion:reduce', z.read("editorial.css").decode())
            self.assertEqual(layout._git_blob_sha(z.read("scene.js")),
                             layout.PINNED_SCENE_BLOB)
            self.assertEqual(layout._git_blob_sha(z.read("styles.css")),
                             layout.PINNED_STYLES_BLOB)

    def test_two_distinct_design_intents_generate_distinct_real_pages(self):
        first, a = pages.build(BRIEF, COPY, LAYOUTS[0], CONTENT)
        second, b = pages.build(BRIEF, COPY, LAYOUTS[1], CONTENT)
        self.assertNotEqual(hashlib.sha256(first).hexdigest(),
                            hashlib.sha256(second).hexdigest())
        self.assertNotEqual(a["parentPreviewSha256"], b["parentPreviewSha256"])
        with zipfile.ZipFile(io.BytesIO(second)) as z:
            self.assertIn('e5-theme-charcoal', z.read("work.html").decode())

    def test_untrusted_markup_is_only_escaped_text(self):
        candidate = json.loads(json.dumps(CONTENT))
        candidate["project"]["summary"] = (
            "A fictional concept with <script>alert(1)</script> shown only as literal "
            "text, never an executable script in an editorial review page."
        )
        archive, _ = pages.build(BRIEF, COPY, LAYOUTS[0], candidate)
        with zipfile.ZipFile(io.BytesIO(archive)) as z:
            work = z.read("work.html").decode()
            self.assertIn("&lt;script&gt;", work)
            self.assertNotIn("<script>", work)
            self.assertNotIn('src="http', work)

    def test_refuses_brand_smuggling_mail_headers_and_unknown_fields(self):
        invalid = [
            {**CONTENT, "brandLabel": "Wrong Corporation"},
            {**CONTENT, "unexpected": "value"},
            {**CONTENT, "version": True},
            {**CONTENT, "contact": {**CONTENT["contact"],
                                  "email": "studio@example.invalid\nBcc:x"}},
            {**CONTENT, "contact": {**CONTENT["contact"],
                                  "email": "attacker@example.invalid%0aBcc:else"}},
            {**CONTENT, "contact": {**CONTENT["contact"],
                                  "message": "https://evil.example/ injection"}},
            {**CONTENT, "project": {**CONTENT["project"],
                                  "process": "Not allowed\u202e hidden text follows"}},
            {**CONTENT, "project": {**CONTENT["project"], "result": ""}},
        ]
        for item in invalid:
            with self.subTest(item=item), self.assertRaises(ValueError):
                pages.build(BRIEF, COPY, LAYOUTS[0], item)
        with self.assertRaises(ValueError):
            pages.read_content(b'{"version":1,"version":1}')
        with self.assertRaises(ValueError):
            pages.read_content(b"\xff")
        with self.assertRaises(ValueError):
            pages.read_content(b"x" * (pages.MAX_CONTENT_BYTES + 1))

    def test_cli_private_nonoverwrite_and_no_input_path_override(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            brief = root / "creative-brief.json"
            proposed = root / "copy-proposal.json"
            choice = root / "layout-proposal.json"
            content = root / "editorial-content.json"
            target = root / "editorial-three-page-review.zip"
            for file, obj in ((brief, BRIEF), (proposed, COPY),
                              (choice, LAYOUTS[0]), (content, CONTENT)):
                file.write_bytes(pages._canonical(obj))
            common = (mock.patch.object(pages, "FOLDER", root),
                      mock.patch.object(gate, "BRIEF", brief),
                      mock.patch.object(gate, "PROPOSAL", proposed),
                      mock.patch.object(layout, "LAYOUT", choice),
                      mock.patch.object(pages, "CONTENT_FILE", content),
                      mock.patch.object(pages, "OUTPUT", target))
            with (common[0], common[1], common[2], common[3],
                  common[4], common[5], mock.patch("sys.argv", ["editorial.py"])):
                self.assertEqual(pages.main(), 0)
                self.assertEqual(pages.main(), 2)
            self.assertTrue(target.is_file())
            self.assertEqual(target.stat().st_mode & 0o777, 0o600)
            snapshot = target.read_bytes()
            with (common[0], common[1], common[2], common[3],
                  common[4], common[5],
                  mock.patch("sys.argv", ["editorial.py", "../../escape"])):
                self.assertEqual(pages.main(), 2)
            self.assertEqual(snapshot, target.read_bytes())

    def test_no_execution_or_external_resources_introduced(self):
        with mock.patch.object(gate, "build", wraps=gate.build) as checked:
            archive, _ = pages.build(BRIEF, COPY, LAYOUTS[0], CONTENT)
            self.assertTrue(checked.called)
        with zipfile.ZipFile(io.BytesIO(archive)) as z:
            for name in ("work.html", "contact.html"):
                html = z.read(name).decode()
                self.assertNotIn("<script", html.casefold())
                self.assertNotIn("<form", html.casefold())
                self.assertNotIn("https://", html.casefold())
                self.assertNotIn('onerror=', html.casefold())
                self.assertIn('rel="stylesheet" href="./editorial.css"', html)
        self.assertLess(len(archive), 90_000)


if __name__ == "__main__":
    unittest.main()
