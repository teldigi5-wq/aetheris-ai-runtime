"""Stage 46.20 merge readiness: adversarial evidence and fail-closed tests."""
from __future__ import annotations

import unittest
from stage46_integration_merge_review import assess

HEAD = "a" * 40
MAIN = "b" * 40
TREE = "c" * 40


def case(**changes):
    args = dict(
        expected_candidate_sha=HEAD, candidate_sha=HEAD, main_sha=MAIN,
        main_sha_after=MAIN, merge_exit=0,
        merge_stdout=TREE + "\n",
        before_status="", after_status="", main_ancestor=False,
        changed_paths=["orchestrator-service/src/main/java/Test.java",
                       "docs/runtime/stage46-review.md"],
    )
    args.update(changes)
    return assess(**args)


class Stage4620MergeReadinessTest(unittest.TestCase):
    def assert_no_release(self, result):
        self.assertEqual(result["decision"], "NO_GO")
        for flag in ("actual_merged_candidate_tested",
                     "reviewed_integration_candidate_signed",
                     "physical_owner_pc_pilot_authorized",
                     "microphone_authorized", "agent_command_execution_authorized",
                     "production_authorized"):
            self.assertIs(result[flag], False, flag)

    def test_clean_tree_simulation_is_not_merge_authorization(self):
        report = case()
        self.assertTrue(report["integration_simulation_valid"])
        self.assertTrue(report["merge_conflict_free"])
        self.assertEqual(report["merge_status"], "CONFLICT_FREE_LOCAL_TREE_SIMULATION")
        self.assertEqual(report["changed_path_count"], 2)
        self.assertTrue(report["worktree_unchanged"])
        self.assert_no_release(report)

    def test_actual_conflict_is_valid_no_go_review(self):
        conflict = "CONFLICT (content): Merge conflict in README.md"
        report = case(merge_exit=1, merge_stdout=TREE + "\n\n" + conflict + "\n")
        self.assertTrue(report["integration_simulation_valid"])
        self.assertFalse(report["merge_conflict_free"])
        self.assertEqual(report["merge_status"], "CONFLICTS_OBSERVED_LOCAL_TREE_SIMULATION")
        self.assertEqual(report["conflict_message_count"], 1)
        self.assertIn(conflict, report["conflict_messages"])
        self.assert_no_release(report)

    def test_different_candidate_sha_fails_verification(self):
        r = case(candidate_sha="d" * 40)
        self.assertFalse(r["integration_simulation_valid"])
        self.assert_no_release(r)

    def test_invalid_head_sha_never_passes(self):
        for bad in ("bad", "A" * 40, "", None):
            self.assertFalse(case(expected_candidate_sha=bad)["integration_simulation_valid"])

    def test_main_branch_ref_movement_is_rejected(self):
        self.assertFalse(case(main_sha_after="d" * 40)["integration_simulation_valid"])

    def test_dirty_worktree_before_or_after_rejected(self):
        self.assertFalse(case(before_status=" M README.md\n")["integration_simulation_valid"])
        self.assertFalse(case(after_status=" M README.md\n")["integration_simulation_valid"])

    def test_merge_tree_execution_error_is_unverified(self):
        for rc in (2, 128, -9, None, True):
            self.assertFalse(case(merge_exit=rc)["integration_simulation_valid"])

    def test_no_tree_digest_is_invalid(self):
        self.assertFalse(case(merge_stdout="fatal: cannot resolve HEAD\n")["integration_simulation_valid"])

    def test_clean_exit_must_not_claim_conflict(self):
        output = TREE + "\nCONFLICT (content): Merge conflict in README.md\n"
        self.assertFalse(case(merge_exit=0, merge_stdout=output)["integration_simulation_valid"])

    def test_invalid_conflict_line_format_rejected(self):
        s = TREE + "\nCONFLICT " + ("x" * 1500) + "\n"
        self.assertFalse(case(merge_exit=1, merge_stdout=s)["integration_simulation_valid"])

    def test_binary_conflicts_are_not_claimed_clean(self):
        conflict = "CONFLICT (binary): Merge conflict in image.bin"
        report = case(merge_exit=1, merge_stdout=TREE + "\n" + conflict + "\n")
        self.assertTrue(report["integration_simulation_valid"])
        self.assertFalse(report["merge_conflict_free"])
        self.assert_no_release(report)

    def test_more_than_500_conflicts_are_rejected(self):
        out = TREE + "\n" + "\n".join(
            f"CONFLICT (content): Merge conflict in file{i}.txt" for i in range(501)
        )
        self.assertFalse(case(merge_exit=1, merge_stdout=out)["integration_simulation_valid"])

    def test_large_stdout_is_rejected(self):
        self.assertFalse(case(merge_stdout=TREE + "\n" + "x" * 351000)["integration_simulation_valid"])

    def test_changed_paths_with_control_chars_rejected(self):
        for path in ("\n", "foo\nbar", "foo\rbar", "a\x00b", ""):
            self.assertFalse(case(changed_paths=[path])["integration_simulation_valid"])

    def test_malformed_changed_paths_rejected(self):
        for paths in ("a/file", None, [5], ["x"] * 12001):
            self.assertFalse(case(changed_paths=paths)["integration_simulation_valid"])

    def test_fast_forward_ancestry_is_metadata_not_release(self):
        report = case(main_ancestor=True)
        self.assertTrue(report["main_is_ancestor_of_candidate"])
        self.assert_no_release(report)

    def test_boolean_ancestry_input_not_silently_coerced(self):
        self.assertFalse(case(main_ancestor=1)["integration_simulation_valid"])

    def test_no_real_merge_or_git_push_is_recorded(self):
        r = case()
        self.assertIs(r["actual_merged_candidate_tested"], False)
        self.assertIs(r["reviewed_integration_candidate_signed"], False)


if __name__ == "__main__":
    unittest.main()
