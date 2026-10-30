"""E5 fixed six-case benchmark; all inference is MOCKED, never real in CI."""
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
import e4_local_proposal_session as sessions
import e5_layout_design_intent as preference
import e5_local_layout_trial as trial
import e5_six_case_intent_benchmark as bench

MODEL = "qwen2.5-coder:1.5b"


def permitted():
    return {models.OPT_LOCAL: "yes",
            sessions.LOCAL_EXPERIMENT_OPT_IN: "yes",
            trial.OPT_LAYOUT: "yes",
            trial.OPT_INTENT: "yes",
            bench.OPT_BENCHMARK: "yes"}


def expected(intent):
    return {"version": 1,
            "composition": preference.PREFERENCES["layoutGoal"][intent["layoutGoal"]],
            "palette": preference.PREFERENCES["contrastPreference"][intent["contrastPreference"]],
            "typography": preference.PREFERENCES["typographicVoice"][intent["typographicVoice"]]}


def simulated_provider(*, failures=False):
    def answer(provider, method, route, **kwargs):
        assert provider == "ollama"
        if method == "GET" and route == "/api/tags":
            return {"models": [{"name": MODEL}]}, 1
        assert method == "POST" and route == "/api/generate"
        parameters = kwargs["payload"]
        assert parameters["stream"] is False
        assert parameters["format"] == "json"
        prompt = json.loads(parameters["prompt"])
        assert set(prompt) == {"schemaVersion", "composition", "palette",
                               "typography", "selectionPolicy", "abstractDesignIntent"}
        assert "Prototype Atelier" not in parameters["prompt"]
        assert "Quiet materials" not in parameters["prompt"]
        assert "scene.js" not in parameters["prompt"]
        intent = prompt["abstractDesignIntent"]
        decision = expected(intent)
        if failures and intent["layoutGoal"] == "informational" and intent["contrastPreference"] == "dark":
            decision = {"version": 1, "composition": "gallery",
                        "palette": "paper", "typography": "editorial-serif"}
        if failures and intent["layoutGoal"] == "balanced" and intent["contrastPreference"] == "light":
            return {"model": MODEL, "done": True,
                    "response": "<script>PRIVATE_PROVIDER_TEXT</script>"}, 1
        return {"model": MODEL, "done": True, "response": json.dumps(decision)}, 1
    return answer


