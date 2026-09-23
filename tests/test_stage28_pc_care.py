import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "scripts" / "stage28" / "pc_care_diagnostics.py"
SPEC = importlib.util.spec_from_file_location("stage28_pc_care", MODULE_PATH)
pc = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(pc)


class Stage28PcCareTests(unittest.TestCase):
    def setUp(self):
        self.policy_path = ROOT / "configs" / "stage28" / "pc-care-policy.json"
        self.policy = pc.load_json(self.policy_path)
        pc.validate_policy(self.policy)

    def fixture(self, name):
        return pc.load_json(ROOT / "tests" / "fixtures" / "stage28" / name)

    def test_policy_is_pre_pc_and_non_mutating(self):
        self.assertEqual(self.policy["physical_pc_status"], "BLOCKED_PENDING_HARDWARE")
        self.assertEqual(self.policy["mode"], "PRE_PC_SYNTHETIC_ONLY")
        self.assertTrue(self.policy["remediation"]["recommendations_only"])
        self.assertTrue(self.policy["remediation"]["owner_approval_required_for_mutation"])
        for key, value in self.policy["remediation"].items():
            if key.endswith("_allowed"):
                self.assertFalse(value, key)

    def test_healthy_fixture_is_healthy(self):
        report = pc.diagnose(self.fixture("healthy.json"), self.policy)
        self.assertEqual(report["diagnosticSeverity"], "HEALTHY")
        self.assertEqual(report["recommendations"], [])
        self.assertFalse(report["hostMutationAttempted"])

    def test_memory_pressure_is_warning_and_recommendation_only(self):
        report = pc.diagnose(self.fixture("memory-pressure.json"), self.policy)
        self.assertEqual(report["diagnosticSeverity"], "WARNING")
        self.assertTrue(report["recommendations"])
        self.assertTrue(all(item["action"] == "RECOMMEND_ONLY" for item in report["recommendations"]))
        self.assertTrue(all(item["owner_approval_required_before_mutation"] for item in report["recommendations"]))

    def test_critical_fixture_does_not_execute_repairs(self):
        report = pc.diagnose(self.fixture("critical-pressure.json"), self.policy)
        self.assertEqual(report["diagnosticSeverity"], "CRITICAL")
        self.assertFalse(report["hostMutationAttempted"])
        self.assertFalse(report["networkRequired"])
        self.assertFalse(report["shellExecutionRequired"])
        self.assertEqual(report["physicalPcStatus"], "BLOCKED_PENDING_HARDWARE")

    def test_non_synthetic_ci_snapshot_is_rejected(self):
        snapshot = self.fixture("healthy.json")
        snapshot["source"] = "owner_physical_pc"
        with self.assertRaises(pc.PcCareError):
            pc.diagnose(snapshot, self.policy)

    def test_policy_fails_closed_if_autonomous_mutation_is_enabled(self):
        broken = json.loads(json.dumps(self.policy))
        broken["remediation"]["autonomous_mutation_allowed"] = True
        with self.assertRaises(pc.PcCareError):
            pc.validate_policy(broken)

    def test_report_bytes_are_deterministic(self):
        report = pc.diagnose(self.fixture("critical-pressure.json"), self.policy)
        first = pc.canonical_bytes(report)
        second = pc.canonical_bytes(pc.diagnose(self.fixture("critical-pressure.json"), self.policy))
        self.assertEqual(first, second)
        self.assertEqual(pc.sha256(first), pc.sha256(second))


if __name__ == "__main__":
    unittest.main()
