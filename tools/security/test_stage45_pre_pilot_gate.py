"""Pure standard-library tests of the non-authorizing pre-pilot gate."""
import copy
import tempfile
from pathlib import Path
import unittest

from stage45_pre_pilot_gate import (
    ROOT,
    LAB_EVIDENCE,
    RELEASE_BLOCKERS,
    assess,
    validate_report,
)


class ReleaseReadinessPolicyTest(unittest.TestCase):
    def test_checked_in_synthetic_sources_are_present_but_no_go(self):
        result = assess(ROOT)
        validate_report(result)
        self.assertTrue(result["synthetic_source_inventory_complete"], result["missing_review_sources"])
        self.assertEqual(result["decision"], "NO_GO")
        self.assertFalse(result["production_pilot_authorized"])
        self.assertEqual(len(result["release_blockers"]), len(RELEASE_BLOCKERS))
        self.assertGreaterEqual(len(result["release_blockers"]), 10)

    def test_empty_or_incomplete_tree_never_changes_no_go_to_go(self):
        with tempfile.TemporaryDirectory() as directory:
            report = assess(Path(directory))
        validate_report(report)
        self.assertEqual(report["decision"], "NO_GO")
        self.assertFalse(report["synthetic_source_inventory_complete"])
        self.assertEqual(len(report["missing_review_sources"]), len(LAB_EVIDENCE))
        self.assertFalse(report["agent_command_execution_authorized"])

    def test_forged_release_authorization_flag_is_rejected(self):
        good = assess(ROOT)
        for key in (
            "production_pilot_authorized",
            "agent_command_execution_authorized",
            "desktop_actions_authorized",
            "windows_device_certified",
            "voice_microphone_certified",
            "protected_branch_merge_permitted",
            "deploy_permitted",
        ):
            bad = copy.deepcopy(good)
            bad[key] = True
            with self.subTest(key=key), self.assertRaisesRegex(
                    ValueError, "PREPILOT_AUTHORIZATION_INVARIANT_BROKEN"):
                validate_report(bad)

    def test_report_cannot_claim_go(self):
        bad = assess(ROOT)
        bad["decision"] = "GO"
        with self.assertRaisesRegex(ValueError, "PREPILOT_MUST_REMAIN_BLOCKED"):
            validate_report(bad)

    def test_missing_immutable_blocker_is_rejected(self):
        bad = assess(ROOT)
        bad["release_blockers"].pop()
        with self.assertRaisesRegex(ValueError, "PREPILOT_BLOCKER_SET_INCOMPLETE"):
            validate_report(bad)

    def test_added_forged_evidence_does_not_authorize(self):
        good = assess(ROOT)
        good["review_only_evidence"].append({
            "id": "FAKE_WINDOWS_AUDIO_SIGNOFF",
            "scope": "UNVERIFIED",
            "source_markers_present": True,
        })
        with self.assertRaisesRegex(ValueError, "PREPILOT_SOURCE_INVENTORY_INCOMPLETE"):
            validate_report(good)

    def test_source_marker_removed_from_copy_fails_inventory_without_launch(self):
        with tempfile.TemporaryDirectory() as directory:
            fake_root = Path(directory)
            for _, path, anchors in LAB_EVIDENCE:
                artifact = fake_root / path
                artifact.parent.mkdir(parents=True, exist_ok=True)
                artifact.write_text("\n".join(anchors), encoding="utf-8")
            initially = assess(fake_root)
            self.assertTrue(initially["synthetic_source_inventory_complete"])
            assert_path = fake_root / LAB_EVIDENCE[0][1]
            assert_path.write_text("NOT_EXECUTION", encoding="utf-8")
            missing = assess(fake_root)
            self.assertFalse(missing["synthetic_source_inventory_complete"])
            self.assertEqual(missing["decision"], "NO_GO")
            self.assertFalse(missing["deploy_permitted"])


if __name__ == "__main__":
    unittest.main()
