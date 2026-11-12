"""Agent Skills catalog contract tests; no tool or network execution."""
import json
import shutil
import tempfile
import unittest
from pathlib import Path

from scripts.validate_agent_skills import ROOT, validate_skills


class AgentSkillsSafetyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        shutil.copytree(ROOT / "skills", self.root / "skills")

    def tearDown(self):
        self.temp.cleanup()

    def mutate(self, func):
        path = self.root / "skills" / "catalog.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        func(data)
        path.write_text(json.dumps(data), encoding="utf-8")

    def test_catalog_is_safe_and_declarative(self):
        self.assertEqual([], validate_skills(self.root))

    def test_execution_cannot_be_enabled(self):
        self.mutate(lambda d: d.update(execution_enabled=True))
        self.assertTrue(validate_skills(self.root))

    def test_new_tool_cannot_be_added(self):
        self.mutate(lambda d: d["skills"][0]["allowed_tools"].append("github.repo.write"))
        self.assertTrue(validate_skills(self.root))

    def test_readonly_effect_cannot_be_promoted(self):
        self.mutate(lambda d: d["skills"][1].update(effect="MUTATE"))
        self.assertTrue(validate_skills(self.root))

    def test_external_domain_cannot_be_expanded(self):
        self.mutate(lambda d: d["skills"][0]["external_hosts"].append("evil.example"))
        self.assertTrue(validate_skills(self.root))

    def test_traversal_is_rejected(self):
        self.mutate(lambda d: d["skills"][0].update(path="../../other/SKILL.md"))
        self.assertTrue(validate_skills(self.root))

    def test_unverified_skill_cannot_claim_activation(self):
        self.mutate(lambda d: d["skills"][0].update(status="PHYSICAL_VALIDATED"))
        self.assertTrue(validate_skills(self.root))

    def test_bad_frontmatter_is_rejected(self):
        path = self.root / "skills" / "github-readonly" / "SKILL.md"
        path.write_text(path.read_text().replace("name: github-readonly", "name: root-agent"), encoding="utf-8")
        self.assertTrue(validate_skills(self.root))

    def test_scripts_cannot_be_smuggled_into_skill_directory(self):
        rogue = self.root / "skills" / "github-readonly" / "run.sh"
        rogue.write_text("echo unsafe", encoding="utf-8")
        self.assertTrue(validate_skills(self.root))

    def test_hardware_truth_cannot_be_promoted(self):
        self.mutate(lambda d: d.update(physical_pc_status="VALIDATED"))
        self.assertTrue(validate_skills(self.root))


if __name__ == "__main__":
    unittest.main()
