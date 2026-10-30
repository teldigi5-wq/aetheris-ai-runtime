"""E4→E5 dedicated layout task and bounded local connector, provider is MOCKED."""
from __future__ import annotations

import contextlib
import hashlib
import io
import json
import os
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock

import e36_free_model_router as models
import e4_free_route_admission as routes
import e4_local_proposal_session as sessions
import e5_local_layout_trial as trial
import e5_structured_layout_preview as layouts

MODEL = "qwen2.5-coder:1.5b"
BRIEF = {"version": 1, "brandLabel": "Prototype Atelier",
         "requiredTerms": ["quiet", "motion"],
         "forbiddenTerms": ["certified"]}
COPY = {"version": 1, "brandLabel": "Prototype Atelier",
        "headlineLines": ["Quiet materials.", "Purposeful motion."]}
LAYOUT = {"version": 1, "composition": "gallery",
          "palette": "paper", "typography": "editorial-serif"}


def flags():
    return {models.OPT_LOCAL: "yes",
            sessions.LOCAL_EXPERIMENT_OPT_IN: "yes",
            trial.OPT_LAYOUT: "yes"}


def provider(provider_name, method, route, **kwargs):
    assert provider_name == "ollama"
    if method == "GET" and route == "/api/tags":
        return {"models": [{"name": MODEL}]}, 2
    assert method == "POST" and route == "/api/generate"
    payload = kwargs["payload"]
    assert payload["model"] == MODEL
    assert payload["stream"] is False
    assert payload["format"] == "json"
    assert payload["options"]["num_predict"] == 100
    prompt = json.loads(payload["prompt"])
    assert set(prompt) == {"schemaVersion", "composition", "palette",
                           "typography", "selectionPolicy"}
    assert "Prototype Atelier" not in payload["prompt"]
    assert "Quiet materials" not in payload["prompt"]
    assert "scene.js" not in payload["prompt"]
    return {"model": MODEL, "done": True, "response": json.dumps(LAYOUT)}, 4


