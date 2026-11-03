"""E6 W5-L pin verifier adversarial exercises, using only tiny FAKE blobs."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from w5l_model_blob_integrity import (
    CONFIG, LAYERS, MANIFEST_RELATIVE, MANIFEST_TYPE,
    descriptor, verify_local_model,
)


def fixture(root):
    bodies = (
        b"fake cfg 1", b"fake gguf 1", b"fake system 1",
        b"fake template 1", b"fake license 1"
    )
    kinds = (CONFIG[0],) + tuple(x[0] for x in LAYERS)
    expected = tuple(
        (kind, hashlib.sha256(body).hexdigest(), len(body))
        for kind, body in zip(kinds, bodies)
    )
    manifest_path = root / MANIFEST_RELATIVE
    manifest_path.parent.mkdir(parents=True)
    blobs = root / "blobs"
    blobs.mkdir()
    for (_, digest, _), body in zip(expected, bodies):
        (blobs / ("sha256-" + digest)).write_bytes(body)
    data = {
        "schemaVersion": 2, "mediaType": MANIFEST_TYPE,
        "config": descriptor(expected[0]),
        "layers": [descriptor(x) for x in expected[1:]],
    }
    manifest_path.write_text(json.dumps(data), encoding="utf-8")
    return expected, manifest_path


class LocalModelPinTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="w5l-blob-fixture-")
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.spec, self.manifest = fixture(self.root)

    def verify(self):
        return verify_local_model(self.root, config=self.spec[0], layers=self.spec[1:])

    def test_only_valid_exact_synthetic_descriptors_pass(self):
        checked = self.verify()
        self.assertTrue(checked["allFiveLocalBlobsSha256Matched"])
        self.assertFalse(checked["modelWeightsActuallyExecutedAttested"])
        self.assertFalse(checked["releaseGo"])

    def test_rejects_corrupt_file_even_when_size_matches(self):
        p = self.root / "blobs" / ("sha256-" + self.spec[1][1])
        b = bytearray(p.read_bytes())
        b[0] ^= 1
        p.write_bytes(bytes(b))
        with self.assertRaisesRegex(ValueError, "PINNED_BLOB_HASH_MISMATCH"):
            self.verify()

    def test_rejects_shortened_blob(self):
        p = self.root / "blobs" / ("sha256-" + self.spec[1][1])
        p.write_bytes(b"x")
        with self.assertRaisesRegex(ValueError, "PINNED_BLOB_SIZE_INVALID"):
            self.verify()

    def test_rejects_symlinked_blob_even_with_matching_bytes(self):
        p = self.root / "blobs" / ("sha256-" + self.spec[1][1])
        original = p.read_bytes()
        p.unlink()
        replacement = self.root / "outside-model"
        replacement.write_bytes(original)
        p.symlink_to(replacement)
        with self.assertRaisesRegex(ValueError, "PINNED_BLOB_MISSING_OR_SYMLINK"):
            self.verify()

    def test_rejects_modified_manifest_and_unapproved_layers(self):
        original = self.manifest.read_text()
        for modify in [
            lambda d: d["layers"].reverse(),
            lambda d: d["layers"][0].update({"size": 100}),
            lambda d: d.update({"releaseGo": True}),
            lambda d: d["config"].update({"digest": "sha256:" + "f" * 64}),
            lambda d: d["layers"].append(d["layers"][0]),
        ]:
            data = json.loads(original)
            modify(data)
            self.manifest.write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError, "MODEL_MANIFEST_NOT_PINNED"):
                self.verify()
        self.manifest.write_text(original)

    def test_rejects_ambiguous_duplicate_manifest_keys(self):
        data = self.manifest.read_text()
        ambiguous = data.replace('"schemaVersion": 2,',
                                 '"schemaVersion": 2, "schemaVersion": 2,', 1)
        self.manifest.write_text(ambiguous)
        with self.assertRaisesRegex(ValueError, "MANIFEST_PATH_OR_JSON_UNTRUSTED"):
            self.verify()

    def test_workflow_orders_exact_hash_and_bounded_pull_before_inference(self):
        path = Path(__file__).parents[4] / ".github" / "workflows" / "runtime-e6-w5l-optin-paired-cpu.yml"
        workflow = path.read_text()
        pin = workflow.index("python3 w5l_model_blob_integrity.py")
        inference = workflow.index("node w5l_owner_cpu_cli.mjs run")
        pull = workflow.index("timeout --signal=TERM --kill-after=10s 600s")
        self.assertLess(pull, pin)
        self.assertLess(pin, inference)
        self.assertIn("Recheck LIVE owner approval again immediately before paired inference", workflow)
        self.assertIn("timeout-minutes: 55", workflow)
        self.assertNotIn("workflow_dispatch:", workflow)

    def test_rejects_manifest_symlink_escape(self):
        self.manifest.unlink()
        external = Path(self.tmp.name).parent / ("w5l-external-" + Path(self.tmp.name).name)
        external.write_text("{}", encoding="utf-8")
        self.addCleanup(lambda: external.unlink(missing_ok=True))
        self.manifest.symlink_to(external)
        with self.assertRaisesRegex(ValueError, "MANIFEST_PATH_OR_JSON_UNTRUSTED"):
            self.verify()

    def test_strict_production_cli_refuses_injected_root_and_args(self):
        script = Path(__file__).with_name("w5l_model_blob_integrity.py")
        common = {**os.environ, "RUNNER_TEMP": str(self.root), "OLLAMA_MODELS": str(self.root),
                  "GITHUB_ACTIONS": "true"}
        for args in ([], ["another-model"]):
            result = subprocess.run([sys.executable, str(script), *args],
                                    env=common, capture_output=True, text=True,
                                    timeout=8, check=False)
            self.assertNotEqual(result.returncode, 0)
            self.assertNotIn("MODEL_BLOBS_MATCH", result.stdout)

    def test_production_descriptors_are_full_sha256_and_fixed_size(self):
        self.assertEqual(LAYERS[0][1],
            "c5396e06af294bd101b30dce59131a76d2b773e76950acc870eda801d3ab0515")
        self.assertEqual(LAYERS[0][2], 397807936)
        self.assertEqual(len((CONFIG, *LAYERS)), 5)


if __name__ == "__main__":
    unittest.main()
