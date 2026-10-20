"""E3.9G-3 independent cross-language zero-authority policy audit."""
from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

import e39g3_validate_java_skill_manifest as contract


class CrossLanguageSkillContractTest(unittest.TestCase):
    def copy_manifest(self) -> tuple[tempfile.TemporaryDirectory, Path]:
        td = tempfile.TemporaryDirectory()
        self.addCleanup(td.cleanup)
        dest = Path(td.name)/"bindings.json"
        dest.write_bytes(contract.POLICY.read_bytes())
        return td, dest

    def edit(self, fn):
        _, path = self.copy_manifest()
        data=json.loads(path.read_text())
        fn(data)
        path.write_text(json.dumps(data))
        return path

    def test_exact_existing_role_and_skill_policy_passes(self):
        result=contract.validate()
        self.assertEqual(result["canonicalRoleCount"], 26)
        self.assertEqual(result["roles"], 7)
        self.assertEqual(result["skills"], 14)
        self.assertFalse(result["javaServiceRuntimeDefaultEnabled"])
        self.assertFalse(result["ownerAuthenticationEstablished"])
        self.assertEqual(result["modelCalls"],0)
        self.assertEqual(result["toolCalls"],0)

    def test_reject_skill_tool_claim_or_extra_root_schema(self):
        p=self.edit(lambda j:j["skills"][0].update({"allowedTools":["shell"]}))
        with self.assertRaisesRegex(ValueError,"E39G3_REFERENCE_SOURCE"):
            contract.validate(p)
        p=self.edit(lambda j:j.update({"allowProduction":True}))
        with self.assertRaisesRegex(ValueError,"E39G3_UNSAFE_ROOT"):
            contract.validate(p)

    def test_reject_existing_agent_cross_role_skill(self):
        p=self.edit(lambda j:j["roles"]["frontend-engineer"].append("aetheris-security-audit"))
        with self.assertRaisesRegex(ValueError,"E39G3_CROSS_LANGUAGE_ROLE_ESCALATION"):
            contract.validate(p)
        p=self.edit(lambda j:j["roles"]["appsec-officer"].append("animate"))
        with self.assertRaisesRegex(ValueError,"E39G3_CROSS_LANGUAGE_ROLE_ESCALATION"):
            contract.validate(p)

    def test_reject_pretended_vendor_repin_or_missing_skills(self):
        p=self.edit(lambda j:j.update({"originalSourceCommit":"latest"}))
        with self.assertRaisesRegex(ValueError,"E39G3_UNSAFE_ROOT"):
            contract.validate(p)
        p=self.edit(lambda j:j["skills"].pop())
        with self.assertRaisesRegex(ValueError,"E39G3_MISSING_OR_DUPLICATE"):
            contract.validate(p)
        p=self.edit(lambda j:j["skills"][0].update(
            {"sourcePath":"../../not-reviewed/SKILL.md"}))
        with self.assertRaisesRegex(ValueError,"E39G3_REFERENCE_SOURCE"):
            contract.validate(p)

    def test_duplicate_json_keys_are_denied(self):
        _,p=self.copy_manifest()
        p.write_text('{"schemaVersion":1,"schemaVersion":2}')
        with self.assertRaisesRegex(ValueError,"E39G_DUPLICATE_JSON_KEY"):
            contract.validate(p)


if __name__ == "__main__":
    unittest.main()
