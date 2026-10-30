"""E4→E5 opt-in REAL transport contract with mocked Ollama; no CI model claims."""
from __future__ import annotations

import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase, main, mock

import e36_free_model_router as models
import e4_local_proposal_session as local
import e5_creative_brief_gate as gate
import e5_local_copy_trial as trial

MODEL = "qwen2.5-coder:1.5b"
BRIEF = {"version": 1, "brandLabel": "Prototype Atelier",
         "requiredTerms": ["quiet", "motion"],
         "forbiddenTerms": ["certified"]}
COPY = {"version": 1, "brandLabel": "Prototype Atelier",
        "headlineLines": ["Quiet materials.", "Purposeful motion."]}


def enabled():
    return {local.LOCAL_EXPERIMENT_OPT_IN: "yes",
            models.OPT_LOCAL: "yes"}


def fake_api(*_args, **kwargs):
    route = _args[2]
    if route == "/api/tags":
        return {"models": [{"name": MODEL}]}, 2
    if route == "/api/generate":
        payload = kwargs["payload"]
        assert payload["model"] == MODEL
        assert payload["stream"] is False
        assert payload["format"] == "json"
        assert payload["options"]["num_predict"] == 180
        assert "model" not in payload["prompt"].lower()
        assert "source" not in payload["prompt"].lower()
        assert "quiet" in payload["prompt"]
        assert "motion" in payload["prompt"]
        return {"model": MODEL, "done": True, "response": json.dumps(COPY)}, 5
    raise AssertionError("UNEXPECTED_NETWORK_ROUTE")


