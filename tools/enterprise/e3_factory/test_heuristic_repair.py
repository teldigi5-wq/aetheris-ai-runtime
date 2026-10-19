"""E3.3 negative security and heuristic generalization tests. stdlib unittest only."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock
import heuristic_repair as h


class HeuristicProposalTests(unittest.TestCase):
    def setUp(self):
        self.t = tempfile.TemporaryDirectory()
        self.addCleanup(self.t.cleanup)
        self.root = Path(self.t.name)
        self.site = self.root / "site"
        self.site.mkdir()
        self.current = self.root / "current"
        self.current.mkdir()

    def setup_case(self, category, broken):
        filename = h.CATEGORIES[category][0]
        source = self.site / filename
        source.write_text(broken, encoding="utf-8")
        # Separate clean contract for independent gate verification.
        if category == "scroll":
            clean = broken.replace("const p = 0.17;", "const p = progress();")
        elif category == "mobile":
            clean = broken.replace("html{min-width:917px}", "")
        else:
            clean = broken.replace("const reduced = Boolean(false);",
                                   "const reduced = motion.matches;")
        (self.site / "manifest.json").write_text(json.dumps(
            {"generatedFilesSha256": {filename: h.digest(clean.encode())}}), encoding="utf-8")
        report = {"evidenceClass": "E32_REAL_CHROMIUM_FAULT_CONTRACT",
                  "caseId": category, "failureCode": h.CATEGORIES[category][1],
                  "passed": False, "browserObserved": {"progress": 0, "motion": "enabled"}}
        (self.current / "diagnosis.json").write_text(json.dumps(report), "utf-8")
        self.setups = [mock.patch.object(h, "artifact", return_value=source),
                       mock.patch.object(h, "diagnosis", return_value=(self.current / "diagnosis.json").read_bytes()),
                       mock.patch.object(h, "CURRENT", self.current),
                       mock.patch.object(h, "SITE", self.site)]
        for p in self.setups:
            p.start()
            self.addCleanup(p.stop)
        return source, clean

    def test_detects_numeric_scroll_without_known_fixture_value(self):
        src = "function progress() {return 1;}\n  const p = 0.17;\n"
        self.assertEqual(h.heuristic("scroll", src), ("  const p = 0.17;",
                      "  const p = progress();"))

    def test_detects_mobile_overflow_without_known_fixture_value(self):
        s = "@media(max-width:760px){html{min-width:917px}body{color:#fff}}"
        self.assertEqual(h.heuristic("mobile", s), ("html{min-width:917px}", ""))

    def test_detects_reduced_motion_boolean_false(self):
        s = 'const motion = window.matchMedia("x");\n const reduced = Boolean(false);'
        self.assertEqual(h.heuristic("reduced", s),
                         (" const reduced = Boolean(false);",
                          " const reduced = motion.matches;"))

    def test_unsupported_defect_abstains_instead_of_fake_repair(self):
        self.assertIsNone(h.heuristic("scroll", "const p = somethingUnknown();"))
        self.assertIsNone(h.heuristic("reduced", "const reduced = 2 + 2;"))
        self.assertIsNone(h.heuristic("mobile", "@media(max-width:760px){html{zoom:3}}"))

    def test_actual_proposal_and_separate_apply_gate(self):
        source, clean = self.setup_case("scroll",
             "function progress() {return 1;}\nconst p = 0.17;\n")
        proposal = h.proposal("scroll")
        self.assertEqual(proposal["path"], "site/main.js")
        receipt = h.apply("scroll")
        self.assertEqual(source.read_text("utf-8"), clean)
        self.assertTrue(receipt["untrustedProposalValidated"])
        self.assertFalse(receipt["modelWasRun"])

    def test_wrong_destination_path_denied(self):
        self.setup_case("scroll", "function progress(){return 1;}\nconst p = 0.17;")
        h.proposal("scroll")
        p = self.current / "proposal.json"
        data = json.loads(p.read_text())
        data["path"] = "../../secrets.env"
        p.write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError, "E33_UNTRUSTED_PATCH_REJECTED"):
            h.apply("scroll")

    def test_new_script_payload_and_extra_keys_denied(self):
        self.setup_case("scroll", "function progress(){return 1;}\nconst p = 0.17;")
        h.proposal("scroll")
        p = self.current / "proposal.json"
        data = json.loads(p.read_text())
        data["new"] = 'fetch("https://evil.invalid")'
        p.write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError, "E33_PATCH_GRAMMAR_DENIED"):
            h.apply("scroll")
        data["new"] = "const p = progress();"
        data["runShell"] = "sh"
        p.write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError, "E33_UNTRUSTED_PATCH_REJECTED"):
            h.apply("scroll")

    def test_wrong_digest_denied(self):
        self.setup_case("scroll", "function progress(){return 1;}\nconst p = 0.17;")
        h.proposal("scroll")
        p = self.current / "proposal.json"
        data = json.loads(p.read_text())
        data["baseSha256"] = "0" * 64
        p.write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError, "E33_BASE_HASH_MISMATCH"):
            h.apply("scroll")

    def test_oversized_candidate_denied(self):
        self.setup_case("scroll", "function progress(){return 1;}\nconst p = 0.17;")
        h.proposal("scroll")
        (self.current / "proposal.json").write_text("x" * 3000)
        with self.assertRaisesRegex(ValueError, "E33_SOURCE_OR_EVIDENCE_NOT_REGULAR"):
            h.apply("scroll")

    def test_symlinked_proposal_denied(self):
        self.setup_case("scroll", "function progress(){return 1;}\nconst p = 0.17;")
        h.proposal("scroll")
        p = self.current / "proposal.json"
        backup = self.current / "back.json"
        backup.write_bytes(p.read_bytes())
        p.unlink()
        try:
            p.symlink_to(backup)
        except OSError:
            self.skipTest("symlink creation not available")
        with self.assertRaisesRegex(ValueError, "E33_SOURCE_OR_EVIDENCE_NOT_REGULAR"):
            h.apply("scroll")

    def test_invalid_category_or_phase_rejected(self):
        for args in ([], ["repair", "scroll"], ["propose", "hidden-case-id"],
                     ["apply", "scroll", "../x"]):
            with self.subTest(args=args):
                with self.assertRaisesRegex(ValueError, "E33_UNSUPPORTED_PHASE_OR_CATEGORY"):
                    h.main(args)


if __name__ == "__main__":
    unittest.main()
