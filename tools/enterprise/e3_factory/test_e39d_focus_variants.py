"""E3.9C-2B1: exact-source/variant registry and adversarial evaluator unit tests.

Model calls: zero. These tests do not substitute for real Chromium proof.
"""
from __future__ import annotations
import json
import inspect
import unittest
from unittest import mock

import e39d_heldout_focus_evaluator as subject


class EightFocusVariants(unittest.TestCase):
    def test_eight_distinct_bytewise_seeded_css_faults(self):
        self.assertEqual(len(subject.FAULTS), 8)
        self.assertEqual(len(set(rule for rule, _ in subject.FAULTS.values())), 8)
        canonical = subject.clean_source()
        self.assertEqual(canonical.decode().count(subject.ORIGINAL), 1)
        checksums = set()
        for name in subject.FAULTS:
            with self.subTest(name=name):
                modified = subject.defective_source(name)
                self.assertNotEqual(modified, canonical)
                self.assertEqual(modified.count(b".skip-link:focus{"), 1)
                # Fixed hidden-at-rest rule remains exactly unchanged.
                self.assertIn(b".skip-link{position:fixed;z-index:100;top:-80px;left:20px;",modified)
                self.assertEqual(modified.replace(
                    subject.FAULTS[name][0].encode(), subject.ORIGINAL.encode(),1),
                    canonical)
                checksums.add(subject.sha(modified))
        self.assertEqual(len(checksums),8)

    def test_five_distinct_mechanisms_and_expected_chromium_failure_classes(self):
        self.assertEqual(subject.FAULTS["top-40"][1],"E39D_FOCUS_OFFSCREEN")
        self.assertEqual(subject.FAULTS["left-120"][1],"E39D_FOCUS_OFFSCREEN")
        self.assertEqual(subject.FAULTS["opacity-zero"][1],"E39D_FOCUS_TRANSPARENT")
        self.assertEqual(subject.FAULTS["visibility-hidden"][1],"E39D_FOCUS_NOT_REACHABLE")
        self.assertEqual(subject.FAULTS["transform-up"][1],"E39D_FOCUS_OFFSCREEN")
        self.assertEqual(len({subject.FAULTS[x][1] for x in subject.FAULTS}),3)

    def test_reject_paths_unknown_cases_and_arbitrary_commands(self):
        for name in ("../secrets","../../main","top-40/../../secrets","",
                     "none","TOp-40","top-999","top-40;sh"):
            with self.subTest(name=name):
                with self.assertRaisesRegex(ValueError,"E39D_UNKNOWN_CASE"):
                    subject.location(name)
        for arguments in (
            [],["restore"],["build","top-40"],["restore","../../secrets"],
            ["restore","top-40","--shell"],["write","top-40"],
            ["restore","top-40; rm -rf ."],
        ):
            with self.subTest(arguments=arguments), self.assertRaises(ValueError):
                subject.main(arguments)

    def test_actual_two_browser_failures_must_match_each_mechanism(self):
        case="opacity-zero"
        current=subject.defective_source(case)
        seed={"beforeSourceSha256":subject.sha(current)}
        observation={
            "evidenceClass":"E39D_REAL_CHROMIUM_FOCUS_EVIDENCE",
            "caseId":case,"phase":"before","passed":False,
            "failureCode":"E39D_FOCUS_TRANSPARENT",
            "pageJavaScriptErrors":[]
        }
        def fake_source(path, _):
            s=str(path)
            if s.endswith("seed.json"):return json.dumps(seed).encode()
            if s.endswith("mobile-before.json") or s.endswith("desktop-before.json"):
                screen="mobile" if s.endswith("mobile-before.json") else "desktop"
                return json.dumps(dict(observation,viewport=screen)).encode()
            if s.endswith("styles.css"):return current
            raise AssertionError(s)
        with mock.patch.object(subject,"checked",side_effect=fake_source):
            subject.verify_before(case)
            bad = dict(observation, failureCode="E39D_FOCUS_OFFSCREEN")
            def corrupt(path,cap):
                raw=fake_source(path,cap)
                if str(path).endswith("desktop-before.json"):
                    return json.dumps(dict(bad,viewport="desktop")).encode()
                return raw
            with mock.patch.object(subject,"checked",side_effect=corrupt):
                with self.assertRaisesRegex(ValueError,"E39D_INDEPENDENT_BROWSER_FAULT_NOT_VERIFIED"):
                    subject.verify_before(case)

    def test_evidence_classes_always_decline_automated_agent_credit(self):
        original=inspect.getsource(subject)
        for forbidden in (
            "subprocess","os.system","Popen(","import requests",
            "ollama pull ","openrouter.ai","eval(","exec("
        ):
            self.assertNotIn(forbidden,original)
        self.assertIn('"modelWasCalled": False',original)
        self.assertIn('"modelGeneratedRepair": False',original)
        self.assertIn('"ownerApproved": False',original)
        self.assertIn('"productionPublished": False',original)
        proposer=(subject.worker.HERE/"e39c2_selector_adviser.py").read_text()
        self.assertNotIn("e39d_heldout_focus_evaluator",proposer)
        self.assertNotIn("E39D_FOCUS_TRANSPARENT",proposer)


if __name__=="__main__":
    unittest.main()
