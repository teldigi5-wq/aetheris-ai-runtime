"""E3 deterministic factory unit and output integrity tests; stdlib only."""
import hashlib
import json
import re
import unittest

import worker


class FactoryTests(unittest.TestCase):
    def test_schema_and_html_escape(self):
        brief = worker.validate_brief(json.loads(
            (worker.HERE / "frozen-brief.json").read_text("utf-8")
        ))
        altered = dict(brief, title='<img src=x onerror="alert(1)">')
        rendered = worker.render_html(altered, "[[TITLE]]")
        self.assertIn("&lt;img", rendered)
        self.assertNotIn("<img", rendered)
        self.assertNotIn('onerror="', rendered)

    def test_project_slug_and_keys_restricted(self):
        spec = json.loads((worker.HERE / "frozen-brief.json").read_text("utf-8"))
        for invalid in ("../credential", "site/name", "Site Name"):
            with self.subTest(invalid=invalid):
                with self.assertRaisesRegex(ValueError, "E3_PROJECT_ID_INVALID"):
                    worker.validate_brief(dict(spec, project_id=invalid))
        with self.assertRaisesRegex(ValueError, "E3_BRIEF_SCHEMA_INVALID"):
            worker.validate_brief(dict(spec, executable="bash -c curl"))
        with self.assertRaisesRegex(ValueError, "E3_BRIEF_FEATURES_INVALID"):
            worker.validate_brief(dict(spec, features=["feature"]))

    def test_checked_in_templates_have_no_network_or_script_injection(self):
        html = (worker.HERE / "template.html").read_text("utf-8")
        js = (worker.HERE / "main.js").read_text("utf-8")
        css = (worker.HERE / "styles.css").read_text("utf-8")
        self.assertIn('id="experience"', html)
        self.assertIn('id="scroll-progress"', html)
        self.assertIn('prefers-reduced-motion:reduce', css)
        self.assertIn('requestAnimationFrame', js)
        self.assertIn('matchMedia', js)
        for forbidden in ("fetch(", "XMLHttpRequest", "eval(", "new Function(",
                          "document.cookie", "localStorage", "WebSocket(", "<iframe"):
            self.assertNotIn(forbidden, html + js)

    def test_generated_output_is_complete_and_checksummed(self):
        output = worker.TARGET
        self.assertTrue(output.is_dir(), "CI must run worker.py before tests")
        self.assertEqual({p.name for p in output.iterdir()}, worker.OUTPUTS)
        report = json.loads((output / "manifest.json").read_text("utf-8"))
        self.assertEqual(report["evidenceState"],
                         "CI_ONLY_WEBSITE_SOURCE_GENERATED_NOT_DEPLOYED")
        self.assertTrue(report["workerExecuted"])
        for claim in ("webBrowserQAVerifiedByWorker", "blenderRenderedFramesPresent",
                      "threeJsOrWebglScenePresent", "realOwnerApprovalGranted",
                      "productionPublished"):
            self.assertFalse(report[claim])
        for row in (output / "SHA256SUMS").read_text("ascii").splitlines():
            digest, filename = row.split("  ")
            self.assertRegex(digest, r"^[0-9a-f]{64}$")
            self.assertEqual(hashlib.sha256((output / filename).read_bytes()).hexdigest(), digest)
        self.assertIn("The Shape of Motion", (output / "index.html").read_text("utf-8"))

    def test_worker_never_overwrites_existing_site(self):
        with self.assertRaisesRegex(ValueError, "E3_OUTPUT_ALREADY_EXISTS_OR_LINK"):
            worker.build()


if __name__ == "__main__":
    unittest.main()
