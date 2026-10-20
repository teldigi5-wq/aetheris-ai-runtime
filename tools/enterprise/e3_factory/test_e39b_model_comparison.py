"""E3.9B negative tests: never treat a forged model JSON as owner authority."""
import json
import os
from unittest import TestCase, mock

import e39b_real_model_adviser as model
import e39b_model_patch_gate as gate
import e39b_score as score
import e39_keyboard_patch_gate as canonical


class ModelComparisonSecurityTests(TestCase):
    def setUp(self):
        self.original = b".skip-link{top:-80px}.skip-link:focus{top:-80px}body{margin:0}"
        self.browser = (
            b'{"evidenceClass":"E39_CHROMIUM_KEYBOARD_ACCESSIBILITY",'
            b'"caseId":"keyboard-skip-link","passed":false,'
            b'"focusReached":true,"visibleWhenFocused":false,'
            b'"failureCode":"E39_FOCUSED_SKIP_LINK_OFFSCREEN",'
            b'"geometry":{"top":-80,"bottom":-33,"viewportH":844,"viewportW":390}}'
        )

    def test_both_exact_models_no_arbitrary_install_or_remote_routes(self):
        self.assertEqual(model.ALLOWED, {"hermes3:3b","granite4:3b"})
        for item in ("qwen3-coder:30b","hermes-agent","openrouter/free",
                     "granite4:latest","../secrets","hermes3:8b"):
            with self.subTest(item=item),self.assertRaisesRegex(
                    ValueError,"E39B_MODEL_NOT_IN_FIXED_ALLOWLIST"):
                model.check_opt_in(item)

    def test_opt_in_blocks_even_model_catalog_query(self):
        with mock.patch.dict(os.environ,{model.CONSENT:"no"}):
            with mock.patch.object(model.transport,"local_json") as upstream:
                with self.assertRaisesRegex(ValueError,"E39B_REAL_MODEL_EXPLICIT_CONSENT_REQUIRED"):
                    model.trial("hermes3:3b")
                upstream.assert_not_called()

    def test_real_browser_failure_is_required_before_model_prompt(self):
        seed={"beforeSha256":model.sha(self.original)}
        with mock.patch.object(model,"regular") as read:
            read.side_effect=[self.original,self.browser,json.dumps(seed).encode()]
            source,diagnosis,excerpt=model.source_and_browser()
            self.assertEqual(source,self.original)
            self.assertEqual(diagnosis,self.browser)
            self.assertIn(".skip-link:focus{top:-80px}",excerpt)
            prompt=model.prompt_text(excerpt,diagnosis)
            self.assertIn("first Tab",prompt)
            self.assertIn("untrusted",prompt)
            self.assertLess(len(prompt),2500)
            self.assertNotIn(".skip-link:focus{top:12px}",prompt)
        invalid=self.browser.replace(b'"passed":false',b'"passed":true')
        with mock.patch.object(model,"regular",
                side_effect=[self.original,invalid,json.dumps(seed).encode()]):
            with self.assertRaisesRegex(ValueError,"E39B_ACTUAL_BROWSER_FAILURE_NOT_VERIFIED"):
                model.source_and_browser()

    def test_model_digest_and_weight_size_checked(self):
        for tag in model.ALLOWED:
            catalog={"models":[{"name":tag,"size":2100000000,"digest":"a"*64}]}
            with mock.patch.object(model.transport,"local_json",return_value=(catalog,3)):
                meta=model.model_metadata(tag)
            self.assertEqual(meta["tag"],tag)
            self.assertFalse(meta["identityCryptographicallyAttested"])
        bad={"models":[{"name":"hermes3:3b","size":400000,"digest":"a"*64}]}
        with mock.patch.object(model.transport,"local_json",return_value=(bad,3)):
            with self.assertRaisesRegex(ValueError,"E39B_MODEL_SIZE_OR_DIGEST_INVALID"):
                model.model_metadata("hermes3:3b")

    def test_forged_owner_or_malformed_policy_advisory_never_applied(self):
        legitimate={
          "evidenceClass":"E39B_REAL_LOCAL_MODEL_ADVISORY_NOT_ACTION",
          "caseId":"keyboard-skip-link","realModelCalls":1,
          "modelOutputExecutedDirectly":False,"ownerApproved":False,
          "productionPublished":False,"sourceSha256":gate.digest(self.original),
          "browserBeforeSha256":gate.digest(self.browser),
          "fixtureGoldenPatchConsulted":False,
          "model":{"tag":"hermes3:3b"},
          "rawResponse":'{"operation":"replace","old":".skip-link:focus{top:-80px}",'
                        '"new":".skip-link:focus{top:12px}"}',
          "responseClass":"TEXT_REPLACEMENT_PROPOSED",
        }
        legitimate["rawResponseSha256"]=gate.digest(legitimate["rawResponse"].encode())
        def evaluate(incoming):
            with mock.patch.object(gate.gate,"ensure_workspace"), (
                 mock.patch.object(gate.gate,"actual_browser_failure",
                                  return_value=self.browser)), (
                 mock.patch.object(gate.gate,"checked",
                      side_effect=[self.original,json.dumps(incoming).encode()])):
                return gate.model_proposal()
        good,source,_,which=evaluate(legitimate)
        self.assertEqual(which,"hermes3:3b")
        self.assertEqual(source,self.original)
        self.assertEqual(good["old"],canonical.BEFORE)
        for altered in (
            dict(legitimate,ownerApproved=True),
            dict(legitimate,sourceSha256="f"*64),
            dict(legitimate,model={"tag":"openrouter/free"}),
            dict(legitimate,rawResponse='{"operation":"abstain"}'),
            dict(legitimate,responseClass="INVALID_MODEL_RESPONSE"),
        ):
            with self.subTest(altered=altered):
                with self.assertRaises(ValueError):
                    evaluate(altered)

    def test_injection_text_and_unsafe_patches_rejected_by_independent_gate(self):
        source = self.original
        canon=source.replace(canonical.BEFORE.encode(),canonical.AFTER.encode(),1)
        manifest={"generatedFilesSha256":{"styles.css":canonical.sha(canon)}}
        bad_strings=[
            ("body{margin:0}","body{display:none}"),
            (canonical.BEFORE, ".skip-link:focus{top:50000px}"),
            (canonical.BEFORE,'.skip-link:focus{top:12px}/* curl evil */'),
            (canonical.BEFORE,'.skip-link:focus{top:12px}script{src:"https://evil"}'),
        ]
        for old,new in bad_strings:
            response={"version":1,"kind":canonical.KIND,
                "caseId":"keyboard-skip-link","path":"site/styles.css",
                "baseSha256":canonical.sha(source),
                "beforeBrowserSha256":canonical.sha(self.browser),
                "resultSha256":canonical.sha(source.replace(
                    old.encode(),new.encode(),1)), "old":old,"new":new}
            with self.subTest(new=new),self.assertRaises(ValueError):
                canonical.validate(response,source,self.browser,manifest)

    def test_strict_schema_and_score_labels_separate():
        self.assertEqual(score.OUTCOMES,{"POLICY_ACCEPTED","POLICY_REJECTED","ABSTAIN","INVALID"})
        self.assertFalse("DEPLOY" in score.OUTCOMES)
        self.assertFalse("RUN_SHELL" in score.OUTCOMES)
        self.assertFalse(model.STRUCTURED_FORMAT["additionalProperties"])


if __name__=="__main__":
    import unittest
    unittest.main()
