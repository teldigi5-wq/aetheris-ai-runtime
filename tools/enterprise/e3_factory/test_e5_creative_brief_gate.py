"""Enterprise E5: an INDEPENDENT, bounded creative brief is not approval."""
import hashlib
import io
import json
import tempfile
import zipfile
from pathlib import Path
from unittest import TestCase, main
from unittest.mock import patch

import e5_creative_brief_gate as gate
import e5_untrusted_copy_preview as preview
import e5_reference_preview as ref

BRIEF = {
    "version": 1, "brandLabel": "Prototype Atelier",
    "requiredTerms": ["quiet", "motion"],
    "forbiddenTerms": ["guaranteed", "certified"]
}
COPY = {
    "version": 1, "brandLabel": "Prototype Atelier",
    "headlineLines": ["Quiet materials.", "Purposeful motion."]
}


class E5CreativeBriefConstraintTests(TestCase):
    def test_independent_brief_yields_deterministic_inert_artifact_and_chain_of_custody(self):
        data, report = gate.build(BRIEF, COPY)
        again, repeated = gate.build(BRIEF, COPY)
        self.assertEqual(data, again)
        self.assertEqual(report, repeated)
        evidence = json.loads(report)
        self.assertEqual(evidence["decision"],
                         "LEXICAL_CONSTRAINT_MATCH_ONLY_NOT_APPROVAL")
        self.assertEqual(evidence["previewZipSha256"], hashlib.sha256(data).hexdigest())
        self.assertEqual(evidence["requiredTermCount"], 2)
        self.assertEqual(evidence["forbiddenTermCount"], 2)
        for value in ("briefAuthenticityVerified", "copyAuthenticityVerified",
                      "ownerApproved", "deploymentAuthorized", "productionReady"):
            self.assertIs(evidence[value], False)
        self.assertIsNone(evidence["modelAuthored"])
        self.assertEqual(evidence["stage45Status"], "NO_GO")
        with zipfile.ZipFile(io.BytesIO(data)) as arc:
            self.assertIsNone(arc.testzip())
            self.assertEqual(set(arc.namelist()),
                set(ref.FILES) | {"copy-proposal.json", "preview-manifest.json"})
            self.assertEqual(arc.read("scene.js"),
                             (ref.REFERENCE / "scene.js").read_bytes())
            self.assertEqual(arc.read("styles.css"),
                             (ref.REFERENCE / "styles.css").read_bytes())
            self.assertIn(b"UNAPPROVED DRAFT", arc.read("index.html"))
            self.assertEqual(json.loads(arc.read("copy-proposal.json")), COPY)
            manifest = json.loads(arc.read("preview-manifest.json"))
            self.assertIsNone(manifest["modelAuthored"])
            self.assertIs(manifest["productionReady"], False)
            self.assertIs(manifest["deploymentAuthorized"], False)

    def test_brand_required_terms_and_forbidden_terms_reject_conflicting_candidate(self):
        failures = [
            ({**COPY, "brandLabel": "Other Studio"}, "BRAND_MISMATCH"),
            ({**COPY, "headlineLines": ["Quiet materials.", "Gentle light."]},
             "REQUIRED_TERM_ABSENT"),
            ({**COPY, "headlineLines": ["Quiet motion.", "Certified quality."]},
             "FORBIDDEN_TERM_PRESENT"),
            ({**COPY, "headlineLines": ["Quiet emotions.", "Gentle light."]},
             "REQUIRED_TERM_ABSENT"),  # not a substring classifier
            ({**COPY, "headlineLines": ["Quiet materials.", "<script>motion</script>"]},
             "DISALLOWED_MARKUP"),
            ({**COPY, "headlineLines": ["Quiet materials.", "https://motion.invalid"]},
             "DISALLOWED_MARKUP"),
        ]
        for proposal, msg in failures:
            with self.subTest(problem=msg), self.assertRaisesRegex(ValueError, msg):
                gate.build(BRIEF, proposal)

    def test_brief_rejects_duplicate_keys_extra_capabilities_unicode_and_overbudget(self):
        examples = [
            b'{"version":1,"brandLabel":"Studio","requiredTerms":["quiet"],"requiredTerms":["light"],"forbiddenTerms":[]}',
            b'{"version":1,"brandLabel":"Studio","requiredTerms":["quiet"],"forbiddenTerms":[],"deploy":true}',
            b'{"version":true,"brandLabel":"Studio","requiredTerms":["quiet"],"forbiddenTerms":[]}',
            b'{"version":1,"brandLabel":"Studio","requiredTerms":[],"forbiddenTerms":[]}',
            b'{"version":1,"brandLabel":"Studio","requiredTerms":["quiet","quiet"],"forbiddenTerms":[]}',
            b'{"version":1,"brandLabel":"Studio","requiredTerms":["quiet"],"forbiddenTerms":["quiet"]}',
            b'{"version":1,"brandLabel":"Studio","requiredTerms":["quiet"],"forbiddenTerms":["bad term"]}',
            b'{"version":1,"brandLabel":"Studio","requiredTerms":["Quiet"],"forbiddenTerms":[]}',
            b'{"version":1,"brandLabel":"Studio","requiredTerms":["quiet"],"forbiddenTerms":["script.exe"]}',
            b'{"version":1,"brandLabel":"Studio","requiredTerms":["quiet"],"forbiddenTerms":["click"]}' + b' '*1300,
            b"\xff",
            b"[]",
        ]
        for raw in examples:
            with self.subTest(raw=raw[:80]), self.assertRaises(ValueError):
                gate.read_brief(raw)

    def test_brief_rejects_non_ascii_ambiguous_values_and_type_coercion(self):
        for value in (True, 1, None, ["quiet"], {"$exec": "deploy"}):
            with self.subTest(value=value):
                payload = {**BRIEF, "requiredTerms": value}
                with self.assertRaises(ValueError):
                    gate.read_brief(json.dumps(payload).encode("utf-8"))
        for label in ("Studio <script>", "Studio https://example.invalid",
                      " Studio", "Studio\u202eBAD"):
            with self.subTest(label=label), self.assertRaises(ValueError):
                gate.read_brief(json.dumps({**BRIEF, "brandLabel": label}).encode())

    def test_failed_review_never_creates_or_overwrites_output(self):
        with tempfile.TemporaryDirectory() as folder:
            directory = Path(folder)
            brief = directory / "creative-brief.json"
            proposal = directory / "copy-proposal.json"
            zip_out = directory / "brief-gated-copy-preview.zip"
            audit = directory / "brief-gated-copy-evidence.json"
            brief.write_text(json.dumps(BRIEF))
            proposal.write_text(json.dumps({**COPY, "brandLabel": "Untrusted Other"}))
            zip_out.write_bytes(b"original")
            audit.write_bytes(b"original-audit")
            with patch.object(gate, "FOLDER", directory), \
                 patch.object(gate, "BRIEF", brief), \
                 patch.object(gate, "PROPOSAL", proposal), \
                 patch.object(gate, "ZIP_OUT", zip_out), \
                 patch.object(gate, "AUDIT_OUT", audit), \
                 patch("sys.argv", ["e5_creative_brief_gate.py"]):
                self.assertEqual(gate.main(), 2)
                self.assertEqual(zip_out.read_bytes(), b"original")
                self.assertEqual(audit.read_bytes(), b"original-audit")

    def test_local_input_and_output_symlinks_cannot_be_trusted(self):
        with tempfile.TemporaryDirectory() as folder:
            directory = Path(folder)
            target = directory / "actual.json"
            target.write_text(json.dumps(BRIEF))
            link = directory / "creative-brief.json"
            link.symlink_to(target)
            with self.assertRaisesRegex(ValueError, "SYMLINK"):
                gate._read_regular(link, gate.MAX_BRIEF_BYTES)
            self.assertFalse(gate._contains_term("emotions", "motion"))
            self.assertTrue(gate._contains_term("Quiet motion.", "motion"))
            self.assertFalse(gate._contains_term("uncertified", "certified"))

    def test_source_is_not_an_inference_service_or_deployment_tool(self):
        source = Path(gate.__file__).read_text()
        for forbidden in ("requests.post(", "subprocess.run(",
                          "os.system(", "eval(", "exec(", "socket.connect(",
                          "vercel deploy", "model.generate("):
            self.assertNotIn(forbidden, source)
        self.assertIn("modelAuthored", source)
        self.assertIn('"ownerApproved": False', source)
        self.assertIn('"productionReady": False', source)
        self.assertIn("reference.PROJECT", source)


if __name__ == "__main__":
    main()
