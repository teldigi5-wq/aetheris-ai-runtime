"""E3.4 fake-local-server and untrusted-proposal checks. NO live model inference."""
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import tempfile
import threading
import unittest
from unittest import mock

import e34_model_adviser as m
import heuristic_repair as h


class FakeModel(BaseHTTPRequestHandler):
    response = b""
    requests = []

    def do_POST(self):
        data = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        self.requests.append((self.path, json.loads(data)))
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(self.response)))
        self.end_headers()
        self.wfile.write(self.response)

    def log_message(self, *_):
        pass


class AdvisoryBoundaryTests(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        self.current = self.root / "current"
        self.current.mkdir()
        self.site = self.root / "site"
        self.site.mkdir()
        self.source = self.site / "main.js"
        self.source.write_bytes(b"function progress(){return 0.8;}\nconst p = 0.17;\n")
        clean = self.source.read_bytes().replace(
            b"const p = 0.17;", b"const p = progress();")
        (self.site / "manifest.json").write_bytes(json.dumps(
            {"generatedFilesSha256": {"main.js": hashlib.sha256(clean).hexdigest()}}
        ).encode())
        self.browser = json.dumps({
            "evidenceClass": "E32_REAL_CHROMIUM_FAULT_CONTRACT",
            "caseId": "scroll", "failureCode": "E32_SCROLL_PROGRESS_STUCK",
            "passed": False, "browserObserved": {"progress": 17}
        }).encode()
        (self.current / "diagnosis.json").write_bytes(self.browser)
        for replacement, value in (
            ("artifact", lambda _category: self.source),
            ("diagnosis", lambda _category: self.browser),
            ("CURRENT", self.current), ("SITE", self.site)):
            patcher = mock.patch.object(h, replacement, value)
            patcher.start()
            self.addCleanup(patcher.stop)

    def write_advisory(self, raw, **changes):
        data = {"evidenceClass": "E34_LOCAL_OLLAMA_RESPONSE_UNATTESTED_NO_ACTION",
                "category": "scroll", "sourceSha256": m.sha(self.source.read_bytes()),
                "diagnosisSha256": m.sha(self.browser),
                "modelRequested": "test-local:latest", "modelResponse": raw,
                "responseSha256": m.sha(raw.encode()), "elapsedMs": 2,
                "transportClass": "LOOPBACK_OLLAMA_RESPONSE_UNATTESTED",
                "modelIdentityAttested": False, "ownerApprovalGranted": False,
                "modelOutputExecuted": False, "productionPublished": False}
        data.update(changes)
        (self.current / "advisory.json").write_bytes(json.dumps(data).encode())

    def test_strict_json_refuses_unknown_fields_and_double_keys(self):
        self.assertEqual(m.strict_json('{"operation":"abstain"}'),
                         {"operation": "abstain"})
        for text in ('{"operation":"abstain","operation":"replace"}',
                     '{"operation":"shell","command":"sh"}',
                     '{"operation":"replace","old":"x","new":"y","admin":true}',
                     '["replace"]', 'not JSON'):
            with self.subTest(text=text), self.assertRaises(ValueError):
                m.strict_json(text)

    def test_query_cannot_connect_without_two_opt_in_conditions(self):
        with mock.patch.dict(os.environ, {m.MODEL_OPT_IN: "no"}):
            with self.assertRaisesRegex(ValueError, "E34_LOCAL_MODEL_EXPLICIT_OPT_IN_REQUIRED"):
                m.generate("test-local:latest", "scroll", self.source.read_bytes(),
                           self.browser, explicit_opt_in=True)
        with mock.patch.dict(os.environ, {m.MODEL_OPT_IN: "yes"}):
            with self.assertRaisesRegex(ValueError, "E34_LOCAL_MODEL_EXPLICIT_OPT_IN_REQUIRED"):
                m.generate("test-local:latest", "scroll", self.source.read_bytes(),
                           self.browser, explicit_opt_in=False)

    def test_mock_loopback_endpoint_and_no_proxy(self):
        FakeModel.requests = []
        FakeModel.response = json.dumps({
            "model": "test-local:latest", "done": True,
            "response": '{"operation":"abstain"}'}).encode()
        server = ThreadingHTTPServer(("127.0.0.1", 0), FakeModel)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        with mock.patch.dict(os.environ, {m.MODEL_OPT_IN: "yes",
                                           "HTTP_PROXY": "http://evil.invalid"}):
            value, meta = m.generate("test-local:latest", "scroll",
                self.source.read_bytes(), self.browser,
                port=server.server_port, explicit_opt_in=True)
        self.assertEqual(m.strict_json(value)["operation"], "abstain")
        self.assertFalse(meta["modelIdentityAttested"])
        self.assertEqual(FakeModel.requests[0][0], "/api/generate")
        self.assertEqual(FakeModel.requests[0][1]["model"], "test-local:latest")
        self.assertFalse(FakeModel.requests[0][1]["stream"])

    def test_abstention_has_no_patch(self):
        self.write_advisory('{"operation":"abstain"}')
        self.assertEqual(m.prepare("scroll")["status"], "ABSTAIN_NO_PROPOSAL")
        self.assertFalse((self.current / "proposal.json").exists())

    def test_text_staging_still_needs_separate_grammar_gate(self):
        text = json.dumps({"operation": "replace", "old": "const p = 0.17;",
                           "new": "const p = progress();"})
        self.write_advisory(text)
        result = m.prepare("scroll")
        self.assertEqual(result["status"], "UNTRUSTED_TEXT_PROPOSAL_STAGED_NOT_APPLIED")
        self.assertIn(b"const p = 0.17;", self.source.read_bytes())
        permitted = h.apply("scroll")
        self.assertFalse(permitted["ownerApproved"])
        self.assertIn(b"const p = progress();", self.source.read_bytes())

    def test_injected_network_code_staged_but_refused_by_independent_gate(self):
        text = json.dumps({"operation": "replace", "old": "const p = 0.17;",
                           "new": 'fetch("https://evil.invalid")'})
        self.write_advisory(text)
        m.prepare("scroll")
        with self.assertRaisesRegex(ValueError, "E33_PATCH_GRAMMAR_DENIED"):
            h.apply("scroll")
        self.assertIn(b"const p = 0.17;", self.source.read_bytes())

    def test_tampered_digest_owner_bit_and_context_denied(self):
        for changes, message in [
            ({"responseSha256": "f" * 64}, "E34_MODEL_RESPONSE_DIGEST_MISMATCH"),
            ({"ownerApprovalGranted": True}, "E34_UNTRUSTED_ADVISORY_CONTEXT"),
            ({"sourceSha256": "a" * 64}, "E34_UNTRUSTED_ADVISORY_CONTEXT"),
        ]:
            with self.subTest(message=message):
                self.write_advisory('{"operation":"abstain"}', **changes)
                with self.assertRaisesRegex(ValueError, message):
                    m.prepare("scroll")

    def test_oversized_or_symlink_advisory_denied(self):
        self.write_advisory('{"operation":"abstain"}')
        path = self.current / "advisory.json"
        path.write_bytes(b"x" * 5000)
        with self.assertRaisesRegex(ValueError, "E33_SOURCE_OR_EVIDENCE_NOT_REGULAR"):
            m.prepare("scroll")
        path.unlink()
        underneath = self.current / "behind.json"
        underneath.write_bytes(b"{}")
        try:
            path.symlink_to(underneath)
        except OSError:
            self.skipTest("symlink unavailable")
        with self.assertRaisesRegex(ValueError, "E33_SOURCE_OR_EVIDENCE_NOT_REGULAR"):
            m.prepare("scroll")

    def test_untrusted_cli_actions_denied(self):
        for args in ([], ["apply", "scroll"], ["query", "scroll"],
                     ["query", "scroll", "model", "--shell"]):
            with self.subTest(args=args), self.assertRaisesRegex(
                    ValueError, "E34_ONLY_QUERY_OR_PREPARE_ALLOWED"):
                m.main(args)


if __name__ == "__main__":
    unittest.main()