class FixedIntentBenchmarkTest(unittest.TestCase):
    def test_default_plan_is_no_read_no_network_and_no_model_attempts(self):
        output = io.StringIO()
        with (mock.patch.object(bench.brief_gate, "build",
                                side_effect=AssertionError("SOURCE READ")),
              mock.patch.object(models, "send",
                                side_effect=AssertionError("NETWORK")),
              contextlib.redirect_stdout(output)):
            self.assertEqual(bench.main(["--model", MODEL]), 0)
        plan = json.loads(output.getvalue())
        self.assertEqual(plan["status"], "PLAN_ONLY_ZERO_INFERENCE")
        self.assertEqual(plan["publicSyntheticCases"], 6)
        self.assertEqual(plan["actualAttempts"], 0)
        self.assertIs(plan["stage45Go"], False)

    def test_public_six_intents_are_unique_and_static_baseline_hits_all_eighteen(self):
        fixed = bench.cases()
        self.assertEqual(len(fixed), 6)
        self.assertEqual(len({n for n, _ in fixed}), 6)
        self.assertEqual(len({preference.sha256(i) for _, i in fixed}), 6)
        self.assertEqual(set(i["layoutGoal"] for _, i in fixed),
                         {"immersive", "informational", "balanced"})
        self.assertEqual(set(i["contrastPreference"] for _, i in fixed),
                         {"light", "dark"})
        self.assertEqual(set(i["typographicVoice"] for _, i in fixed),
                         {"editorial", "contemporary"})
        self.assertEqual(sum(preference.score(i, expected(i))["matched"]
                             for _, i in fixed), 18)

    def test_five_optins_must_all_exist_before_any_provider_interaction(self):
        for missing in permitted():
            env = permitted()
            env.pop(missing)
            with (self.subTest(missing=missing),
                  mock.patch.dict(os.environ, env, clear=True),
                  mock.patch.object(models, "send",
                                    side_effect=AssertionError("NETWORK"))):
                with self.assertRaisesRegex(ValueError, "FIVE_EXPLICIT_OPT_INS"):
                    bench.evaluate(MODEL)
        with (mock.patch.dict(os.environ, permitted(), clear=True),
              mock.patch.object(models, "send",
                                side_effect=AssertionError("NETWORK"))):
            for bad in ("openrouter/free", "unknown-local:latest", "../model"):
                with self.subTest(bad=bad), self.assertRaises(ValueError):
                    bench.evaluate(bad)

    def test_high_resource_requires_additional_owner_optin(self):
        with (mock.patch.dict(os.environ, permitted(), clear=True),
              mock.patch.object(models, "send",
                                side_effect=AssertionError("NETWORK"))):
            with self.assertRaisesRegex(ValueError, "HEAVY_MODEL"):
                bench.evaluate("qwen3-coder:30b")

    def test_all_six_mock_contracts_produce_distinct_reproducible_previews(self):
        with (mock.patch.dict(os.environ, permitted(), clear=True),
              mock.patch.object(models, "send",
                                side_effect=simulated_provider()) as sender):
            archived, report = bench.evaluate(MODEL)
        self.assertEqual(sender.call_count, 12)
        self.assertEqual(report["submitted"], 6)
        self.assertEqual(report["validResponses"], 6)
        self.assertEqual(report["invalidOrUnavailable"], 0)
        self.assertEqual(report["alignmentPointsIncludingFailuresAsZero"], 18)
        self.assertEqual(report["totalPossibleAlignmentPoints"], 18)
        self.assertEqual(report["alignmentFraction"], "18/18")
        self.assertEqual(report["humanAuthoredStaticBaselinePoints"], 18)
        self.assertIs(report["benchmarkCasesHeldOut"], False)
        self.assertIs(report["creativeQualityMeasured"], False)
        self.assertIs(report["siteCodeModelAuthored"], False)
        self.assertIs(report["ownerHardwarePerformanceCertified"], False)
        self.assertIs(report["modelAuthorshipCryptographicallyAttested"], False)
        self.assertIs(report["stage45Go"], False)
        with zipfile.ZipFile(io.BytesIO(archived)) as z:
            self.assertIsNone(z.testzip())
            self.assertEqual(len(z.namelist()), 13)
            self.assertEqual(json.loads(z.read("evaluation.json")), report)
            for row in report["results"]:
                name = row["caseId"]
                self.assertEqual(row["alignmentPoints"], 3)
                self.assertEqual(hashlib.sha256(z.read("previews/" + name + ".zip")).hexdigest(),
                                 row["previewSha256"])
                self.assertEqual(hashlib.sha256(z.read("receipts/" + name + ".json")).hexdigest(),
                                 row["receiptSha256"])
        with (mock.patch.dict(os.environ, permitted(), clear=True),
              mock.patch.object(models, "send", side_effect=simulated_provider())):
            again, same = bench.evaluate(MODEL)
        self.assertEqual(archived, again)
        self.assertEqual(report, same)

    def test_wrong_alignment_and_invalid_provider_are_both_counted(self):
        with (mock.patch.dict(os.environ, permitted(), clear=True),
              mock.patch.object(models, "send",
                                side_effect=simulated_provider(failures=True)) as sender):
            archive, report = bench.evaluate(MODEL)
        self.assertEqual(sender.call_count, 12)
        self.assertEqual(report["submitted"], 6)
        self.assertEqual(report["validResponses"], 5)
        self.assertEqual(report["invalidOrUnavailable"], 1)
        self.assertEqual(report["alignmentFraction"], "12/18")
        self.assertEqual(report["alignmentPointsIncludingFailuresAsZero"], 12)
        self.assertEqual(report["results"][1]["alignmentPoints"], 0)
        self.assertTrue(report["results"][1]["validLayoutReturned"])
        self.assertFalse(report["results"][-1]["validLayoutReturned"])
        self.assertEqual(report["results"][-1]["status"], "REJECTED_OR_PROVIDER_UNAVAILABLE")
        self.assertNotIn(b"PRIVATE_PROVIDER_TEXT", archive)
        with zipfile.ZipFile(io.BytesIO(archive)) as z:
            self.assertIsNone(z.testzip())
            self.assertEqual(len(z.namelist()), 11)
            self.assertNotIn("previews/case-06.zip", z.namelist())
            self.assertNotIn("receipts/case-06.json", z.namelist())

    def test_provider_unavailable_means_zero_over_total_and_no_leaked_text(self):
        with (mock.patch.dict(os.environ, permitted(), clear=True),
              mock.patch.object(models, "send",
                                side_effect=ValueError("PRIVATE_MODEL_ERROR")) as sender):
            archive, report = bench.evaluate(MODEL)
        self.assertEqual(sender.call_count, 6)  # one inventory attempt per case, no retry
        self.assertEqual(report["validResponses"], 0)
        self.assertEqual(report["invalidOrUnavailable"], 6)
        self.assertEqual(report["alignmentFraction"], "0/18")
        self.assertNotIn(b"PRIVATE_MODEL_ERROR", archive)
        with zipfile.ZipFile(io.BytesIO(archive)) as z:
            self.assertEqual(z.namelist(), ["evaluation.json"])

    def test_preexisting_zip_refused_before_any_real_model_calls(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            existing = folder / "six-case-intent-benchmark.zip"
            existing.write_bytes(b"KEEP_PRIOR_DATA")
            with (mock.patch.dict(os.environ, permitted(), clear=True),
                  mock.patch.object(bench, "FOLDER", folder),
                  mock.patch.object(bench, "OUTPUT", existing),
                  mock.patch.object(models, "send",
                                    side_effect=AssertionError("NETWORK")),
                  contextlib.redirect_stdout(io.StringIO()) as output):
                self.assertEqual(bench.main(["--model", MODEL, "--run-local"]), 2)
            self.assertEqual(existing.read_bytes(), b"KEEP_PRIOR_DATA")
            self.assertIn("LOCAL_BENCHMARK_REFUSED", output.getvalue())

    def test_opted_in_mock_cli_persists_exactly_one_private_0600_evidence_zip(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            out = folder / "six-case-intent-benchmark.zip"
            with (mock.patch.dict(os.environ, permitted(), clear=True),
                  mock.patch.object(bench, "FOLDER", folder),
                  mock.patch.object(bench, "OUTPUT", out),
                  mock.patch.object(models, "send",
                                    side_effect=simulated_provider(failures=True)) as sender,
                  contextlib.redirect_stdout(io.StringIO()) as stdout):
                self.assertEqual(bench.main(["--model", MODEL, "--run-local"]), 0)
            self.assertEqual(sender.call_count, 12)
            self.assertTrue(out.is_file())
            self.assertEqual(out.stat().st_mode & 0o777, 0o600)
            msg = json.loads(stdout.getvalue())
            self.assertEqual(msg["alignmentFraction"], "12/18")
            self.assertEqual(msg["sha256"], hashlib.sha256(out.read_bytes()).hexdigest())
            self.assertIs(msg["creativeQualityMeasured"], False)
            self.assertIs(msg["stage45Go"], False)


if __name__ == "__main__":
    unittest.main()
