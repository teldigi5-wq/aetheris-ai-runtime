"""E3.9G-8 security and no-unearned-browser-credit tests (network-free)."""
from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

import e39g8_policy_worker as worker
import e39g8_independent_score as scorer


class G8PolicyTests(unittest.TestCase):
    GOOD = "granite4-3b--opacity-zero--baseline"

    def test_allowlist_only_both_arms_and_fixed_models(self):
        for model in worker.MODEL:
            for case in ("top-120","opacity-zero"):
                for arm in ("baseline","skill-context"):
                    got=worker.unpack("--".join((model,case,arm)))
                    self.assertEqual(got[1:3],(case,arm))
        for bad in (
            "../granite4-3b--top-120--baseline",
            "granite4-3b--top-40--baseline",
            "granite4-3b--top-120--owner",
            "openrouter--top-120--baseline",
            "granite4-3b--opacity-zero--baseline/../../etc",
        ):
            with self.subTest(bad=bad):
                with self.assertRaisesRegex(ValueError,"E39G8_FIXED_MODEL_CASE_ARM"):
                    worker.unpack(bad)

    def test_worker_has_no_model_or_tool_imports(self):
        code=Path(worker.__file__).read_text()
        self.assertNotIn("import subprocess",code)
        self.assertNotIn("import e39g7_skill_ab_adviser",code)
        self.assertNotIn("http.client",code)
        self.assertNotIn("requests",code)
        self.assertIn("frozen.validate_candidate",code)

    def test_cannot_rerun_or_apply_when_output_exists(self):
        with tempfile.TemporaryDirectory() as temp:
            folder=Path(temp)/"test"
            folder.mkdir()
            with mock.patch.object(worker,"evidence",
                return_value=("granite4-3b","opacity-zero","baseline",folder,
                              b"broken",b"fixed",
                              {"rawResponseSha256":"a"*64,
                               "browserBeforeSha256":{}},True,
                              "POLICY_ELIGIBLE_NOT_BROWSER_TESTED")):
                with self.assertRaisesRegex(ValueError,"E39G8_ARM_OUTPUT_ALREADY_EXISTS"):
                    worker.apply(self.GOOD)

    def test_score_requires_provenance_never_forged_browser_positive(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            folder=root/"granite4-3b"/"opacity-zero"/"baseline"
            folder.mkdir(parents=True)
            broken=b"synthetic-defective-styles"
            # A claimed successful screenshot cannot replace a genuine
            # earlier worker receipt with the exact evidence class.
            (folder/"patch-receipt.json").write_text(json.dumps({
                "evidenceClass":"E39G8_FAKE_OWNER_APPROVAL",
                "caseId":"opacity-zero","arm":"baseline",
            }))
            records=root/"e39d"/"opacity-zero"
            records.mkdir(parents=True)
            (records/"e39g7-granite4-3b-baseline.json").write_text(json.dumps({
                "realModelCalls":1,"rawResponseSha256":"d"*64}))
            (records/"e39g7-granite4-3b-score.json").write_text(json.dumps({
                "arms":{"baseline":{"outcome":"POLICY_REJECTED"}},
                "modelTag":"granite4:3b"}))
            with mock.patch.object(worker,"ROOT",root), mock.patch.object(
                 scorer.fixture,"location",return_value=records), mock.patch.object(
                 scorer.fixture,"defective_source",return_value=broken), mock.patch.object(
                 scorer.fixture,"checked",return_value=broken):
                with self.assertRaisesRegex(ValueError,"E39G8_FORGED_MODEL_POLICY_APPLICATION"):
                    scorer.score(self.GOOD)
            self.assertFalse((folder/"model-browser-score.json").exists())

    def test_score_does_not_credit_rejected_untested_patch(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            folder=root/"granite4-3b"/"opacity-zero"/"baseline"
            folder.mkdir(parents=True)
            broken=b"defect"
            raw='{"operation":"abstain"}'
            sha=scorer.sha
            record={"evidenceClass":"E39G8_NETWORK_NONE_FROZEN_POLICY_MODEL_PATCH",
                "caseId":"opacity-zero","arm":"baseline","modelTag":"granite4:3b",
                "actualModelCallsFromVerifiedG7Receipt":1,
                "modelRawSha256":sha(raw.encode()),
                "beforeBrowserSha256":{"mobile":"a","desktop":"b"},
                "sourceBeforeSha256":sha(broken),"sourceAfterSha256":sha(broken),
                "frozenPolicyEligible":False,"sandboxPatchApplied":False,
                "ownerApprovalGranted":False,"productionPublished":False,
                "modelBrowserRepairVerified":False}
            (folder/"patch-receipt.json").write_text(json.dumps(record))
            other=root/"e39d"/"opacity-zero"
            other.mkdir(parents=True)
            (other/"e39g7-granite4-3b-baseline.json").write_text(json.dumps({
                "realModelCalls":1,"rawResponseSha256":sha(raw.encode()),
                "websiteEdited":False,"rawModelResponse":raw,
                "browserBeforeSha256":{"mobile":"a","desktop":"b"}}))
            (other/"e39g7-granite4-3b-score.json").write_text(json.dumps({
                "modelTag":"granite4:3b","arms":{"baseline":{"outcome":"ABSTAIN"}}}))
            with mock.patch.object(worker,"ROOT",root), mock.patch.object(
                 scorer.fixture,"location",return_value=other), mock.patch.object(
                 scorer.fixture,"defective_source",return_value=broken), mock.patch.object(
                 scorer.fixture,"checked",return_value=broken):
                row=scorer.score(self.GOOD)
            self.assertFalse(row["actualModelProposedSandboxPatchApplied"])
            self.assertFalse(row["actualModelAfterChromiumExecuted"])
            self.assertFalse(row["chromiumVerifiedModelRepair"])
            self.assertEqual(row["realAfterChromiumViewports"],0)

    def test_browser_evaluator_independent_of_model_oracle(self):
        code=Path(__file__).with_name("e39g8_independent_browser.mjs").read_text()
        self.assertIn("chromium.launch",code)
        self.assertIn("page.keyboard.press('Tab')",code)
        self.assertIn("reducedMotion:'reduce'",code)
        self.assertIn("screenshotSha256",code)
        self.assertNotIn("e39e_model_policy",code)
        self.assertNotIn("fixture.clean_source",code)
        self.assertNotIn("expectedPatch",code)


if __name__ == "__main__":
    unittest.main()
