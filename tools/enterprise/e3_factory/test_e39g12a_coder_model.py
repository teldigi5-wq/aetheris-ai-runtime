"""E3.9G-12A adversarial licensed coding model pilot tests. No network used."""
from __future__ import annotations
import json
import os
from pathlib import Path
import unittest
from unittest import mock

import e39g12a_coder_adviser as coder
import e39g12a_coder_gate as gate
import e39g12a_coder_score as score
import e39g9_multidomain_worker as frozen

class ApacheCodingModelPilotTests(unittest.TestCase):
    def test_approved_license_and_digest_from_upstream_are_not_owner_identity(self):
        digest=coder.REVIEWED_DIGEST_PREFIX+"c"*(64-len(coder.REVIEWED_DIGEST_PREFIX))
        with mock.patch.object(coder.transport,"local_json",
          return_value=({"models":[{"name":"qwen2.5-coder:1.5b",
                        "size":986_000_000,"digest":digest}]},2)):
            received=coder.candidate_metadata("qwen2.5-coder:1.5b")
        self.assertEqual(received["selfReportedDigest"],digest)
        self.assertEqual(received["declaredUpstreamLicense"],"Apache-2.0")
        self.assertIs(received["licenseAndModelIdentityCryptographicallyAttested"],False)

    def test_digest_or_model_drift_refused(self):
        samples=[
            {"name":"qwen2.5-coder:1.5b","size":986_000_000,"digest":"a"*64},
            {"name":"qwen2.5-coder:1.5b","size":5_000_000_000,
                 "digest":coder.REVIEWED_DIGEST_PREFIX+"c"*52},
            {"name":"qwen2.5-coder:3b","size":986_000_000,
                 "digest":coder.REVIEWED_DIGEST_PREFIX+"c"*52},
        ]
        for item in samples:
            with self.subTest(sample=item):
                with mock.patch.object(coder.transport,"local_json",
                    return_value=({"models":[item]},2)):
                    with self.assertRaisesRegex(ValueError,"E39G12A_"):
                        coder.candidate_metadata("qwen2.5-coder:1.5b")

    def test_model_and_site_scope_fixed(self):
        self.assertEqual(coder.MODELS,{"qwen2.5-coder:1.5b":"qwen25-coder-15b"})
        self.assertEqual(set(coder.CASES),{"mobile-940","reduced-boolean","scroll-24"})
        self.assertEqual(set(coder.ARMS),{"original","targeted"})
        for case in coder.CASES:
            for arm in coder.ARMS:
                self.assertEqual(gate.trial(f"qwen25-coder-15b--{case}--{arm}")[1:3],(case,arm))
        for bad in ("qwen25-coder-15b--scroll-24--semantic",
                    "qwen25-coder-15b--scroll-24--owner",
                    "../qwen25-coder-15b--scroll-24--targeted",
                    "granite4-3b--scroll-24--targeted"):
            with self.assertRaisesRegex(ValueError,"E39G12A_EXACT_MODEL"):
                gate.trial(bad)

    def test_explicit_opt_in_before_network_or_metadata(self):
        with mock.patch.dict(os.environ,{},clear=True):
            with mock.patch.object(coder.transport,"local_json") as request:
                with self.assertRaisesRegex(ValueError,"E39G12A_ACTUAL_MODEL_OPT_IN_REQUIRED"):
                    coder.run("qwen2.5-coder:1.5b","scroll-24")
                request.assert_not_called()
        with self.assertRaisesRegex(ValueError,"E39G12A_MODEL_CASE_OUT_OF_SCOPE"):
            coder.run("qwen2.5-coder:3b","scroll-24")

    def test_same_source_targeted_prompt_and_skills_as_original_benchmark(self):
        import e39g10_targeted_adviser as historical
        self.assertEqual(coder.targeted_prompt.__code__.co_code,
                         historical.targeted_prompt.__code__.co_code)
        self.assertEqual(coder.prior.prompt.__code__.co_code,
                         historical.prior.prompt.__code__.co_code)
        self.assertEqual(coder.FLAG,
                         "AETHERIS_E39G12A_ACTUAL_TARGETED_PROMPT_EXPLICIT_OPT_IN")

    def test_original_frozen_repair_policy_not_overridden_by_new_model(self):
        _,filename,clean,broken=frozen.CASES["scroll-24"]
        source=(frozen.worker.HERE/filename).read_bytes()
        dirty=source.decode().replace(clean,broken,1).encode()
        with self.assertRaisesRegex(ValueError,"E39G9_"):
            frozen.permitted("scroll-24",{"operation":"replace",
                "old":"const p = 0.24;","new":"const p = 1.00;"},dirty,source)
        self.assertIn("frozen.permitted",Path(gate.__file__).read_text())

    def test_no_model_oracle_source_mutation_or_false_credit(self):
        proposer=Path(coder.__file__).read_text()
        judge=Path(score.__file__).read_text()
        browser=Path(__file__).with_name("e39g12a_coder_browser.mjs").read_text()
        runner=Path(__file__).with_name("run_locked_worker.sh").read_text()
        self.assertNotIn("import e39g9_multidomain_worker",proposer)
        self.assertNotIn("import e39g12a_coder_gate",proposer)
        self.assertNotIn("subprocess",proposer)
        self.assertNotIn("os.system(",proposer)
        self.assertIn("browserVerifiedModelRepair",judge)
        self.assertIn("deterministicResetCountedAsRepair",judge)
        self.assertIn("chromium.launch",browser)
        self.assertIn("screenshotSha256",browser)
        self.assertIn("e39g12a-apply",runner)
        self.assertIn("--network none",runner)

if __name__=="__main__":unittest.main()
