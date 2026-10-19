"""E3.4B unit security tests. All mocks explicitly not real inference evidence."""
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest import mock

import e34b_real_inference as target


class RealTrialContractTests(unittest.TestCase):
    def test_explicit_real_trial_flag_and_env_required(self):
        with mock.patch.dict(os.environ, {target.OPT_IN: "no"}):
            with self.assertRaisesRegex(ValueError, "E34B_REAL_INFERENCE_EXPLICIT_OPT_IN_REQUIRED"):
                target.trial()
        self.assertEqual(target.MODEL, "smollm2:135m")

    def test_tag_digest_and_size_are_checked(self):
        valid = {"models": [{"name": "smollm2:135m", "size": 270000000,
                              "digest": "a" * 64}]}
        with mock.patch.object(target, "local_json", return_value=(valid, 12)):
            m = target.local_model_metadata()
        self.assertEqual(m["sizeBytes"], 270000000)
        self.assertFalse(m["identityCryptographicallyAttested"])
        for broken in [
            {"models": []},
            {"models": [{"name": "smollm2:135m", "size": 270000000, "digest": "fake"}]},
            {"models": [{"name": "smollm2:135m", "size": 800000000, "digest": "a" * 64}]},
        ]:
            with self.subTest(broken=broken), mock.patch.object(
                    target, "local_json", return_value=(broken, 12)):
                with self.assertRaises(ValueError):
                    target.local_model_metadata()

    def test_unsupported_network_endpoint_rejected_without_connection(self):
        with self.assertRaisesRegex(ValueError, "E34B_UNEXPECTED_LOCAL_ENDPOINT"):
            target.local_json("GET", "https://evil.invalid/private")

    def test_mocked_model_text_cannot_be_reported_as_verified_repair(self):
        with tempfile.TemporaryDirectory() as home:
            root = Path(home)
            evidence = root / "e34b"
            source = b"function progress() {return 1;}\nconst p = 0.24;\n"
            diagnostics = b'{"caseId":"scroll","failureCode":"E32_SCROLL_PROGRESS_STUCK"}'
            expected_metadata = {
                "tag": "smollm2:135m", "selfReportedDigest": "a" * 64,
                "sizeBytes": 270000000, "modelListQueryMs": 2,
                "identityCryptographicallyAttested": False,
            }
            mocked = {
                "model": "smollm2:135m", "done": True,
                "eval_count": 45, "prompt_eval_count": 109,
                "total_duration": 350000000, "response": '{"operation":"abstain"}',
            }
            with (
                mock.patch.dict(os.environ, {target.OPT_IN: "yes"}),
                mock.patch.object(target, "OUTPUT", evidence),
                mock.patch.object(target.gate, "bounded_file", return_value=source),
                mock.patch.object(target.gate, "artifact", return_value=root / "site/main.js"),
                mock.patch.object(target.gate, "diagnosis", return_value=diagnostics),
                mock.patch.object(target.gate, "CURRENT", root),
                mock.patch.object(target, "local_model_metadata", return_value=expected_metadata),
                mock.patch.object(target, "local_json", return_value=(mocked, 51)),
            ):
                result = target.trial()
            self.assertTrue(result["actualLocalModelRequestCompleted"])
            self.assertFalse(result["aiRepairVerifiedByBrowser"])
            self.assertFalse(result["ownerApprovalGranted"])
            self.assertEqual(result["responseClass"], "SCHEMA_VALID_ABSTAIN")
            self.assertTrue((evidence / "real-inference.json").exists())
            self.assertFalse((root / "advisory.json").exists() is False)
            # This test used mocked API responses: never count its pass as real inference.
            # Only the separate real Docker-backed CI job may claim real local model data.

    def test_invalid_response_is_measured_but_never_staged(self):
        with tempfile.TemporaryDirectory() as home:
            root = Path(home)
            source = b"function progress() {return 1;}\nconst p = 0.24;\n"
            response = {"model": target.MODEL, "done": True,
                        "eval_count": 12, "response": "not JSON"}
            metadata = {"tag": target.MODEL, "selfReportedDigest": "a" * 64,
                        "sizeBytes": 270000000, "modelListQueryMs": 2,
                        "identityCryptographicallyAttested": False}
            with (
                mock.patch.dict(os.environ, {target.OPT_IN: "yes"}),
                mock.patch.object(target, "OUTPUT", root / "out"),
                mock.patch.object(target.gate, "bounded_file", return_value=source),
                mock.patch.object(target.gate, "artifact", return_value=root / "site/main.js"),
                mock.patch.object(target.gate, "diagnosis", return_value=b"browser fault"),
                mock.patch.object(target.gate, "CURRENT", root),
                mock.patch.object(target, "local_model_metadata", return_value=metadata),
                mock.patch.object(target, "local_json", return_value=(response, 13)),
            ):
                evidence = target.trial()
            self.assertEqual(evidence["responseClass"], "INVALID_UNTRUSTED_MODEL_TEXT")
            self.assertFalse((root / "advisory.json").exists())
            self.assertFalse(evidence["aiRepairVerifiedByBrowser"])

    def test_mismatched_model_identity_or_zero_generated_tokens_fails(self):
        with tempfile.TemporaryDirectory() as home:
            root = Path(home)
            source = b"const p = 0.24;"
            metadata = {"tag": target.MODEL, "selfReportedDigest": "a" * 64,
                        "sizeBytes": 270000000, "modelListQueryMs": 2,
                        "identityCryptographicallyAttested": False}
            for reply in [{"model": "other:135m", "done": True,
                           "eval_count": 14, "response": "{}"},
                          {"model": target.MODEL, "done": True,
                           "eval_count": 0, "response": "{}"}]:
                with self.subTest(reply=reply), (
                    mock.patch.dict(os.environ, {target.OPT_IN: "yes"}),
                    mock.patch.object(target, "OUTPUT", root / "out"),
                    mock.patch.object(target.gate, "bounded_file", return_value=source),
                    mock.patch.object(target.gate, "artifact", return_value=root / "site/main.js"),
                    mock.patch.object(target.gate, "diagnosis", return_value=b"browser fault"),
                    mock.patch.object(target, "local_model_metadata", return_value=metadata),
                    mock.patch.object(target, "local_json", return_value=(reply, 13)),
                ):
                    with self.assertRaisesRegex(ValueError, "E34B_REAL_INFERENCE_COMPLETION_NOT_CONFIRMED"):
                        target.trial()


if __name__ == "__main__":
    unittest.main()
