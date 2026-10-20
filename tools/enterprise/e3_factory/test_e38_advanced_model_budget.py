"""E3.8 advanced local model selection must preserve opt-in and hardware budget gates.

All tests are mocked: NONE claim these models were installed or inferred in CI.
"""
import json
import os
from pathlib import Path
import unittest
from unittest import mock

import e36_free_model_router as router


class AdvancedModelBudgetTests(unittest.TestCase):
    def setUp(self):
        self.fixture = (b"const p = 0.24;\n",
                        b'{"caseId":"scroll","passed":false}', "Synthetic test data only")

    def test_expansion_counts_and_exact_advanced_tags(self):
        self.assertEqual(len(router.LOCAL_CANDIDATES), 20)
        self.assertEqual(len(set(router.LOCAL_CANDIDATES)), 20)
        self.assertEqual(len(router.CLOUD_CANDIDATES), 6)
        self.assertEqual(len(set(router.CLOUD_CANDIDATES)), 6)
        expected = {
            "qwen3.5:0.8b", "qwen3.5:27b", "qwen3.5:35b",
            "granite4:3b", "granite4:7b-a1b-h",
            "gpt-oss:20b", "gpt-oss:120b",
            "devstral-small-2:24b", "qwen3-coder-next:q4_K_M",
        }
        self.assertTrue(expected.issubset(router.LOCAL_CANDIDATES))
        self.assertIn("nvidia/nemotron-3.5-super-vl:free", router.CLOUD_CANDIDATES)
        self.assertNotIn("gpt-oss:20b-cloud", router.LOCAL_CANDIDATES)

    def test_high_resource_models_are_explicit_and_do_not_autorun(self):
        heavy = router.HIGH_RESOURCE_LOCAL_CANDIDATES
        self.assertTrue({
            "qwen3.5:9b", "qwen3.5:27b", "qwen3.5:35b",
            "qwen3-coder:30b", "gpt-oss:20b", "gpt-oss:120b",
            "devstral-small-2:24b", "qwen3-coder-next:q4_K_M"
        }.issubset(heavy))
        self.assertFalse("granite4:3b" in heavy)
        with mock.patch.object(router, "create_context", return_value=self.fixture):
            with mock.patch.object(router, "send") as send:
                for name in sorted(heavy):
                    with self.subTest(tag=name), mock.patch.dict(os.environ, {
                        router.OPT_LOCAL: "yes", router.OPT_HIGH_RESOURCE: "no"}):
                        with self.assertRaisesRegex(
                                ValueError, "E38_HIGH_RESOURCE_MODEL_REQUIRES_SECOND_OPT_IN"):
                            router.request_advice("ollama", name, "scroll")
                send.assert_not_called()

    def test_two_opt_ins_still_require_installed_model(self):
        tag = "gpt-oss:20b"
        env = {router.OPT_LOCAL: "yes", router.OPT_HIGH_RESOURCE: "yes"}
        with mock.patch.dict(os.environ, env), mock.patch.object(
                router, "create_context", return_value=self.fixture), mock.patch.object(
                router, "send", return_value=({"models":[]},1)) as sent:
            with self.assertRaisesRegex(ValueError, "E36_LOCAL_MODEL_NOT_INSTALLED"):
                router.request_advice("ollama", tag, "scroll")
            self.assertEqual(sent.call_count,1)
            self.assertEqual(sent.call_args.args[:3],("ollama","GET","/api/tags"))

    def test_opted_in_installed_model_response_is_read_only(self):
        tag = "gpt-oss:20b"
        def fake_send(provider, method, route, **kwargs):
            if route == "/api/tags":
                return {"models":[{"name":tag}]},1
            self.assertEqual(kwargs["payload"]["model"],tag)
            return {"model":tag,"done":True,
                    "response":'{"operation":"abstain"}'},3
        env = {router.OPT_LOCAL: "yes",router.OPT_HIGH_RESOURCE:"yes"}
        with mock.patch.dict(os.environ,env),mock.patch.object(
                router,"create_context",return_value=self.fixture),mock.patch.object(
                router,"send",side_effect=fake_send):
            result=router.request_advice("ollama",tag,"scroll")
        self.assertEqual(result["responseClass"],"ABSTAIN")
        self.assertFalse(result["responseWasExecuted"])
        self.assertFalse(result["ownerApprovalGranted"])
        self.assertFalse(result["productionPublished"])

    def test_future_twelve_are_separate_from_active_routes(self):
        ref = (Path(__file__).resolve().parents[3] /
               "docs/runtime/enterprise-e38-future-model-research.json")
        # parent[3] should point to repository root for tools/enterprise/e3_factory/file
        if not ref.exists():
            ref = Path(__file__).resolve().parents[3] / (
                "docs/runtime/enterprise-e38-future-model-research.json")
        content=json.loads(ref.read_text(encoding="utf-8"))
        self.assertEqual(content["counts"]["totalFutureResearchCandidates"],12)
        self.assertEqual(len(content["additional"]),10)
        self.assertEqual(len(content["includesExistingResearchOnly"]),2)
        self.assertFalse(content["rules"]["autoLaunch"])
        all_ids=[x["id"] for x in content["additional"]]
        self.assertEqual(len(set(all_ids)),10)
        self.assertTrue(all(x["status"]=="FUTURE_RESEARCH_NOT_CALLABLE"
                            for x in content["additional"]))
        for identity in all_ids:
            self.assertNotIn(identity,router.LOCAL_CANDIDATES)
            self.assertNotIn(identity,router.CLOUD_CANDIDATES)
        for identity in content["includesExistingResearchOnly"]:
            self.assertIn(identity,router.RESEARCH_ONLY_MODELS)
        self.assertEqual(
            sum(1 for x in router.CLOUD_CANDIDATES if x=="openrouter/free"),1)

    def test_no_download_or_model_auto_tool_activation(self):
        import inspect
        code=inspect.getsource(router)
        for forbidden in ("subprocess","os.system","Popen(","ollama pull ",
                          "docker run ","import requests","requests.post"):
            self.assertNotIn(forbidden,code)
        catalog=router.catalog_status()
        self.assertFalse(catalog["modelDownloadsAutomatically"])
        self.assertEqual(len(catalog["highResourceLocalCandidatesRequireSecondOptIn"]),
                         len(router.HIGH_RESOURCE_LOCAL_CANDIDATES))
        self.assertFalse(catalog["productionEnabled"])


if __name__ == "__main__":
    unittest.main()
