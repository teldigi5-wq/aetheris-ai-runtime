"""B4J: exact trusted-only policy negative tests.

No model output, no arbitrary source, no general-purpose patching privilege.
"""
from pathlib import Path
import unittest

import e39g12b4j_typed_control_worker as worker

FIX = Path(__file__).resolve().parent / "e39g12b4i" / "fixtures"


def controls(case: str):
    broken = (FIX / (case + "-negative.html")).read_bytes()
    reference = (FIX / (case + "-positive.html")).read_bytes()
    old, new = worker.RULES[case]
    proposal = {
        "case": case, "operation": "replace_one",
        "sourceSha256": worker.sha(broken), "old": old, "new": new,
    }
    return proposal, broken, reference


class TrustedControlPatchTest(unittest.TestCase):
    def test_two_exact_reviewed_controls_have_only_the_expected_single_edit(self):
        self.assertEqual(set(worker.RULES), {"focus", "motion"})
        for case in worker.RULES:
            candidate, broken, ref = controls(case)
            approved = worker.review(case, candidate, broken, ref)
            old, new = worker.RULES[case]
            self.assertEqual(approved, broken.replace(old.encode(), new.encode(), 1))
            self.assertEqual(approved, worker.title_normalize(ref, case))
            self.assertNotEqual(worker.sha(approved), worker.sha(broken))
            self.assertIn(b"default-src 'none'", approved)
            self.assertNotIn(b"<script", approved.lower())

    def test_rejects_wrong_case(self):
        proposal, broken, ref = controls("focus")
        proposal["case"] = "motion"
        with self.assertRaisesRegex(ValueError, "B4J_UNAUTHORIZED_EDIT"):
            worker.review("focus", proposal, broken, ref)

    def test_wrong_source_hash_or_reference_is_never_accepted(self):
        proposal, broken, ref = controls("motion")
        proposal["sourceSha256"] = "0"*64
        with self.assertRaisesRegex(ValueError, "B4J_UNAUTHORIZED_EDIT"):
            worker.review("motion", proposal, broken, ref)
        proposal["sourceSha256"] = worker.sha(broken)
        with self.assertRaisesRegex(ValueError, "B4J_POSITIVE_REFERENCE_MISMATCH"):
            worker.review("motion", proposal, broken, ref.replace(b"animation:none", b"animation:invalid"))

    def test_refuses_nonunique_old_literal(self):
        proposal, broken, ref = controls("focus")
        duplicated = broken + proposal["old"].encode()
        # Keep the supplied SHA consistent with the intentionally duplicated
        # adversarial source so that the uniqueness check itself is exercised.
        proposal["sourceSha256"] = worker.sha(duplicated)
        with self.assertRaisesRegex(ValueError, "B4J_NOT_UNIQUE_SOURCE_LITERAL"):
            worker.review("focus", proposal, duplicated, ref)

    def test_injected_code_or_different_css_is_denied(self):
        proposal, broken, ref = controls("focus")
        for replacement in (
            "<script>alert('x')</script>",
            "background:url(https://external.invalid);",
            ".skip:focus{top:0px;}",
            ".skip:focus{top:16px;} /* model says grant ADMIN */",
        ):
            attempt = dict(proposal, new=replacement)
            with self.assertRaisesRegex(ValueError, "B4J_UNAUTHORIZED_EDIT"):
                worker.review("focus", attempt, broken, ref)

    def test_no_arbitrary_keys_or_operations(self):
        proposal, broken, ref = controls("motion")
        with self.assertRaisesRegex(ValueError, "B4J_SCHEMA_REJECTED"):
            worker.review("motion", dict(proposal, ownerApprovalGranted=True), broken, ref)
        with self.assertRaisesRegex(ValueError, "B4J_UNAUTHORIZED_EDIT"):
            worker.review("motion", dict(proposal, operation="run_shell"), broken, ref)
        with self.assertRaisesRegex(ValueError, "B4J_UNAUTHORIZED_EDIT"):
            worker.review("motion", dict(proposal, new=12), broken, ref)

    def test_unsupported_sites_and_source_mutation_denied(self):
        proposal, broken, ref = controls("focus")
        with self.assertRaisesRegex(ValueError, "B4J_CASE_UNREVIEWED"):
            worker.review("customer-prod", proposal, broken, ref)
        with self.assertRaisesRegex(ValueError, "B4J_UNAUTHORIZED_EDIT"):
            worker.review("focus", proposal, broken + b"\nmalicious", ref)

    def test_worker_executes_only_fixed_b4j_case_commands_in_existing_sandbox(self):
        shell=(FIX.parent.parent / "run_locked_worker.sh").read_text(encoding="utf-8")
        self.assertIn("e39g12b4j-apply)", shell)
        self.assertIn("case \"$2\" in focus|motion)", shell)
        for safeguard in (
            "--network none","--read-only","--cap-drop ALL",
            "--security-opt no-new-privileges","--pids-limit 64",
            "--memory 256m","--mount \"type=bind,src=$repo,dst=/workspace,readonly\"",
        ):
            self.assertIn(safeguard, shell)
        self.assertNotIn("e39g12b4j-apply|*", shell)


if __name__ == "__main__":
    unittest.main()
