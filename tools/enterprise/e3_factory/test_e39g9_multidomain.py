"""E3.9G-9 adversarial tests of independent multi-domain model / patch boundaries."""
from __future__ import annotations

import json
import os
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest import mock

import e39g9_multidomain_worker as gate
import e39g9_multidomain_adviser as adviser


class MultiDomainPolicyTests(unittest.TestCase):
    def test_original_three_e33_fixture_families_only(self):
        self.assertEqual(set(gate.CASES),{"mobile-940","reduced-boolean","scroll-24"})
        self.assertEqual(len(gate.MODEL),2)
        self.assertEqual(set(gate.ARMS),{"baseline","skill-context"})
        for name,(_,filename,good,bad) in gate.CASES.items():
            source=(gate.worker.HERE/filename).read_bytes().decode()
            with self.subTest(name=name):
                self.assertEqual(source.count(good),1)
                self.assertNotIn(bad,source)

    def test_model_identity_case_and_arm_are_exact(self):
        for model in gate.MODEL:
            for case in gate.CASES:
                for arm in gate.ARMS:
                    got=gate.trial("--".join((model,case,arm)))
                    self.assertEqual(got[1:3],(case,arm))
        for malformed in ("hermes3-3b--mobile--baseline",
             "granite4-3b--mobile-940--publish",
             "claude--reduced-boolean--skill-context",
             "../../hermes3-3b--scroll-24--baseline"):
            with self.subTest(value=malformed):
                with self.assertRaisesRegex(ValueError,"E39G9_MODEL_CASE_ARM_NOT_ALLOWLISTED"):
                    gate.trial(malformed)

    def test_policy_real_single_literal_canonical_repair_on_three_families(self):
        repairs={
            "mobile-940":("html{min-width:940px}",""),
            "reduced-boolean":("const reduced = Boolean(false);",
                               "const reduced = motion.matches;"),
            "scroll-24":("const p = 0.24;","const p = progress();"),
        }
        for case,(old,new) in repairs.items():
            _,name,clean,broken=gate.CASES[case]
            origin=(gate.worker.HERE/name).read_bytes()
            corrupt=origin.decode().replace(clean,broken,1).encode()
            with self.subTest(case=case):
                output=gate.permitted(case,{"operation":"replace","old":old,"new":new},
                        corrupt,origin)
                self.assertEqual(output,origin)

    def test_reject_tool_payload_broad_diffs_and_wrong_properties(self):
        original=gate.worker.HERE/"main.js"
        good=original.read_bytes()
        _,_,clean,broken=gate.CASES["scroll-24"]
        before=good.decode().replace(clean,broken,1).encode()
        attempts=[
           ("const p = 0.24;","eval(location.search);"),
           ("const p = 0.24;","const p = progress();\nconsole.log('secret')"),
           ("const p = 0.24;","const reduced = motion.matches;"),
           ("function render()", "function render()"),
           ("const p = 0.24;",""),
        ]
        for old,new in attempts:
            with self.subTest(old=old,new=new):
                with self.assertRaisesRegex(ValueError,"E39G9_"):
                    gate.permitted("scroll-24",{"operation":"replace","old":old,"new":new},before,good)

    def test_duplicate_json_fields_are_rejected(self):
        with self.assertRaisesRegex(ValueError,"E39G9_DUPLICATE_UNTRUSTED_JSON_KEY"):
            gate.strict_reply('{"operation":"abstain","operation":"replace"}')
        with self.assertRaisesRegex(ValueError,"E39G9_UNSAFE_REPLY_SCHEMA"):
            gate.strict_reply('{"operation":"replace","old":"x","new":"y","exec":"sh"}')

    def test_exact_vendor_skill_bytes_with_untrusted_warnings(self):
        t,s=adviser.skill_text("reduced-boolean")
        self.assertIn("animation-performance",t)
        self.assertIn("aetheris-web-performance",t)
        self.assertIn("emil-design-eng",t)
        self.assertEqual(len(s),4)
        baseline=adviser.prompt("reduced-boolean","const reduced = Boolean(false);",
                     "Reduced motion ignored",{"mobile":{"motion":"enabled"}},
                     "baseline",t)
        treatment=adviser.prompt("reduced-boolean","const reduced = Boolean(false);",
                     "Reduced motion ignored",{"mobile":{"motion":"enabled"}},
                     "skill-context",t)
        self.assertTrue(treatment.startswith(baseline[:-len("Return one small safe JSON proposal or abstain. No Markdown.")]))
        self.assertNotIn("animation-performance",baseline)
        self.assertIn("UNTRUSTED QUOTED DATA",treatment)

    def test_ci_opt_in_before_any_real_transport(self):
        with mock.patch.dict(os.environ,{},clear=True):
            with self.assertRaisesRegex(ValueError,"E39G9_REAL_OLLAMA_OPT_IN_MISSING"):
                adviser.run("hermes3:3b","mobile-940")
        with self.assertRaisesRegex(ValueError,"E39G9_FIXED_MODEL"):
            adviser.run("unreviewed:remote","mobile-940")

    def test_adviser_does_not_import_policy_or_independent_browser_scorer(self):
        src=Path(adviser.__file__).read_text()
        self.assertNotIn("import e39g9_multidomain_worker",src)
        self.assertNotIn("import e39g9_multidomain_score",src)
        self.assertNotIn("import e39g9_multidomain_browser",src)
        for dangerous in ("import subprocess","os.system(","eval(","exec(","openrouter.ai"):
            self.assertNotIn(dangerous,src)

    def test_duplicate_evidence_prevents_model_request(self):
        with TemporaryDirectory() as td:
            root=Path(td)
            casefolder=root/"cases"/"mobile-940"
            casefolder.mkdir(parents=True)
            (casefolder/"e39g9-granite4-3b-baseline.json").write_text("{}")
            with mock.patch.object(adviser,"input_source",return_value=(
                     casefolder,b"source","snippet","problem",{"mobile":{}},{"mobile":"abc"})):
                with mock.patch.object(adviser,"skill_text",return_value=("skill",[])), \
                    mock.patch.dict(os.environ,{adviser.OPT_IN:"yes"}), \
                    mock.patch.object(adviser.transport,"local_json") as connection:
                    with self.assertRaisesRegex(ValueError,"E39G9_REPEAT_RUN_NO_NETWORK_ALLOWED"):
                        adviser.run("granite4:3b","mobile-940")
                    connection.assert_not_called()


if __name__=="__main__":
    unittest.main()
