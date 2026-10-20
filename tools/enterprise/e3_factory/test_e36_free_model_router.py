"""E3.6 free-only multi-model route safety tests, entirely offline/mocked."""
import json
import os
import unittest
from unittest import mock

import e36_free_model_router as router


class MultiModelSafetyTests(unittest.TestCase):
    def setUp(self):
        self.synthetic = (
            b"function progress(){return 1;}\nconst p = 0.24;\n",
            b'{"caseId":"scroll","failureCode":"E32_SCROLL_PROGRESS_STUCK","passed":false}',
            "Fix the synthetic scroll example only."
        )

    def test_lists_three_local_and_three_free_candidates_and_unresolved_artemis(self):
        record = router.catalog_status()
        self.assertEqual(len(record["ollamaLocalCandidatesNotNecessarilyInstalled"]), 3)
        self.assertEqual(len(record["openrouterZeroPriceCandidatesRequireLiveCheck"]), 3)
        self.assertEqual(record["unresolvedCandidates"]["artemis"],
                         "UNRESOLVED_ID_NOT_CALLABLE")
        self.assertFalse(record["productionEnabled"])
        with self.assertRaisesRegex(ValueError, "E36_ARTEMIS_ID_UNVERIFIED"):
            router.checked_choice("openrouter", "artemis")

    def test_paid_variants_cloud_redirects_and_invented_arbitrary_ids_refused(self):
        for name in ("google/gemma-4-31b-it", "openai/gpt-4",
                     "google/gemma-4-31b-it:free",
                     "https://evil.invalid", "artemis"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                router.checked_choice("openrouter", name)
        for name in ("../../secrets", "qwen2.5:72b", "artemis"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                router.checked_choice("ollama", name)
        for values in (
            ("openrouter", "GET", "https://evil.invalid"),
            ("openrouter", "POST", "/api/v1/models"),
            ("ollama", "POST", "/api/pull"),
            ("other", "GET", "/api/tags"),
        ):
            with self.subTest(values=values), self.assertRaisesRegex(
                    ValueError, "E36_REMOTE_HOST_OR_ROUTE_DENIED"):
                router.send(*values)

    def test_prices_require_three_independent_zero_values(self):
        model = router.CLOUD_CANDIDATES[1]
        good = {"data": [{"id": model, "pricing": {
            "prompt": "0", "completion": 0, "request": "0.0000"}}]}
        self.assertTrue(router.validated_free_entry(good, model)["liveCatalogValidated"])
        for bad in (
            {"data": []},
            {"data": [{"id": model, "pricing": {
                "prompt": "0", "completion": "0.1", "request": "0"}}]},
            {"data": [{"id": model, "pricing": {
                "prompt": "0", "completion": "0"}}]},
            {"data": [{"id": model, "pricing": {
                "prompt": "free", "completion": "0", "request": "0"}}]},
            {"data": [{"id": model, "pricing": {
                "prompt": "0", "completion": "0", "request": "0"}},
                      {"id": model, "pricing": {
                          "prompt": "0", "completion": "0", "request": "0"}}]},
        ):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                router.validated_free_entry(bad, model)

    def test_remote_source_upload_needs_both_permissions_and_key(self):
        model = router.CLOUD_CANDIDATES[1]
        with mock.patch.object(router, "create_context", return_value=self.synthetic), (
                mock.patch.object(router, "send") as send):
            for env in (
                {router.OPT_CLOUD: "no", router.OPT_SHARE: "yes",
                 router.KEY_ENV: "long-example-nonsecret"},
                {router.OPT_CLOUD: "yes", router.OPT_SHARE: "no",
                 router.KEY_ENV: "long-example-nonsecret"},
                {router.OPT_CLOUD: "yes", router.OPT_SHARE: "yes",
                 router.KEY_ENV: ""},
            ):
                with self.subTest(env=env), mock.patch.dict(os.environ, env):
                    with self.assertRaises(ValueError):
                        router.request_advice("openrouter", model, "scroll")
            send.assert_not_called()

    def test_valid_external_free_request_sends_exactly_one_inference_after_probe(self):
        model = router.CLOUD_CANDIDATES[1]
        calls = []
        key = "fake-test-key-not-real"
        def fake(provider, method, path, **kwargs):
            calls.append((provider, method, path, kwargs))
            if method == "GET":
                return ({"data": [{"id": model, "pricing": {
                    "prompt": "0", "completion": "0", "request": "0"}}]}, 3)
            return ({"model": model, "choices": [
                {"message": {"content": '{"operation":"abstain"}'}}],
                "usage": {"cost": 0}}, 9)
        env = {router.OPT_CLOUD: "yes", router.OPT_SHARE: "yes",
               router.KEY_ENV: key}
        with mock.patch.dict(os.environ, env), (
                mock.patch.object(router, "create_context", return_value=self.synthetic)), (
                mock.patch.object(router, "send", side_effect=fake)):
            result = router.request_advice("openrouter", model, "scroll")
        self.assertEqual([x[1] for x in calls], ["GET", "POST"])
        self.assertEqual(calls[1][2], "/api/v1/chat/completions")
        self.assertEqual(calls[1][3]["payload"]["model"], model)
        self.assertEqual(result["responseClass"], "ABSTAIN")
        self.assertFalse(result["responseWasExecuted"])
        self.assertTrue(result["externalSourceDisclosure"])
        self.assertFalse(result["productionPublished"])
        self.assertNotIn(key, json.dumps(result))
        self.assertEqual(result["currentPricingOrLocalStatus"]["validatedPromptPrice"], "0")

    def test_cloud_provider_reported_cost_fails_closed(self):
        model = router.CLOUD_CANDIDATES[0]
        def mocked(provider, method, route, **kwargs):
            if method == "GET":
                return ({"data": [{"id": model, "pricing": {
                    "prompt": "0", "completion": "0", "request": "0"}}]}, 2)
            return ({"model": "some/free",
                     "choices": [{"message": {"content": '{"operation":"abstain"}'}}],
                     "usage": {"cost": 0.01}}, 3)
        with mock.patch.dict(os.environ, {router.OPT_CLOUD: "yes",
                                          router.OPT_SHARE: "yes",
                                          router.KEY_ENV: "fake-long-nonsecret"}), (
                mock.patch.object(router, "create_context", return_value=self.synthetic)), (
                mock.patch.object(router, "send", side_effect=mocked)):
            with self.assertRaisesRegex(ValueError, "E36_PROVIDER_REPORTED_NONZERO_COST"):
                router.request_advice("openrouter", model, "scroll")

    def test_local_model_uninstalled_fails_and_no_remote_fallback(self):
        with mock.patch.dict(os.environ, {router.OPT_LOCAL: "yes"}), (
                mock.patch.object(router, "create_context", return_value=self.synthetic)), (
                mock.patch.object(router, "send", return_value=({"models": []}, 1)) as send):
            with self.assertRaisesRegex(ValueError, "E36_LOCAL_MODEL_NOT_INSTALLED"):
                router.request_advice("ollama", router.LOCAL_CANDIDATES[0], "scroll")
            self.assertEqual(send.call_count, 1)
            self.assertEqual(send.call_args.args[:2], ("ollama", "GET"))

    def test_local_response_stays_untrusted_and_is_not_applied(self):
        model = router.LOCAL_CANDIDATES[0]
        def mocked(provider, method, route, **kwargs):
            if method == "GET":
                return {"models": [{"name": model}]}, 1
            return {"model": model, "done": True,
                    "response": '{"operation":"replace","old":"const p = 0.24;",'
                                '"new":"const p = progress();"}'}, 7
        with mock.patch.dict(os.environ, {router.OPT_LOCAL: "yes"}), (
                mock.patch.object(router, "create_context", return_value=self.synthetic)), (
                mock.patch.object(router, "send", side_effect=mocked)):
            result = router.request_advice("ollama", model, "scroll")
        self.assertEqual(result["responseClass"], "TEXT_PROPOSAL_REQUIRES_INDEPENDENT_PATCH_GATE")
        self.assertFalse(result["responseWasExecuted"])
        self.assertFalse(result["ownerApprovalGranted"])
        self.assertFalse(result["gitHubWrites"])
        self.assertFalse(result["externalSourceDisclosure"])

    def test_error_envelopes_are_rejected_without_charging_or_automatic_fallback(self):
        with self.assertRaisesRegex(ValueError, "E36_RESPONSE_OVERSIZED"):
            router.checked_json(b"x" * (router.MAX_BODY + 1))
        for text in (b"null", b"[]", b"not-json"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                router.checked_json(text)


if __name__ == "__main__":
    unittest.main()