class E4E5LocalCopyContractTests(TestCase):
    def test_default_plan_is_offline_and_does_not_read_a_customer_brief(self):
        capture = io.StringIO()
        with (mock.patch.object(models, "send", side_effect=AssertionError("NETWORK")),
              mock.patch.object(gate, "_read_regular", side_effect=AssertionError("READ")),
              contextlib.redirect_stdout(capture)):
            result = trial.cli(["--model", MODEL])
        self.assertEqual(result, 0)
        obj = json.loads(capture.getvalue())
        self.assertEqual(obj["status"], "PLAN_ONLY_NO_INFERENCE")
        self.assertFalse(obj["modelInstalledVerified"])
        self.assertFalse(obj["deploymentAuthorized"])

    def test_dedicated_copy_task_is_not_legacy_scroll_repair(self):
        self.assertEqual(trial.TASK, "website-copy")

    def test_missing_local_optins_or_unknown_model_never_contact_provider(self):
        for flags in ({}, {local.LOCAL_EXPERIMENT_OPT_IN: "yes"},
                      {models.OPT_LOCAL: "yes"}):
            with (self.subTest(flags=flags), mock.patch.dict(os.environ, flags, clear=True),
                  mock.patch.object(models, "send", side_effect=AssertionError("NETWORK"))):
                with self.assertRaisesRegex(ValueError, "DOUBLE_OPT_IN"):
                    trial.LocalCopyTrial().execute(MODEL, BRIEF)
        with (mock.patch.dict(os.environ, enabled(), clear=True),
              mock.patch.object(models, "send", side_effect=AssertionError("NETWORK"))):
            for name in ("artemis", "openrouter/free", "nonexistent:latest"):
                with self.subTest(name=name), self.assertRaises(ValueError):
                    trial.LocalCopyTrial().execute(name, BRIEF)

    def test_real_connector_shape_is_checked_and_review_zip_stays_human_coded(self):
        with (mock.patch.dict(os.environ, enabled(), clear=True),
              mock.patch.object(models, "send", side_effect=fake_api) as calls):
            session = trial.LocalCopyTrial()
            zip_bytes, evidence, receipt = session.execute(MODEL, BRIEF)
            self.assertEqual(calls.call_count, 2)
            self.assertEqual(calls.call_args_list[0].args,
                             ("ollama", "GET", "/api/tags"))
            self.assertEqual(calls.call_args_list[1].args,
                             ("ollama", "POST", "/api/generate"))
            with self.assertRaisesRegex(ValueError, "ONE_ATTEMPT_EXHAUSTED"):
                session.execute(MODEL, BRIEF)
            self.assertEqual(calls.call_count, 2)
        audit = json.loads(evidence)
        info = json.loads(receipt)
        self.assertEqual(audit["decision"], "LEXICAL_CONSTRAINT_MATCH_ONLY_NOT_APPROVAL")
        self.assertEqual(info["reviewZipSha256"], hashlib.sha256(zip_bytes).hexdigest())
        self.assertEqual(info["reviewEvidenceSha256"], hashlib.sha256(evidence).hexdigest())
        self.assertEqual(info["siteCodeProvenance"], "E3.9H_HUMAN_CODED_BROWSER_REFERENCE")
        self.assertIs(info["siteGeneratedByModel"], False)
        self.assertIs(info["providerIdentityCryptographicallyAttested"], False)
        self.assertIs(info["ownerApproved"], False)
        self.assertIs(info["deploymentAuthorized"], False)
        self.assertIs(info["stage45Go"], False)
        self.assertFalse(audit["ownerApproved"])
        self.assertIsNone(audit["modelAuthored"])

    def test_signed_model_name_and_ollama_response_are_not_trusted_as_identity(self):
        for response in (
            {"model": "attacker/model", "done": True, "response": json.dumps(COPY)},
            {"model": MODEL, "done": False, "response": json.dumps(COPY)},
            {"model": MODEL, "done": True, "response": "{}"},
            {"model": MODEL, "done": True, "response": " " * (trial.MAX_PROVIDER_TEXT_BYTES + 1)},
            {"model": MODEL, "done": True, "response": json.dumps({**COPY, "deploy": True})},
            {"model": MODEL, "done": True, "response": json.dumps({
                **COPY, "headlineLines": ["Quiet motions.", "Some cinematic light."]})},
            {"model": MODEL, "done": True, "response": json.dumps({
                **COPY, "headlineLines": ["Quiet certified.", "Purposeful motion."]})},
        ):
            def provider(_provider, _method, route, **_kwargs):
                if route == "/api/tags":
                    return {"models": [{"name": MODEL}]}, 0
                return response, 0
            with (self.subTest(response=response),
                  mock.patch.dict(os.environ, enabled(), clear=True),
                  mock.patch.object(models, "send", side_effect=provider) as call):
                with self.assertRaises(ValueError):
                    trial.LocalCopyTrial().execute(MODEL, BRIEF)
                self.assertEqual(call.call_count, 2)

    def test_missing_model_and_untrusted_inventory_do_not_dispatch_text(self):
        for inventory in ({"models": []},
                          {"models": [{"name": 123}]},
                          {"models": "not-a-list"},
                          {"models": [{"name": MODEL}] * 2}):
            with (self.subTest(inventory=inventory),
                  mock.patch.dict(os.environ, enabled(), clear=True),
                  mock.patch.object(models, "send", return_value=(inventory, 0)) as call):
                with self.assertRaises(ValueError):
                    trial.LocalCopyTrial().execute(MODEL, BRIEF)
                self.assertEqual(call.call_count, 1)

    def test_high_resource_models_require_separate_opt_in(self):
        high = "qwen3-coder:30b"
        with (mock.patch.dict(os.environ, enabled(), clear=True),
              mock.patch.object(models, "send", side_effect=AssertionError("NETWORK"))):
            with self.assertRaisesRegex(ValueError, "HIGH_RESOURCE"):
                trial.LocalCopyTrial().execute(high, BRIEF)

    def test_untrusted_brief_denied_before_inventory_lookup(self):
        with (mock.patch.dict(os.environ, enabled(), clear=True),
              mock.patch.object(models, "send", side_effect=AssertionError("NETWORK"))):
            for bad in ({**BRIEF, "requiredTerms": []},
                        {**BRIEF, "forbiddenTerms": ["quiet"]},
                        {**BRIEF, "brandLabel": "<script>"}):
                with self.subTest(bad=bad), self.assertRaises(ValueError):
                    trial.LocalCopyTrial().execute(MODEL, bad)

    def test_dry_run_cli_process_only_accepts_allowlisted_models(self):
        capture = io.StringIO()
        with contextlib.redirect_stdout(capture):
            status = trial.cli(["--model", "attacker.invalid:latest"])
        self.assertEqual(status, 2)
        self.assertEqual(json.loads(capture.getvalue())["status"],
                         "LOCAL_MODEL_COPY_TRIAL_DENIED_OR_UNAVAILABLE")

    def test_rejected_provider_reply_never_writes_reviews(self):
        with TemporaryDirectory() as directory:
            d = Path(directory)
            brief_file = d / "creative-brief.json"
            brief_file.write_text(json.dumps(BRIEF))
            outputs = [d / n for n in ("local-model-copy-trial-preview.zip",
                                     "local-model-copy-trial-evidence.json",
                                     "local-model-copy-trial-receipt.json")]
            with (mock.patch.dict(os.environ, enabled(), clear=True),
                  mock.patch.object(trial, "FOLDER", d),
                  mock.patch.object(trial.creative, "BRIEF", brief_file),
                  mock.patch.object(trial, "ZIP", outputs[0]),
                  mock.patch.object(trial, "EVIDENCE", outputs[1]),
                  mock.patch.object(trial, "RECEIPT", outputs[2]),
                  mock.patch.object(models, "send", side_effect=ValueError("SENSITIVE_DATA")),
                  contextlib.redirect_stdout(io.StringIO()) as out):
                self.assertEqual(trial.cli(["--model", MODEL, "--run-local"]), 2)
                self.assertNotIn("SENSITIVE_DATA", out.getvalue())
                self.assertTrue(all(not x.exists() for x in outputs))

    def test_successful_model_adapter_cli_writes_only_local_research_outputs(self):
        with TemporaryDirectory() as directory:
            d = Path(directory)
            b = d / "creative-brief.json"
            b.write_text(json.dumps(BRIEF))
            paths = [d / n for n in ("local-model-copy-trial-preview.zip",
                                    "local-model-copy-trial-evidence.json",
                                    "local-model-copy-trial-receipt.json")]
            with (mock.patch.dict(os.environ, enabled(), clear=True),
                  mock.patch.object(trial, "FOLDER", d),
                  mock.patch.object(trial.creative, "BRIEF", b),
                  mock.patch.object(trial, "ZIP", paths[0]),
                  mock.patch.object(trial, "EVIDENCE", paths[1]),
                  mock.patch.object(trial, "RECEIPT", paths[2]),
                  mock.patch.object(models, "send", side_effect=fake_api),
                  contextlib.redirect_stdout(io.StringIO()) as out):
                self.assertEqual(trial.cli(["--model", MODEL, "--run-local"]), 0)
                self.assertEqual(json.loads(out.getvalue())["status"],
                                 "LOCAL_MODEL_COPY_UNTRUSTED_OFFLINE_REVIEW")
                self.assertTrue(all(x.is_file() for x in paths))
                self.assertIs(json.loads(paths[2].read_text())["stage45Go"], False)


if __name__ == "__main__":
    main()
