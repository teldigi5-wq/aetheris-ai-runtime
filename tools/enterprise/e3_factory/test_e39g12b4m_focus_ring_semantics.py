"""E3.9G-12B4M source-locked negative/positive browser QA for focus outline.

This independently protects model evidence and trusted fixtures; real Chromium
assertions run separately and are required for actual rendering claims.
"""
from pathlib import Path
import hashlib
import re
import unittest

HERE=Path(__file__).resolve().parent
BROWSER=HERE/"e39g12b4m_focus_ring_chromium.mjs"
BROKEN=HERE/"e39g12b4i"/"fixtures"/"focus-negative.html"
POSITIVE=HERE/"e39g12b4i"/"fixtures"/"focus-positive.html"
SOURCE_SHA="385d3fac74d775586bb389beb3068e6e34a7fb0e08f8d854c80cb55b433b290b"
MODEL_RESPONSE_SHA="51d092cbb0181fa010fe656382833ab8762a15d152aec297ac2e8d8fb29645d6"
OLD=".skip:focus{top:-120px;}"
MODEL=".skip:focus{top:0;}"
SAFE=".skip:focus{top:16px;}"


class FocusRingVisibilityRegression(unittest.TestCase):
    def test_exact_original_provenance_and_positive_only_changes_one_css_rule(self):
        original=BROKEN.read_bytes()
        positive=POSITIVE.read_bytes()
        self.assertEqual(hashlib.sha256(original).hexdigest(),SOURCE_SHA)
        self.assertEqual(original.count(OLD.encode()),1)
        self.assertEqual(positive.count(SAFE.encode()),1)
        normalized=positive.replace(b"Trusted focus positive control",
                                    b"Trusted focus negative control")
        self.assertEqual(normalized,original.replace(OLD.encode(),SAFE.encode(),1))
        # This is the actual past B4L suggestion recovered from archived
        # run 40164465244 model receipt, NOT a new model generation.
        model_result=original.replace(OLD.encode(),MODEL.encode(),1)
        self.assertNotEqual(model_result,normalized)
        self.assertIn(MODEL.encode(),model_result)

    def test_boundary_classifies_model_top_zero_and_near_six_as_clipped_outline(self):
        # Existing fixture explicitly specifies outline width 3px and offset 4px:
        # rectangle alone is insufficient; entire focus indicator needs 7px
        # clearance before rendering is counted as fully visible.
        css=BROKEN.read_text()
        self.assertIn("a:focus-visible{outline:3px solid #17487e;outline-offset:4px}",css)
        for top,expect_ring in ((-120,False),(0,False),(6,False),(8,True),(16,True)):
            self.assertEqual(top>=7,expect_ring)
        script=BROWSER.read_text()
        self.assertIn(MODEL_RESPONSE_SHA,script)
        self.assertIn("r.top>=halo",script)
        self.assertIn("r.left>=halo",script)
        self.assertIn("r.right+halo<=innerWidth",script)
        self.assertIn("r.bottom+halo<=innerHeight",script)
        self.assertIn("el.matches(':focus-visible')",script)
        self.assertIn("expectedRingClearancePixels",script)

    def test_no_other_site_or_executable_code_enters_browser(self):
        text=BROWSER.read_text()
        for required in (
            "process.argv.length !== 2",
            "javaScriptEnabled:false","serviceWorkers:'block'",
            "context.route('**/*'","route.abort()",
            "page.goto('about:blank')","page.setContent(html",
            "page.keyboard.press('Tab')","variant.newRule",
            "stage45Go:false","productionAuthorized:false",
            "revisedHistoricalB4LScore:false",
            "modelCallsPerformed:0","modelRepairNewlyCredited:false",
            "width:320","width:390","width:768","width:1280","width:1920"
        ):
            self.assertIn(required,text)
        for disallowed in (
            "child_process","spawn(","execSync(", "eval(","new Function(",
            "http://127.0.0.1","file://","fetch(","process.argv[2]",
            "e39g12b4k_model_patch_worker"
        ):
            self.assertNotIn(disallowed,text)
        self.assertEqual(text.count("newRule:"),5)
        self.assertEqual(text.count("historicallyPolicyApproved:true"),1)

    def test_no_change_to_prior_policy_contract(self):
        import e39g12b4j_typed_control_worker as frozen
        self.assertEqual(set(frozen.RULES),{"focus","motion"})
        self.assertEqual(frozen.RULES["focus"],(OLD,SAFE))
        self.assertNotEqual(MODEL,SAFE)
        self.assertEqual(frozen.FIELDS,
            {"case","operation","sourceSha256","old","new"})


if __name__=="__main__":
    unittest.main()
