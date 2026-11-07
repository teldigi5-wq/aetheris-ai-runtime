"""Offline test of the EXACT real-model SHA256 gate; no network/model calls."""
from __future__ import annotations

import io
import json
import os
from pathlib import Path
import tempfile
import textwrap
import unittest
from unittest.mock import patch
import urllib.request

EXPECTED = "dae161e27b0e90dd1856c8bb3209201fd6736d8eb66298e75ed87571486f4364"
MODEL = "qwen2.5-coder:7b"
WORKFLOW = (Path(__file__).resolve().parents[3] /
            ".github/workflows/runtime-e39g-heldout-model-bridge.yml")


def embedded_model_pin_script():
    text = WORKFLOW.read_text(encoding="utf-8")
    start = "          python3 - <<'PY'\n"
    end = "\n          PY"
    if text.count(start) != 1:
        raise AssertionError("Expected exactly one standalone digest-verifier script")
    body = text.split(start, 1)[1].split(end, 1)[0]
    return textwrap.dedent(body)


def run_mocked_digest_check(actual: str, *, model: str = MODEL):
    """Run production workflow's real inline verifier with a fake local HTTP response."""
    program = compile(embedded_model_pin_script(), str(WORKFLOW), "exec")
    with tempfile.TemporaryDirectory() as scratch:
        env_file = Path(scratch) / "env"
        data = {"models": [{"name": model, "digest": "sha256:" + actual,
                           "size": 4650000000}]}
        reply = io.BytesIO(json.dumps(data).encode("utf-8"))
        with patch.dict(os.environ, {
                "MODEL": MODEL, "MODEL_SHA256": EXPECTED,
                "GITHUB_ENV": str(env_file)
             }), patch.object(urllib.request, "urlopen", return_value=reply) as remote:
            exec(program, {"__name__": "__main__"})
            remote.assert_called_once_with("http://127.0.0.1:11434/api/tags", timeout=30)
        return env_file.read_text(encoding="utf-8")


class FullDigestPinTests(unittest.TestCase):
    def test_literal_full_256_bit_pin_is_present(self):
        self.assertEqual(len(EXPECTED), 64)
        raw = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("MODEL_SHA256: '" + EXPECTED + "'", raw)
        self.assertNotIn("digest.startswith(", raw)
        self.assertNotIn("startswith('dae161", raw)

    def test_exact_sha_allows_the_known_model_without_network(self):
        result = run_mocked_digest_check(EXPECTED)
        self.assertEqual(result, "PINNED_MODEL_SHA256=" + EXPECTED + "\n")

    def test_same_prefix_with_different_suffix_is_denied(self):
        changed = EXPECTED[:-1] + ("0" if EXPECTED[-1] != "0" else "1")
        self.assertEqual(changed[:20], EXPECTED[:20])
        with self.assertRaisesRegex(ValueError, "E39G_FULL_MODEL_SHA256_MISMATCH"):
            run_mocked_digest_check(changed)

    def test_other_model_digest_denied(self):
        with self.assertRaisesRegex(ValueError, "E39G_FULL_MODEL_SHA256_MISMATCH"):
            run_mocked_digest_check("a" * 64)

    def test_wrong_model_tag_denied_without_pin_export(self):
        with self.assertRaises(AssertionError):
            run_mocked_digest_check(EXPECTED, model="different-model:7b")


if __name__ == "__main__":
    unittest.main()
