"""Adversarial unit tests for E3.1's byte-exact, diagnosis-gated repair."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

import fault_repair


class RepairTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.site = self.root / "main.js"
        self.site.write_bytes(fault_repair.SOURCE.read_bytes())
        self.evidence = self.root / "repair-evidence"

    def put_valid_diagnosis(self):
        (self.evidence / "diagnosis.json").write_text(json.dumps({
            "evidenceClass": "REAL_CHROMIUM_QA_CI_ONLY_NOT_PRODUCTION",
            "passed": False,
            "failure": "AssertionError: Late progress not near end: 0",
        }), encoding="utf-8")

    def test_detect_seed_and_exact_repair(self):
        before = self.site.read_bytes()
        seeded = fault_repair.seed(self.site, self.evidence)
        self.assertNotEqual(self.site.read_bytes(), before)
        self.assertEqual(seeded["originalSha256"], hashlib.sha256(before).hexdigest())
        self.put_valid_diagnosis()
        report = fault_repair.repair(self.site, self.evidence)
        self.assertEqual(self.site.read_bytes(), before)
        self.assertTrue(report["sourceRestoredExactly"])
        self.assertFalse(report["approvalGranted"])
        self.assertFalse(report["productionDeployed"])

    def test_repair_without_real_browser_diagnosis_denied(self):
        fault_repair.seed(self.site, self.evidence)
        with self.assertRaisesRegex(ValueError, "E31_BROWSER_DIAGNOSIS_REQUIRED"):
            fault_repair.repair(self.site, self.evidence)

    def test_arbitrary_malicious_change_is_not_repaired(self):
        fault_repair.seed(self.site, self.evidence)
        self.put_valid_diagnosis()
        self.site.write_text(self.site.read_text("utf-8") + "\n// unexpected change\n",
                             encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "E31_UNAPPROVED_PATCH_OR_DRIFT"):
            fault_repair.repair(self.site, self.evidence)

    def test_unrelated_browser_error_does_not_enable_repair(self):
        fault_repair.seed(self.site, self.evidence)
        self.put_valid_diagnosis()
        data = json.loads((self.evidence / "diagnosis.json").read_text("utf-8"))
        data["failure"] = "browser harness timed out before checking scroll"
        (self.evidence / "diagnosis.json").write_text(json.dumps(data), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "E31_WRONG_BROWSER_FAILURE"):
            fault_repair.repair(self.site, self.evidence)

    def test_seeding_twice_is_rejected_without_overwrite(self):
        fault_repair.seed(self.site, self.evidence)
        with self.assertRaisesRegex(ValueError, "E31_SOURCE_PRECONDITION_FAILED"):
            fault_repair.seed(self.site, self.evidence)

    def test_repair_refuses_script_symlink(self):
        target = self.root / "original.js"
        target.write_bytes(fault_repair.SOURCE.read_bytes())
        self.site.unlink()
        self.site.symlink_to(target)
        with self.assertRaisesRegex(ValueError, "E31_SCRIPT_MISSING_OR_LINKED"):
            fault_repair.seed(self.site, self.evidence)

    def test_no_arbitrary_cli_actions(self):
        for args in ([], ["--path", "/etc/passwd"], ["publish"], ["seed", "extra"]):
            with self.subTest(args=args):
                with self.assertRaisesRegex(ValueError, "E31_ONLY_SEED_OR_REPAIR_ALLOWED"):
                    fault_repair.main(args)


if __name__ == "__main__":
    unittest.main()