class LocalLayoutContract(unittest.TestCase):
    def test_plan_only_never_reads_brief_or_calls_provider(self):
        out = io.StringIO()
        with (mock.patch.object(trial.briefs, "_read_regular",
                                side_effect=AssertionError("READ")),
              mock.patch.object(models, "send",
                                side_effect=AssertionError("NETWORK")),
              contextlib.redirect_stdout(out)):
            self.assertEqual(trial.cli(["--model", MODEL]), 0)
        data = json.loads(out.getvalue())
        self.assertEqual(data["status"], "PLAN_ONLY_NO_INFERENCE_OR_BRIEF_ACCESS")
        self.assertEqual(data["modelCalls"], 0)
        self.assertFalse(data["stage45Go"])

    def test_distinct_website_layout_advisory_denies_remote_cloud_and_repair_reuse(self):
        local = routes.advisory({
            "task": "website-layout", "provider": "ollama", "modelId": MODEL,
            "externalInferenceConsent": False, "sourceDisclosureConsent": False,
            "highResourceConsent": False, "maxCostUsd": "0"
        }, {"installedLocalModelIds": [MODEL], "cloudModelDetail": None,
            "catalogFetchedUnixSeconds": None}, 2000)
        self.assertEqual(local["decision"],
                         "CANDIDATE_FOR_SEPARATE_RUNTIME_VERIFICATION")
        remote = routes.advisory({
            "task": "website-layout", "provider": "openrouter",
            "modelId": sorted(models.CLOUD_CANDIDATES)[0],
            "externalInferenceConsent": True, "sourceDisclosureConsent": True,
            "highResourceConsent": False, "maxCostUsd": "0"
        }, {"installedLocalModelIds": [], "cloudModelDetail": None,
            "catalogFetchedUnixSeconds": None}, 2000)
        self.assertEqual(remote["reason"], "WEBSITE_LAYOUT_LOCAL_ONLY")
        self.assertEqual(remote["decision"], "DENIED_NO_FALLBACK")
        with (mock.patch.dict(os.environ, flags(), clear=True),
              mock.patch.object(models, "send",
                                side_effect=AssertionError("NETWORK"))):
            with self.assertRaisesRegex(ValueError, "UNAPPROVED_TASK"):
                sessions.LocalProposalSession().propose(MODEL, "website-layout")

    def test_missing_optins_refuse_before_any_network_request(self):
        for env in ({}, {models.OPT_LOCAL: "yes"},
                    {models.OPT_LOCAL: "yes",
                     sessions.LOCAL_EXPERIMENT_OPT_IN: "yes"}):
            with (self.subTest(env=env),
                  mock.patch.dict(os.environ, env, clear=True),
                  mock.patch.object(models, "send",
                                    side_effect=AssertionError("NETWORK"))):
                with self.assertRaisesRegex(ValueError, "THREE_OPT_INS"):
                    trial.LocalLayoutTrial().execute(MODEL, BRIEF, COPY)

    def test_successful_mock_local_selection_compiles_provenance_safe_real_layout(self):
        with (mock.patch.dict(os.environ, flags(), clear=True),
              mock.patch.object(models, "send", side_effect=provider) as client):
            instance = trial.LocalLayoutTrial()
            bundle, receipt = instance.execute(MODEL, BRIEF, COPY)
            self.assertEqual(client.call_count, 2)
            with self.assertRaisesRegex(ValueError, "ONE_ATTEMPT_EXHAUSTED"):
                instance.execute(MODEL, BRIEF, COPY)
            self.assertEqual(client.call_count, 2)
        self.assertEqual(receipt["task"], "website-layout")
        self.assertEqual(receipt["layoutDecision"], LAYOUT)
        self.assertFalse(receipt["briefAndCopySentToModel"])
        self.assertFalse(receipt["sourceCodeSentToModel"])
        self.assertFalse(receipt["modelAuthoredCssOrJavascript"])
        self.assertFalse(receipt["ownerApproved"])
        self.assertFalse(receipt["deploymentAuthorized"])
        self.assertFalse(receipt["stage45Go"])
        with zipfile.ZipFile(io.BytesIO(bundle)) as outer:
            self.assertEqual(set(outer.namelist()), {"receipt.json", "preview.zip"})
            self.assertIsNone(outer.testzip())
            data = outer.read("preview.zip")
            self.assertEqual(receipt["previewSha256"],
                             hashlib.sha256(data).hexdigest())
            with zipfile.ZipFile(io.BytesIO(data)) as site:
                self.assertIsNone(site.testzip())
                markup = site.read("index.html")
                self.assertIn(b'data-e5-composition="gallery"', markup)
                self.assertIn(b'data-e5-palette="paper"', markup)
                self.assertIn(b"UNAPPROVED DRAFT", markup)
                self.assertEqual(site.read("e5-layout.css"),
                                 layouts.TRUSTED_CSS.encode())
                self.assertEqual(site.read("scene.js"),
                                 layouts.reference.validate_source(
                                     layouts.reference.REFERENCE)["scene.js"])

    def test_malformed_provider_envelopes_and_tokens_are_untrusted(self):
        variants = [
            {"model": MODEL, "done": True,
             "response": '{"version":1,"composition":"gallery","palette":"paper","typography":"editorial-serif","deploy":true}'},
            {"model": MODEL, "done": True,
             "response": '{"version":1,"composition":"gallery","palette":"paper","typography":"editorial-serif","palette":"charcoal"}'},
            {"model": MODEL, "done": True,
             "response": '{"version":1,"composition":"gallery","palette":"paper","typography":"url(https://evil.test)"}'},
            {"model": "spoofed:latest", "done": True,
             "response": json.dumps(LAYOUT)},
            {"model": MODEL, "done": False, "response": json.dumps(LAYOUT)},
            {"model": MODEL, "done": True,
             "response": " " * (layouts.MAX_LAYOUT_BYTES + 1)},
            ["unexpected"],
        ]
        for answer in variants:
            def faulty(_name, _method, route, **_kwargs):
                if route == "/api/tags":
                    return {"models": [{"name": MODEL}]}, 2
                return answer, 0
            with (self.subTest(answer=answer),
                  mock.patch.dict(os.environ, flags(), clear=True),
                  mock.patch.object(models, "send", side_effect=faulty) as transport):
                with self.assertRaises(ValueError):
                    trial.LocalLayoutTrial().execute(MODEL, BRIEF, COPY)
                self.assertEqual(transport.call_count, 2)

    def test_bad_brief_is_refused_before_network_and_bad_inventory_before_generation(self):
        with (mock.patch.dict(os.environ, flags(), clear=True),
              mock.patch.object(models, "send",
                                side_effect=AssertionError("NETWORK"))):
            with self.assertRaises(ValueError):
                trial.LocalLayoutTrial().execute(
                    MODEL, {**BRIEF, "requiredTerms": []}, COPY)
        for inventory in ({"models": "fake"}, {"models": []},
                          {"models": [{"name": 3}]}, ["invalid"]):
            with (self.subTest(inventory=inventory),
                  mock.patch.dict(os.environ, flags(), clear=True),
                  mock.patch.object(models, "send",
                                    return_value=(inventory, 0)) as client):
                with self.assertRaises(ValueError):
                    trial.LocalLayoutTrial().execute(MODEL, BRIEF, COPY)
                self.assertEqual(client.call_count, 1)

    def test_high_resource_model_requires_specific_optin(self):
        with (mock.patch.dict(os.environ, flags(), clear=True),
              mock.patch.object(models, "send",
                                side_effect=AssertionError("NETWORK"))):
            with self.assertRaisesRegex(ValueError, "HIGH_RESOURCE"):
                trial.LocalLayoutTrial().execute("qwen3-coder:30b", BRIEF, COPY)

    def test_private_cli_refuses_preexisting_evidence_and_preserves_it(self):
        with tempfile.TemporaryDirectory() as root:
            directory = Path(root)
            source_b = directory / "creative-brief.json"
            source_c = directory / "copy-proposal.json"
            output = directory / "local-model-layout-review.zip"
            source_b.write_text(json.dumps(BRIEF))
            source_c.write_text(json.dumps(COPY))
            output.write_bytes(b"existing-review-must-survive")
            with (mock.patch.dict(os.environ, flags(), clear=True),
                  mock.patch.object(trial, "FOLDER", directory),
                  mock.patch.object(trial, "BRIEF", source_b),
                  mock.patch.object(trial, "COPY", source_c),
                  mock.patch.object(trial, "OUTPUT", output),
                  mock.patch.object(models, "send",
                                    side_effect=AssertionError("NETWORK")),
                  contextlib.redirect_stdout(io.StringIO()) as captured):
                self.assertEqual(trial.cli(["--model", MODEL, "--run-local"]), 2)
                self.assertEqual(output.read_bytes(), b"existing-review-must-survive")
                self.assertIn("LOCAL_LAYOUT_REFUSED", captured.getvalue())

    def test_private_cli_writes_single_0600_review_bundle_under_mocked_transport(self):
        with tempfile.TemporaryDirectory() as root:
            directory = Path(root)
            source_b = directory / "creative-brief.json"
            source_c = directory / "copy-proposal.json"
            output = directory / "local-model-layout-review.zip"
            source_b.write_text(json.dumps(BRIEF))
            source_c.write_text(json.dumps(COPY))
            with (mock.patch.dict(os.environ, flags(), clear=True),
                  mock.patch.object(trial, "FOLDER", directory),
                  mock.patch.object(trial, "BRIEF", source_b),
                  mock.patch.object(trial, "COPY", source_c),
                  mock.patch.object(trial, "OUTPUT", output),
                  mock.patch.object(models, "send", side_effect=provider),
                  contextlib.redirect_stdout(io.StringIO()) as captured):
                self.assertEqual(trial.cli(["--model", MODEL, "--run-local"]), 0)
            self.assertTrue(output.is_file())
            self.assertEqual(output.stat().st_mode & 0o777, 0o600)
            self.assertEqual(json.loads(captured.getvalue())["status"],
                             "LOCAL_LAYOUT_TOKEN_REVIEW_ONLY")
            self.assertEqual(json.loads(captured.getvalue())["bundleSha256"],
                             hashlib.sha256(output.read_bytes()).hexdigest())


if __name__ == "__main__":
    unittest.main()
