"""E3.7 Hermes and advanced open-weight catalog: no installs or live inference."""
import json
import os
import unittest
from unittest import mock

import e36_free_model_router as router


class VerifiedOpenWeightCatalogTest(unittest.TestCase):
    def test_11_local_and_5_free_remote_candidates_are_exact(self):
        self.assertEqual(len(router.LOCAL_CANDIDATES), 11)
        self.assertEqual(len(set(router.LOCAL_CANDIDATES)), 11)
        self.assertEqual(len(router.CLOUD_CANDIDATES), 5)
        self.assertIn("hermes3:3b", router.LOCAL_CANDIDATES)
        self.assertIn("hermes3:8b", router.LOCAL_CANDIDATES)
        for tag in ("qwen3.5:2b", "qwen3.5:4b", "qwen3.5:9b",
                    "gemma3:4b", "deepseek-r1:7b", "qwen3-coder:30b"):
            self.assertIn(tag, router.LOCAL_CANDIDATES)

    def test_hermes_4_is_documented_but_not_an_unreviewed_callable_tag(self):
        for tag in ("NousResearch/Hermes-4-14B",
                    "NousResearch/Hermes-4-70B"):
            self.assertIn(tag, router.RESEARCH_ONLY_MODELS)
            self.assertFalse(router.RESEARCH_ONLY_MODELS[tag]["localRuntimeValidated"])
            self.assertNotIn(tag, router.LOCAL_CANDIDATES)
            with self.assertRaisesRegex(ValueError,
                                         "E36_UNKNOWN_OR_PAID_MODEL_REFUSED"):
                router.checked_choice("ollama", tag)

    def test_old_mistyped_artemis_does_not_route_to_any_model(self):
        with self.assertRaisesRegex(ValueError, "E36_ARTEMIS_ID_UNVERIFIED"):
            router.checked_choice("ollama", "artemis")
        entry = router.catalog_status()
        self.assertEqual(entry["historicalNameCorrection"]["artemis"],
                         "MISIDENTIFICATION_CORRECTED_TO_HERMES")
        self.assertFalse(entry["automaticModelFallback"])

    def test_even_verified_tags_require_actual_installation_and_opt_in(self):
        for tag in ("hermes3:3b", "hermes3:8b", "qwen3.5:4b",
                    "gemma3:4b", "deepseek-r1:7b"):
            with self.subTest(tag=tag):
                with mock.patch.dict(os.environ, {router.OPT_LOCAL: "yes"}):
                    with mock.patch.object(router, "create_context",
                                           return_value=(b"source", b"diagnosis", "prompt")):
                        with mock.patch.object(router, "send",
                                               return_value=({"models": []}, 1)) as sent:
                            with self.assertRaisesRegex(
                                    ValueError, "E36_LOCAL_MODEL_NOT_INSTALLED"):
                                router.request_advice("ollama", tag, "scroll")
                            self.assertEqual(sent.call_count, 1)

    def test_no_local_model_or_remote_call_by_default(self):
        with mock.patch.dict(os.environ, {router.OPT_LOCAL: "no"}):
            with mock.patch.object(router, "create_context",
                                   return_value=(b"source", b"diagnosis", "prompt")):
                with mock.patch.object(router, "send") as sent:
                    with self.assertRaisesRegex(
                            ValueError, "E36_LOCAL_MODEL_NOT_OPTED_IN"):
                        router.request_advice("ollama", "hermes3:3b", "scroll")
                    sent.assert_not_called()

    def test_router_cannot_install_weights_or_grant_agent_permissions(self):
        import inspect
        content = inspect.getsource(router)
        for dangerous in ("subprocess", "os.system", "Popen(",
                          "ollama pull ", "docker run ", "requests.post"):
            self.assertNotIn(dangerous, content)
        self.assertIn('"ownerApprovalGranted": False', content)
        self.assertIn('"responseWasExecuted": False', content)


if __name__ == "__main__":
    unittest.main()
