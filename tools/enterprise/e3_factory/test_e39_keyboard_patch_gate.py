"""E3.9A independent new keyboard defect policy attack tests. No AI was used."""
import json
from pathlib import Path
import unittest

import e39_keyboard_patch_gate as gate
import worker


class KeyboardDefectPolicyTests(unittest.TestCase):
    def setUp(self):
        self.canonical = (worker.HERE / "styles.css").read_bytes()
        self.assertEqual(self.canonical.decode("utf-8").count(gate.AFTER), 1)
        self.assertEqual(self.canonical.decode("utf-8").count(gate.BEFORE), 0)
        self.faulty = self.canonical.decode("utf-8").replace(
            gate.AFTER, gate.BEFORE, 1).encode("utf-8")
        self.browser = b'{"evidenceClass":"E39_CHROMIUM_KEYBOARD_ACCESSIBILITY","passed":false}'
        self.manifest = {
            "generatedFilesSha256": {"styles.css": gate.sha(self.canonical)}
        }
        self.valid = {
            "version": 1, "kind": gate.KIND,
            "caseId": "keyboard-skip-link", "path": "site/styles.css",
            "baseSha256": gate.sha(self.faulty),
            "resultSha256": gate.sha(self.canonical),
            "beforeBrowserSha256": gate.sha(self.browser),
            "old": gate.BEFORE, "new": gate.AFTER,
        }

    def test_exact_single_literal_in_good_manifests_passes(self):
        self.assertEqual(
            gate.validate(self.valid, self.faulty, self.browser, self.manifest),
            self.canonical
        )

    def test_unknown_fields_forged_owner_approval_and_commands_denied(self):
        for k, v in [
            ("ownerApproved", True), ("command", "curl attacker.test"),
            ("targetHost", "owner-pc"), ("deploy", True),
            ("modelWasRun", True), ("extraFiles", ["secrets.env"])
        ]:
            with self.subTest(k=k):
                altered = dict(self.valid, **{k: v})
                with self.assertRaisesRegex(ValueError, "E39_UNTRUSTED_PROPOSAL_REJECTED"):
                    gate.validate(altered, self.faulty, self.browser, self.manifest)

    def test_wrong_path_kind_case_and_hashes_all_denied(self):
        for key, value in [
            ("path", "../.env"), ("path", "site/main.js"),
            ("version", True), ("version", 2), ("kind", "EXECUTE_SHELL"),
            ("caseId", "scroll-24"), ("baseSha256", "0" * 64),
            ("beforeBrowserSha256", "f" * 64),
            ("old", ".skip-link:focus{top:-80px}body{display:none}"),
            ("new", '.skip-link:focus{top:12px}script{src:"evil"}')
        ]:
            with self.subTest(key=key, value=value):
                altered = dict(self.valid, **{key: value})
                with self.assertRaisesRegex(ValueError, "E39_UNTRUSTED_PROPOSAL_REJECTED"):
                    gate.validate(altered, self.faulty, self.browser, self.manifest)

    def test_wrong_canonical_digest_blocks_even_exact_literal(self):
        altered = dict(self.valid, resultSha256="f" * 64)
        with self.assertRaisesRegex(ValueError, "E39_CANONICAL_SOURCE_HASH_REJECTED"):
            gate.validate(altered, self.faulty, self.browser, self.manifest)
        with self.assertRaisesRegex(ValueError, "E39_CANONICAL_SOURCE_HASH_REJECTED"):
            gate.validate(self.valid, self.faulty, self.browser, {
                "generatedFilesSha256": {"styles.css": "f" * 64}
            })

    def test_duplicated_target_is_not_ambiguously_modified(self):
        source = self.faulty + b"\n" + gate.BEFORE.encode()
        amended = dict(self.valid, baseSha256=gate.sha(source))
        with self.assertRaisesRegex(ValueError, "E39_AMBIGUOUS_CSS_EDIT"):
            gate.validate(amended, source, self.browser, self.manifest)

    def test_no_authority_or_import_of_e39_fixture_into_ai_adviser(self):
        import inspect
        script = inspect.getsource(gate)
        self.assertNotIn("subprocess", script)
        self.assertNotIn("os.system", script)
        self.assertNotIn("import requests", script)
        self.assertIn('"modelWasCalled": False', script)
        self.assertIn('"ownerApproved": False', script)
        self.assertIn('"productionPublished": False', script)
        llm_code = (worker.HERE / "e38c_structured_inference.py").read_text(
            encoding="utf-8")
        self.assertNotIn("e39_keyboard_evaluator", llm_code)
        self.assertNotIn("keyboard-skip-link", llm_code)
        self.assertNotIn("E39_FOCUSED_SKIP_LINK_OFFSCREEN", llm_code)


if __name__ == "__main__":
    unittest.main()
