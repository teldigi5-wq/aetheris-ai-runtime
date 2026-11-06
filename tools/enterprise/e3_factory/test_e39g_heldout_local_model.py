"""B4G/B4H interoperability tests using a MOCK Ollama, never real model scoring."""
import io
import json
from pathlib import Path
import tempfile
import unittest
import e39g12b4g_curator_boundary as b4g
import e39g12b4h_proposal_admission as b4h
import e39g_heldout_local_model as bridge

MODEL = "qwen2.5-coder:7b"
DIGEST = "a" * 64

class Reply(io.BytesIO):
    status = 200

class MockOllama:
    def __init__(self, digest=DIGEST, failure=False):
        self.digest = digest
        self.failure = failure
        self.calls = []
    def open(self, request, timeout=None):
        self.calls.append((request.full_url, request.get_method()))
        if request.full_url == bridge.HOST + "/api/tags":
            value = {"models": [{"name": MODEL, "digest": "sha256:" + self.digest}]}
        else:
            body = json.loads(request.data)
            assert body["model"] == MODEL
            assert body["stream"] is False
            assert "reference.html" not in body["prompt"]
            assert "referenceSha256" not in body["prompt"]
            value = {"model": MODEL, "response": json.dumps({"operation":"abstain"}),
                     "done": not self.failure}
        return Reply(json.dumps(value).encode())

def packet():
    cases = []
    for i in range(4):
        html = "<!doctype html><html><body>bad focus %s</body></html>" % i
        cases.append({"id": "case-0"+str(i+1), "site": "site-alpha" if i<2 else "site-beta",
            "fault": "focus-rule", "defectDescription": "Please check keyboard focus visibility.",
            "sourceLicense": "CC0-1.0", "brokenHtml": html,
            "brokenSha256": b4g.digest(html.encode())})
    return b4g.packet_of(cases, "f"*64)

class BridgeTests(unittest.TestCase):
    def setUp(self):
        temp=tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root=Path(temp.name)
        self.bundle=self.root/"model.zip"
        self.out=self.root/"receipt.json"
        self.bundle.write_bytes(b4g.public_zip(packet()))

    def test_exact_four_calls_emit_b4h_compatible_receipt(self):
        client=MockOllama()
        result=bridge.infer(self.bundle,self.out,MODEL,DIGEST,client)
        self.assertEqual(result["observedCalls"],4)
        self.assertFalse(result["independentCuratorVerified"])
        self.assertEqual(len(client.calls),5)
        self.assertTrue(all(url.startswith(bridge.HOST+"/api/") for url,_ in client.calls))
        self.assertEqual(self.out.stat().st_mode & 0o777,0o600)
        receipt=b4g.parse(self.out.read_bytes())
        self.assertEqual(set(receipt),b4h.RECEIPT_FIELDS)
        self.assertEqual(receipt["modelCallsSelfReported"],4)
        for trial,case in zip(receipt["trials"],packet()["cases"]):
            self.assertEqual(set(trial),b4h.TRIAL_FIELDS)
            self.assertEqual(trial["rawResponseSha256"],b4g.digest(trial["rawModelResponse"].encode()))
            self.assertEqual(b4h.inspect_response(trial["rawModelResponse"],case)["outcome"],"MODEL_ABSTAINED")

    def test_wrong_pinned_digest_denied_before_inference(self):
        c=MockOllama(digest="b"*64)
        with self.assertRaisesRegex(ValueError,"DIGEST_MISMATCH"):
            bridge.infer(self.bundle,self.out,MODEL,DIGEST,c)
        self.assertEqual(len(c.calls),1)
        self.assertFalse(self.out.exists())

    def test_incomplete_reply_creates_no_success_receipt(self):
        with self.assertRaisesRegex(ValueError,"INCOMPLETE"):
            bridge.infer(self.bundle,self.out,MODEL,DIGEST,MockOllama(failure=True))
        self.assertFalse(self.out.exists())

    def test_reference_field_leakage_denied(self):
        bad=packet()
        bad["cases"][0]["referenceSha256"]="b"*64
        self.bundle.write_bytes(b4g.public_zip(bad))
        with self.assertRaisesRegex(ValueError,"UNEXPECTED_CASE_FIELDS"):
            bridge.infer(self.bundle,self.out,MODEL,DIGEST,MockOllama())

    def test_replayed_output_or_symlink_denied(self):
        self.out.write_text("existing")
        with self.assertRaisesRegex(ValueError,"OUTPUT_MUST_BE_NEW"):
            bridge.infer(self.bundle,self.out,MODEL,DIGEST,MockOllama())
        self.out.unlink()
        self.out.symlink_to(self.bundle)
        with self.assertRaisesRegex(ValueError,"SYMLINK"):
            bridge.infer(self.bundle,self.out,MODEL,DIGEST,MockOllama())

    def test_malformed_model_name_and_partial_digest_denied(self):
        for name,digest in [("http://external",DIGEST),(MODEL,"abc")]:
            with self.assertRaises(ValueError):
                bridge.infer(self.bundle,self.out,name,digest,MockOllama())
        self.assertFalse(self.out.exists())

    def test_bridge_never_installs_models_or_executes_repairs(self):
        s=Path(bridge.__file__).read_text()
        for marker in ("/api/pull","subprocess.","os.system(", "shell=True", "eval("):
            self.assertNotIn(marker,s)

if __name__=="__main__":
    unittest.main()
