"""E4/E5 multi-brief suite: all provider interactions MOCKED in CI."""
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
import e5_local_brief_evaluation as evaluate
import e5_local_copy_trial as trial

MODEL = "qwen2.5-coder:1.5b"
BRIEF_A = {"version": 1, "brandLabel": "Prototype Atelier",
           "requiredTerms": ["quiet", "motion"], "forbiddenTerms": ["certified"]}
BRIEF_B = {"version": 1, "brandLabel": "Stone Workshop",
           "requiredTerms": ["stone", "craft"], "forbiddenTerms": ["guaranteed"]}
SUITE = {"version": 1, "cases": [
    {"caseId": "case-one", "brief": BRIEF_A},
    {"caseId": "case-two", "brief": BRIEF_B},
]}


def optins():
    return {models.OPT_LOCAL: "yes",
            sessions.LOCAL_EXPERIMENT_OPT_IN: "yes",
            evaluate.OPT_EVALUATE: "yes"}


def fake_model(provider, method, route, **kwargs):
    assert provider == "ollama"
    if method == "GET" and route == "/api/tags":
        return {"models": [{"name": MODEL}]}, 1
    assert method == "POST" and route == "/api/generate"
    prompt = json.loads(kwargs["payload"]["prompt"])
    brand = prompt["brandLabel"]
    if brand == "Prototype Atelier":
        copy = {"version": 1, "brandLabel": brand,
                "headlineLines": ["Quiet materials.", "Purposeful motion."]}
    else:
        copy = {"version": 1, "brandLabel": brand,
                "headlineLines": ["Stone forms.", "Quiet craft."]}
    return {"model": MODEL, "done": True, "response": json.dumps(copy)}, 1


