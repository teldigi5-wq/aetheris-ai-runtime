"""E3.9F runtime-seeded protocol invariants; NO real LLM calls in unit tests."""
from __future__ import annotations
import inspect
import unittest
from unittest import mock

import e39f_runtime_sealed_challenge as blind


class RuntimeBlindSeededChallenge(unittest.TestCase):
    def test_seed_reproduction_and_distinct_runtime_parameters(self):
        one=blind.generate_rules(bytes.fromhex("aa"*32))
        two=blind.generate_rules(bytes.fromhex("bb"*32))
        self.assertEqual(one,blind.generate_rules(bytes.fromhex("aa"*32)))
        self.assertEqual(set(one),set(blind.CASE_IDS))
        self.assertNotEqual(one,two)
        self.assertEqual(len(one),8)
        for case, item in one.items():
            with self.subTest(case=case):
                self.assertTrue(item["defectiveRule"].startswith(".skip-link:focus{"))
                self.assertEqual(item["expectedFailure"],blind.FAILURE[item["family"]])
                self.assertNotIn("url(",item["defectiveRule"])
                self.assertNotIn("!important",item["defectiveRule"])

    def test_wrong_seed_or_case_is_rejected(self):
        for seed in (b"",b"short",b"a"*64):
            with self.subTest(length=len(seed)), self.assertRaisesRegex(ValueError,"E39F_SEED_LENGTH_INVALID"):
                blind.generate_rules(seed)
        for case in ("challenge-00","challenge-09","../private.json","top-40"):
            with self.subTest(case=case),self.assertRaisesRegex(ValueError,"E39F_CASE_NOT_ALLOWED"):
                blind.generated(case,{"cases":{}})
        with self.assertRaisesRegex(ValueError,"E39F_BAD_CASE"):
            blind.directory("../../etc")

    def test_no_model_credit_and_no_seed_in_public_receipt_schema(self):
        source=inspect.getsource(blind)
        self.assertIn('"modelGeneratedRepair":False',source)
        self.assertIn('"realModelCalls":0',source)
        self.assertIn('"externallyCuratedSealedCorpus":False',source)
        self.assertIn("hmac.digest(",source)
        self.assertIn("secrets.token_bytes(32)",source)
        self.assertNotIn("ollama",source)
        self.assertNotIn("openrouter",source)
        self.assertNotIn("subprocess",source)
        self.assertNotIn("requests",source)

    def test_fixed_network_none_runner_remains_bound(self):
        from pathlib import Path
        runner=(Path(__file__).parent/"run_locked_worker.sh").read_text()
        self.assertIn("e39f-restore)",runner)
        self.assertIn("challenge-0[1-8]",runner)
        self.assertIn("--network none",runner)
        self.assertIn("--read-only --cap-drop ALL",runner)
        self.assertIn("--memory 256m --memory-swap 256m",runner)


if __name__ == "__main__":
    unittest.main()
