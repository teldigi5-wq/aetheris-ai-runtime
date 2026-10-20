"""E3.8B guard tests; mocks never count toward the real-model benchmark."""
import json
import os
import unittest
from unittest import mock
import e38b_hermes_inference as subject


class E38BModelSafetyTest(unittest.TestCase):
    def test_no_model_or_network_without_explicit_ci_opt_in(self):
        with mock.patch.dict(os.environ, {subject.OPT_IN: "no"}):
            with mock.patch.object(subject, "model_metadata") as metadata:
                with self.assertRaisesRegex(ValueError,"E38B_REAL_MODEL_EXPLICIT_CI_OPT_IN_REQUIRED"):
                    subject.trial("scroll-24")
                metadata.assert_not_called()

    def test_all_cases_and_size_restrictions_are_fixed(self):
        self.assertEqual(set(subject.CASES),
                         {"scroll-24","mobile-940","reduced-boolean"})
        for c in ("../etc/passwd","scroll","deploy", "mobile-9999"):
            with self.subTest(c=c), self.assertRaisesRegex(
                    ValueError,"E38B_CASE_NOT_IN_EXPLICIT_ALLOWLIST"):
                with mock.patch.dict(os.environ,{subject.OPT_IN:"yes"}):
                    subject.trial(c)

    def test_model_tag_and_provenance_validation(self):
        valid={"models":[{"name":subject.MODEL,"digest":"a"*64,"size":2000000000}]}
        with mock.patch.object(subject.transport,"local_json",return_value=(valid,7)):
            result=subject.model_metadata()
            self.assertEqual(result["tag"],"hermes3:3b")
            self.assertFalse(result["identityCryptographicallyAttested"])
        for v in (
            {"models":[]},
            {"models":[{"name":subject.MODEL,"digest":"invalid","size":2000000000}]},
            {"models":[{"name":subject.MODEL,"digest":"a"*64,"size":271000000}]},
            {"models":[{"name":subject.MODEL,"digest":"a"*64,"size":9000000000}]},
        ):
            with self.subTest(value=v),mock.patch.object(
                    subject.transport,"local_json",return_value=(v,2)):
                with self.assertRaises(ValueError):
                    subject.model_metadata()

    def test_prompt_needs_actual_independent_matching_browser_failure(self):
        data={"caseId":"scroll","passed":False,
              "failureCode":"E32_SCROLL_PROGRESS_STUCK",
              "browserObserved":{"progress":24,"motion":"enabled"}}
        source=b"function progress(){return 0.86;}\nconst p = 0.24;\n"
        with self.assertRaisesRegex(ValueError,"E38B_WRONG_REAL_BROWSER_FAILURE"):
            subject.build_prompt("scroll",source,json.dumps({
                **data,"passed":True}).encode())
        with self.assertRaisesRegex(ValueError,"E38B_NO_BROWSER_OBSERVATIONS"):
            subject.build_prompt("scroll",source,json.dumps({
                **data,"browserObserved":{}}).encode())
        accepted=subject.build_prompt("scroll",source,json.dumps(data).encode())
        self.assertIn("const p = 0.24;",accepted)
        self.assertIn("untrusted",accepted.lower())

    def test_category_source_snippets_are_data_not_code_execution(self):
        cases=[
          ("scroll",b"function progress(){return 1;}\nconst p = 0.24;\n"),
          ("mobile",b"@media(max-width:760px){html{min-width:940px}body{color:#fff}}"),
          ("reduced",b"const motion = window.matchMedia('x');\nconst reduced = Boolean(false);\n")
        ]
        for category,source in cases:
            with self.subTest(category=category):
                self.assertTrue(subject.relevant_source(category,source))
                self.assertLess(len(subject.relevant_source(category,source)),1000)

    def test_no_source_change_from_model_module(self):
        import inspect
        src=inspect.getsource(subject)
        for forbidden in ("subprocess","os.system","os.popen","Popen(",
                          "shutil.rmtree","requests.post","urllib.request"):
            self.assertNotIn(forbidden,src)
        self.assertIn('"ownerApproved": False',src)
        self.assertIn('"sourceModifiedByModel": False',src)


if __name__=="__main__":
    unittest.main()