class MultiBriefEvaluationTests(unittest.TestCase):
    def test_default_never_reads_suite_or_contacts_ollama(self):
        output = io.StringIO()
        with (mock.patch.object(evaluate.brief_gate, "_read_regular",
                                side_effect=AssertionError("READ")),
              mock.patch.object(models, "send",
                                side_effect=AssertionError("NETWORK")),
              contextlib.redirect_stdout(output)):
            self.assertEqual(evaluate.cli(["--model", MODEL]), 0)
        doc = json.loads(output.getvalue())
        self.assertEqual(doc["status"], "PLAN_ONLY_NO_LOCAL_MODEL_CONTACT")
        self.assertEqual(doc["actualAttempts"], 0)
        self.assertIs(doc["suiteRead"], False)
        self.assertIs(doc["stage45Go"], False)

    def test_suite_is_prevalidated_before_any_provider_call(self):
        invalid = [
            {**SUITE, "version": True},
            {**SUITE, "cases": []},
            {**SUITE, "cases": SUITE["cases"] * 3},
            {**SUITE, "cases": [SUITE["cases"][0]] * 2},
            {"version": 1, "cases": [
                {"caseId": "../unsafe", "brief": BRIEF_A},
                {"caseId": "case-two", "brief": BRIEF_B}]},
            {"version": 1, "cases": [
                {"caseId": "case-one", "brief": {**BRIEF_A, "requiredTerms": []}},
                {"caseId": "case-two", "brief": BRIEF_B}]},
            {**SUITE, "dispatch": True},
        ]
        with (mock.patch.dict(os.environ, optins(), clear=True),
              mock.patch.object(models, "send",
                                side_effect=AssertionError("NETWORK"))):
            for case in invalid:
                with self.subTest(case=case), self.assertRaises(ValueError):
                    evaluate.evaluate(case, MODEL)
        duplicated = b'{"version":1,"version":1,"cases":[]}'
        with self.assertRaises(ValueError):
            evaluate.read_suite(duplicated)

    def test_requires_separate_third_evaluation_optin_and_no_cloud_fallback(self):
        for env in ({}, {models.OPT_LOCAL: "yes"},
                    {models.OPT_LOCAL: "yes",
                     sessions.LOCAL_EXPERIMENT_OPT_IN: "yes"}):
            with (self.subTest(env=env),
                  mock.patch.dict(os.environ, env, clear=True),
                  mock.patch.object(models, "send",
                                    side_effect=AssertionError("NETWORK"))):
                with self.assertRaisesRegex(ValueError, "TRIPLE_OPT_IN"):
                    evaluate.evaluate(SUITE, MODEL)
        with mock.patch.dict(os.environ, optins(), clear=True):
            for candidate in ("openrouter/free", "artemis", "../unsafe"):
                with self.subTest(candidate=candidate), self.assertRaises(ValueError):
                    evaluate.evaluate(SUITE, candidate)

    def test_two_distinct_local_responses_yield_two_verified_lexical_acceptances(self):
        with (mock.patch.dict(os.environ, optins(), clear=True),
              mock.patch.object(models, "send", side_effect=fake_model) as provider):
            archive, report = evaluate.evaluate(SUITE, MODEL)
        self.assertEqual(provider.call_count, 4)  # GET tags and POST generate for each.
        self.assertEqual(report["experimentalAdapterInvocations"], 2)
        self.assertEqual(report["acceptedLexically"], 2)
        self.assertEqual(report["rejectedOrUnavailable"], 0)
        self.assertEqual(report["acceptanceFraction"], "2/2")
        self.assertFalse(report["siteGeneratedByModel"])
        self.assertFalse(report["hardwareBenchmarkCertified"])
        self.assertFalse(report["providerIdentityCryptographicallyAttested"])
        self.assertFalse(report["visualDesignQualityAssessed"])
        self.assertFalse(report["ownerApproved"])
        self.assertFalse(report["stage45Go"])
        with zipfile.ZipFile(io.BytesIO(archive)) as z:
            self.assertIsNone(z.testzip())
            self.assertEqual(len(z.namelist()), 7)
            self.assertEqual(json.loads(z.read("evaluation.json")), report)
            for row in report["results"]:
                case = row["caseId"]
                raw_zip = z.read("previews/" + case + ".zip")
                receipt = z.read("receipts/" + case + ".json")
                self.assertEqual(hashlib.sha256(raw_zip).hexdigest(),
                                 row["previewSha256"])
                self.assertEqual(hashlib.sha256(receipt).hexdigest(),
                                 row["receiptSha256"])
                self.assertIsNone(zipfile.ZipFile(io.BytesIO(raw_zip)).testzip())
        # Frozen synthetic calls should yield byte-identical data packages.
        with (mock.patch.dict(os.environ, optins(), clear=True),
              mock.patch.object(models, "send", side_effect=fake_model)):
            another, same_report = evaluate.evaluate(SUITE, MODEL)
        self.assertEqual(archive, another)
        self.assertEqual(report, same_report)

    def test_rejected_second_brief_keeps_failure_denominator_and_no_leaked_model_text(self):
        def mixed(provider, method, route, **kwargs):
            payload, timing = fake_model(provider, method, route, **kwargs)
            if route == "/api/generate":
                if json.loads(kwargs["payload"]["prompt"])["brandLabel"] == "Stone Workshop":
                    payload["response"] = '<script>SECRET_NEVER_LOGGED</script>'
            return payload, timing
        with (mock.patch.dict(os.environ, optins(), clear=True),
              mock.patch.object(models, "send", side_effect=mixed) as provider):
            archive, report = evaluate.evaluate(SUITE, MODEL)
        self.assertEqual(provider.call_count, 4)
        self.assertEqual(report["experimentalAdapterInvocations"], 2)
        self.assertEqual(report["acceptedLexically"], 1)
        self.assertEqual(report["rejectedOrUnavailable"], 1)
        self.assertEqual(report["acceptanceFraction"], "1/2")
        self.assertNotIn(b"SECRET_NEVER_LOGGED", archive)
        self.assertIsNone(report["results"][1]["previewSha256"])
        with zipfile.ZipFile(io.BytesIO(archive)) as z:
            self.assertEqual(len(z.namelist()), 4)
            self.assertNotIn("previews/case-two.zip", z.namelist())

    def test_provider_failures_are_counted_and_do_not_abort_rest_of_suite(self):
        with (mock.patch.dict(os.environ, optins(), clear=True),
              mock.patch.object(models, "send",
                                side_effect=ValueError("UNTRUSTED_SECRET_TOKEN")) as provider):
            archive, report = evaluate.evaluate(SUITE, MODEL)
        self.assertEqual(provider.call_count, 2)
        self.assertEqual(report["acceptedLexically"], 0)
        self.assertEqual(report["rejectedOrUnavailable"], 2)
        self.assertEqual(report["acceptanceFraction"], "0/2")
        self.assertNotIn(b"UNTRUSTED_SECRET_TOKEN", archive)
        with zipfile.ZipFile(io.BytesIO(archive)) as z:
            self.assertEqual(z.namelist(), ["evaluation.json"])

    def test_cli_refuses_preexisting_evidence_and_never_overwrites_a_prior_run(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            suite = folder / "local-evaluation-suite.json"
            bundle = folder / "local-evaluation-evidence.zip"
            suite.write_text(json.dumps(SUITE))
            bundle.write_bytes(b"frozen-original-evidence")
            with (mock.patch.dict(os.environ, optins(), clear=True),
                  mock.patch.object(evaluate, "FOLDER", folder),
                  mock.patch.object(evaluate, "SUITE", suite),
                  mock.patch.object(evaluate, "BUNDLE", bundle),
                  mock.patch.object(models, "send",
                                    side_effect=AssertionError("NETWORK")),
                  contextlib.redirect_stdout(io.StringIO()) as output):
                self.assertEqual(evaluate.cli(["--model", MODEL, "--run-local"]), 2)
            self.assertEqual(bundle.read_bytes(), b"frozen-original-evidence")
            self.assertIn("LOCAL_EVALUATION_REFUSED", output.getvalue())

    def test_cli_real_adaptor_mock_writes_private_zip_without_applying_changes(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            suite = folder / "local-evaluation-suite.json"
            bundle = folder / "local-evaluation-evidence.zip"
            suite.write_text(json.dumps(SUITE))
            with (mock.patch.dict(os.environ, optins(), clear=True),
                  mock.patch.object(evaluate, "FOLDER", folder),
                  mock.patch.object(evaluate, "SUITE", suite),
                  mock.patch.object(evaluate, "BUNDLE", bundle),
                  mock.patch.object(models, "send", side_effect=fake_model) as provider,
                  contextlib.redirect_stdout(io.StringIO()) as output):
                self.assertEqual(evaluate.cli(["--model", MODEL, "--run-local"]), 0)
            self.assertEqual(provider.call_count, 4)
            doc = json.loads(output.getvalue())
            self.assertEqual(doc["acceptedLexically"], 2)
            self.assertTrue(bundle.is_file())
            self.assertEqual(doc["evidenceZipSha256"],
                             hashlib.sha256(bundle.read_bytes()).hexdigest())
            self.assertFalse(doc["ownerApproved"])
            self.assertFalse(doc["providerIdentityCryptographicallyAttested"])
            self.assertEqual(os.stat(bundle).st_mode & 0o777, 0o600)

    def test_high_resource_requires_existing_optin_before_any_provider_request(self):
        with (mock.patch.dict(os.environ, optins(), clear=True),
              mock.patch.object(models, "send", side_effect=AssertionError("NETWORK"))):
            with self.assertRaisesRegex(ValueError, "HIGH_RESOURCE"):
                evaluate.evaluate(SUITE, "qwen3-coder:30b")


if __name__ == "__main__":
    unittest.main()
