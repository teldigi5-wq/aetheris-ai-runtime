"""B4N fail-closed constrained focus CSS and separate real Chrome policy checks."""
from __future__ import annotations
import json
from pathlib import Path
import unittest

import e39g12b4n_focus_semantic_policy as n
import e39g12b4j_typed_control_worker as frozen

HERE=Path(__file__).resolve().parent
RUNNER=HERE/"run_locked_worker.sh"
BROWSER=HERE/"e39g12b4n_semantic_after_browser.mjs"


def attempt(case,top):
    return {
        "caseId":case,"operation":"replace_one",
        "sourceSha256":n.ORIGINAL_SHA,"old":n.OLD,
        "new":f".skip:focus{{top:{top}px;}}"
    }


class SafeAlternativePolicy(unittest.TestCase):
    def test_only_trusted_source_and_exact_case_vocabulary(self):
        html=n.original()
        self.assertEqual(n.sha(html),n.ORIGINAL_SHA)
        self.assertEqual(set(n.CASES),
            {"zero","six","eight","sixteen","thirtytwo","malicious"})
        for case,top in (("zero",0),("six",6),("eight",8),
                         ("sixteen",16),("thirtytwo",32)):
            result,decision,pixels=n.inspect(case,attempt(case,top),html)
            self.assertEqual(decision,"STRUCTURAL_ONLY_BROWSER_REVIEW_REQUIRED")
            self.assertEqual(pixels,top)
            self.assertEqual(result,html.replace(n.OLD.encode(),
                f".skip:focus{{top:{top}px;}}".encode(),1))
            self.assertNotEqual(result,html)
            self.assertNotIn(b"<script",result.lower())
            self.assertIn(b"default-src 'none'",result)
        self.assertEqual(n.inspect("malicious",{
            **attempt("malicious",16),
            "new":"<script>alert('owner')</script>"
        },html),(None,"RULE_FORMAT_REJECTED",None))

    def test_schema_and_source_authorization_denial(self):
        html=n.original()
        candidate=attempt("eight",8)
        variations=(
          (dict(candidate,extraAuthority="admin"),"SCHEMA_REJECTED"),
          (dict(candidate,operation="exec"),"PROVENANCE_OR_IDENTITY_REJECTED"),
          (dict(candidate,caseId="other"),"PROVENANCE_OR_IDENTITY_REJECTED"),
          (dict(candidate,sourceSha256="0"*64),"PROVENANCE_OR_IDENTITY_REJECTED"),
          (dict(candidate,old=".skip{top:-120px;}"),"PROVENANCE_OR_IDENTITY_REJECTED"),
          (dict(candidate,new=".skip:focus{top:065px;}"),"RULE_FORMAT_REJECTED"),
          (dict(candidate,new=".skip:focus{top:65px;}"),"RULE_OUT_OF_RANGE"),
          (dict(candidate,new=".skip:focus{top:-1px;}"),"RULE_FORMAT_REJECTED"),
          (dict(candidate,new=".skip:focus{top:0;}"),"RULE_FORMAT_REJECTED"),
          (dict(candidate,new=".skip:focus{top:8px;}/*attack*/"),"RULE_FORMAT_REJECTED"),
          (dict(candidate,new=".skip:focus{top:8px;background:url(http://evil)}"),"RULE_FORMAT_REJECTED"),
          (dict(candidate,new=8),"RULE_FORMAT_REJECTED"),
        )
        for obj,why in variations:
            with self.subTest(why=why,obj=obj):
                output,category,top=n.inspect("eight",obj,html)
                self.assertIsNone(output)
                self.assertIsNone(top)
                self.assertEqual(category,why)
        with self.assertRaisesRegex(ValueError,"B4N_CASE_NOT_ALLOWLISTED"):
            n.inspect("../customer-prod",candidate,html)

    def test_source_changes_cannot_be_silently_accepted(self):
        html=n.original()
        changed=html+b"unexpected"
        original=attempt("eight",8)
        result,reason,_=n.inspect("eight",original,changed)
        self.assertIsNone(result)
        self.assertEqual(reason,"PROVENANCE_OR_IDENTITY_REJECTED")

    def test_frozen_historical_exact_rule_and_ratings(self):
        self.assertEqual(frozen.RULES["focus"],
            (n.OLD,".skip:focus{top:16px;}"))
        proposal={"case":"focus","operation":"replace_one",
                  "sourceSha256":n.ORIGINAL_SHA,"old":n.OLD,
                  "new":".skip:focus{top:8px;}"}
        ref=frozen.checked(frozen.FIX/"focus-positive.html",5000)
        with self.assertRaisesRegex(ValueError,"B4J_UNAUTHORIZED_EDIT"):
            frozen.review("focus",proposal,n.original(),ref)
        self.assertEqual(n.TEST_CLASS,
            "E39G12B4N_SYNTHETIC_BOUNDED_CSS_REVIEW_NOT_AI")

    def test_runner_and_browser_are_fixed_allowlisted_no_js_or_network(self):
        script=RUNNER.read_text(encoding="utf-8")
        self.assertIn("e39g12b4n-probe)",script)
        self.assertIn("zero|six|eight|sixteen|thirtytwo|malicious",script)
        for token in ("--network none","--read-only","--cap-drop ALL",
                      "--security-opt no-new-privileges","--memory 256m"):
            self.assertIn(token,script)
        browser=BROWSER.read_text(encoding="utf-8")
        for token in ("process.argv.length!==2","javaScriptEnabled:false",
                      "serviceWorkers:'block'","route.abort()",
                      "page.goto('about:blank')","B4N_WHOLE_SOURCE_CHANGED",
                      "r.top>=margin","el.matches(':focus-visible')",
                      "genuinelyHeldOut:false","modelCallsPerformed:0",
                      "ownerApprovalGranted:false","modelRepairCredited:false",
                      "semanticPassedCases","semanticFailedCases"):
            self.assertIn(token,browser)
        for bad in ("child_process","execSync(","spawn(","eval(","new Function(",
                    "http://127.0.0.1","process.argv[2]","file://"):
            self.assertNotIn(bad,browser)


if __name__=="__main__":
    unittest.main()
