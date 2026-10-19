"""E3.2 negative tests for untrusted diff proposals, evidence and path boundaries."""
import json
import os
from pathlib import Path
import tempfile
import unittest

import multi_fault


class FrozenMultiFaultTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def case(self, name="scroll"):
        rule, original, broken = multi_fault.fixed_source(name)
        target = self.root / rule["filename"]
        target.write_bytes(original)
        folder = self.root / ("evidence-" + name)
        return target, folder, original, broken

    def diagnosis(self, folder, name, code=None):
        data = {
            "evidenceClass": "E32_REAL_CHROMIUM_FAULT_CONTRACT",
            "caseId": name, "passed": False,
            "failureCode": code or multi_fault.CASES[name]["failureCode"],
        }
        (folder / "diagnosis.json").write_text(json.dumps(data), "utf-8")

    def test_all_three_fixed_faults_restore_exact_bytes(self):
        for name in multi_fault.CASES:
            with self.subTest(name=name):
                target, evidence, clean, broken = self.case(name)
                multi_fault.seed(name, target, evidence)
                self.assertEqual(target.read_bytes(), broken)
                self.diagnosis(evidence, name)
                result = multi_fault.repair(name, target, evidence)
                self.assertEqual(target.read_bytes(), clean)
                self.assertFalse(result["ownerApprovalGranted"])
                self.assertFalse(result["modelProposedPatch"])

    def test_repair_rejects_missing_browser_proof(self):
        target, evidence, _, _ = self.case()
        multi_fault.seed("scroll", target, evidence)
        with self.assertRaisesRegex(ValueError, "E32_REQUIRED_EVIDENCE_MISSING_OR_LINKED"):
            multi_fault.repair("scroll", target, evidence)

    def test_unrelated_real_browser_failure_not_sufficient(self):
        target, evidence, _, _ = self.case()
        multi_fault.seed("scroll", target, evidence)
        self.diagnosis(evidence, "scroll", "E32_MOBILE_HORIZONTAL_OVERFLOW")
        with self.assertRaisesRegex(ValueError, "E32_BROWSER_DIAGNOSIS_MISMATCH"):
            multi_fault.repair("scroll", target, evidence)

    def test_mutated_proposal_path_denied(self):
        target, evidence, _, _ = self.case()
        multi_fault.seed("scroll", target, evidence)
        self.diagnosis(evidence, "scroll")
        p = evidence / "proposal.json"
        proposal = json.loads(p.read_text("utf-8"))
        proposal["path"] = "../../../../secrets.env"
        p.write_text(json.dumps(proposal), "utf-8")
        with self.assertRaisesRegex(ValueError, "E32_UNTRUSTED_PATCH_REJECTED"):
            multi_fault.repair("scroll", target, evidence)

    def test_injected_code_denied_even_if_proposal_other_fields_match(self):
        target, evidence, _, _ = self.case()
        multi_fault.seed("scroll", target, evidence)
        self.diagnosis(evidence, "scroll")
        p = evidence / "proposal.json"
        proposal = json.loads(p.read_text("utf-8"))
        proposal["replace"] = 'fetch("https://evil.invalid")'
        p.write_text(json.dumps(proposal), "utf-8")
        with self.assertRaisesRegex(ValueError, "E32_UNTRUSTED_PATCH_REJECTED"):
            multi_fault.repair("scroll", target, evidence)

    def test_forged_source_sha_denied(self):
        target, evidence, _, _ = self.case()
        multi_fault.seed("scroll", target, evidence)
        self.diagnosis(evidence, "scroll")
        p = evidence / "proposal.json"
        proposal = json.loads(p.read_text("utf-8"))
        proposal["baseSha256"] = "f" * 64
        p.write_text(json.dumps(proposal), "utf-8")
        with self.assertRaisesRegex(ValueError, "E32_UNTRUSTED_PATCH_REJECTED"):
            multi_fault.repair("scroll", target, evidence)

    def test_source_drift_never_overwritten(self):
        target, evidence, _, _ = self.case()
        multi_fault.seed("scroll", target, evidence)
        self.diagnosis(evidence, "scroll")
        target.write_bytes(target.read_bytes() + b"// attacker appended")
        with self.assertRaisesRegex(ValueError, "E32_UNAPPROVED_SOURCE_DRIFT"):
            multi_fault.repair("scroll", target, evidence)

    def test_injected_extra_proposal_field_denied(self):
        target, evidence, _, _ = self.case()
        multi_fault.seed("scroll", target, evidence)
        self.diagnosis(evidence, "scroll")
        p = evidence / "proposal.json"
        proposal = json.loads(p.read_text("utf-8"))
        proposal["shellCommand"] = "rm -rf /"
        p.write_text(json.dumps(proposal), "utf-8")
        with self.assertRaisesRegex(ValueError, "E32_UNTRUSTED_PATCH_REJECTED"):
            multi_fault.repair("scroll", target, evidence)

    def test_overlong_proposal_denied(self):
        target, evidence, _, _ = self.case()
        multi_fault.seed("scroll", target, evidence)
        self.diagnosis(evidence, "scroll")
        (evidence / "proposal.json").write_text('{"payload":"' + "a" * 5000 + '"}', "utf-8")
        with self.assertRaisesRegex(ValueError, "E32_EVIDENCE_SIZE_EXCEEDED"):
            multi_fault.repair("scroll", target, evidence)

    def test_seed_cannot_overwrite_different_source(self):
        target, evidence, _, _ = self.case()
        target.write_text("arbitrary JS", "utf-8")
        with self.assertRaisesRegex(ValueError, "E32_SEED_UNEXPECTED_BASE"):
            multi_fault.seed("scroll", target, evidence)

    @unittest.skipIf(os.name == "nt", "Windows symlink privileges not guaranteed")
    def test_symlinked_source_and_proposals_are_denied(self):
        target, evidence, clean, _ = self.case()
        actual = self.root / "actual.js"
        actual.write_bytes(clean)
        target.unlink()
        target.symlink_to(actual)
        with self.assertRaisesRegex(ValueError, "E32_SEED_UNEXPECTED_BASE"):
            multi_fault.seed("scroll", target, evidence)
        target.unlink()
        target.write_bytes(clean)
        multi_fault.seed("scroll", target, evidence)
        self.diagnosis(evidence, "scroll")
        file = evidence / "proposal.json"
        copy = evidence / "copy.json"
        copy.write_bytes(file.read_bytes())
        file.unlink()
        file.symlink_to(copy)
        with self.assertRaisesRegex(ValueError, "E32_REQUIRED_EVIDENCE_MISSING_OR_LINKED"):
            multi_fault.repair("scroll", target, evidence)

    def test_unsupported_case_or_phase_is_denied(self):
        for args in ([], ["seed"], ["repair", "unknown"], ["seed", "../escape"],
                     ["repair", "scroll", "--path", "/etc"]):
            with self.subTest(args=args):
                with self.assertRaisesRegex(ValueError, "E32_ONLY_FROZEN_PHASE_AND_CASE_ALLOWED"):
                    multi_fault.main(args)


if __name__ == "__main__":
    unittest.main()
