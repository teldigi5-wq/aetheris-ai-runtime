"""E3.9G-10 negative tests: no gold leakage, no forbidden edits or scores."""
from __future__ import annotations
import json,os
from pathlib import Path
import unittest
from unittest import mock
import e39g9_multidomain_worker as frozen
import e39g9_multidomain_adviser as previous
import e39g10_targeted_adviser as target
import e39g10_targeted_gate as gate

class SourceTargetedTests(unittest.TestCase):
    def test_three_distinct_functional_sources_targeted_without_gold(self):
        samples={
            "mobile-940":b"x html{min-width:940px} y",
            "reduced-boolean":b"x const reduced = Boolean(false); y",
            "scroll-24":b"x const p = 0.24; y",
        }
        expected={"mobile-940":"html{min-width:940px}",
                  "reduced-boolean":"const reduced = Boolean(false);",
                  "scroll-24":"const p = 0.24;"}
        for case,source in samples.items():
            with self.subTest(case=case):
                actual=target.editable_source_line(case,source)
                self.assertEqual(actual,expected[case])
                prompt=target.targeted_prompt(case,source,"not an oracle",
                    "Observed bad browser result",{"mobile":{"scrollWidth":940}},
                    "LOW TRUST SKILL DATA")
                self.assertIn("ONLY EDITABLE OLD SUBSTRING",prompt)
                self.assertIn(previous.CASES[case][0],prompt)
                self.assertIn(actual,prompt)
                self.assertNotIn("E39G9_E33_REVIEWED_REPAIR_GRAMMAR",prompt)
                self.assertNotIn("clean_source()",prompt)
    def test_wrong_model_scope_token_denied(self):
        for token in ("../hermes3-3b--scroll-24--targeted",
                "granite4-3b--mobile-940--admin",
                "granite4-3b--scroll-24--skill-context",
                "remote--reduced-boolean--original"):
            with self.assertRaisesRegex(ValueError,"E39G10_EXACT_MODEL"):
                gate.trial(token)
    def test_genuine_model_calls_require_optin_before_metadata(self):
        with mock.patch.dict(os.environ,{},clear=True):
            with self.assertRaisesRegex(ValueError,"E39G10_ACTUAL_MODEL_OPT_IN_REQUIRED"):
                target.run("granite4:3b","scroll-24")
        with self.assertRaisesRegex(ValueError,"E39G10_MODEL_CASE_OUT_OF_SCOPE"):
            target.run("remote-model","scroll-24")
    def test_unchanged_repair_scope_refuses_wrong_target_and_injected_js(self):
        name="scroll-24"
        _,filename,good,bad=frozen.CASES[name]
        canonical=(frozen.worker.HERE/filename).read_bytes()
        broken=canonical.decode().replace(good,bad,1).encode()
        for old,new in [
            ("const p = 0.24;","const p = progress(); eval('x')"),
            ("const p = 0.24;","const reduced = motion.matches;"),
            ("const reduced = motion.matches;","const p = progress();")]:
            with self.assertRaisesRegex(ValueError,"E39G9_"):
                frozen.permitted(name,{"operation":"replace","old":old,
                       "new":new},broken,canonical)
    def test_approval_and_stagerelase_not_in_model_or_web_outputs(self):
        adviser=Path(target.__file__).read_text()
        gate_code=Path(gate.__file__).read_text()
        scorer=Path(__file__).with_name("e39g10_targeted_score.py").read_text()
        browser=Path(__file__).with_name("e39g10_targeted_browser.mjs").read_text()
        self.assertNotIn("import e39g9_multidomain_worker",adviser)
        self.assertNotIn("import e39g10_targeted_gate",adviser)
        self.assertNotIn("subprocess",adviser)
        self.assertNotIn("os.system",adviser)
        self.assertIn("frozen.permitted",gate_code)
        self.assertIn("browserVerifiedModelRepair",scorer)
        self.assertIn("chromium.launch",browser)
        self.assertIn("screenshotSha256",browser)
        self.assertNotIn("goldenFix",browser)
    def test_duplicate_model_key_rejected_by_frozen_gate(self):
        with self.assertRaisesRegex(ValueError,"E39G9_DUPLICATE_UNTRUSTED_JSON_KEY"):
            frozen.strict_reply('{"operation":"replace","old":"x","old":"y","new":"z"}')

if __name__=="__main__":unittest.main()
