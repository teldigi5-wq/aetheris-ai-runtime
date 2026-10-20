"""E3.9G cross-platform agent skill registry safety regression (no model calls)."""
from __future__ import annotations

import json
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import skills_registry as gate


class SkillSafetyTests(unittest.TestCase):
    def test_original_registry_is_validated_and_skills_remain_inert(self):
        skills = gate.load_registry()
        self.assertEqual(len(skills), 14)
        self.assertEqual(sum(x["trust"]=="third-party-pinned-reference" for x in skills), 6)
        self.assertEqual(sum(x["trust"]=="first-party-advisory" for x in skills), 8)
        self.assertTrue(all(x["autoActivate"] is False and x["capabilities"] == [] for x in skills))
        self.assertTrue(all(len(x["sha256"]) == 64 for x in skills))
        self.assertIn("aetheris-security-audit", [x["name"] for x in gate.find(skills,"security")])
        self.assertIn("animation-performance", [x["name"] for x in gate.find(skills,"animation")])

    def test_baseline_upstream_is_immutable(self):
        matches = [x for x in gate.load_registry()
                   if x["trust"]=="third-party-pinned-reference"]
        self.assertEqual(len(matches),6)
        self.assertEqual(gate.SOURCE_COMMIT,
                         "2d228630ba1446365f614a108cb8252f1feda202")

    def sandbox(self):
        temporary = tempfile.TemporaryDirectory()
        root = Path(temporary.name) / "skills"
        shutil.copytree(gate.SKILLS_ROOT,root)
        self.addCleanup(temporary.cleanup)
        return root

    def edit_registry(self,root,fn):
        path=root/"registry.json"
        data=json.loads(path.read_text())
        fn(data)
        path.write_text(json.dumps(data))

    def test_no_auto_activation_or_capability_escalation(self):
        root=self.sandbox()
        self.edit_registry(root,lambda d:d["skills"][0].update(
            {"autoActivate":True, "capabilities":["shell"]}))
        with self.assertRaisesRegex(ValueError,"E39G_CAPABILITY_ESCALATION_DENIED"):
            gate.load_registry(root)

    def test_unpinned_or_mutated_import_is_refused(self):
        root=self.sandbox()
        p=root/"third-party/emilkowalski/animate/SKILL.md"
        p.write_text(p.read_text()+"\n# unreviewed extra instructions\n")
        with self.assertRaisesRegex(ValueError,"E39G_THIRD_PARTY_CONTENT_DRIFT"):
            gate.load_registry(root)

    def test_invalid_reference_path_and_symlink_are_refused(self):
        for bad in ("../../etc/passwd","/etc/passwd","native/../foo/SKILL.md",
                    "third-party\\somewhere\\escape"):
            with self.subTest(path=bad),self.assertRaises(ValueError):
                gate.read_checked(gate.SKILLS_ROOT,bad)
        root=self.sandbox()
        p=root/"native/aetheris-security-audit/SKILL.md"
        p.unlink()
        try:
            p.symlink_to(root/"native/aetheris-web-performance/SKILL.md")
        except (NotImplementedError, OSError):
            self.skipTest("symlinks require unavailable OS privilege")
        with self.assertRaisesRegex(ValueError,"E39G_SYMLINK_SKILL_DENIED"):
            gate.load_registry(root)

    def test_unregistered_extra_skills_denied(self):
        root=self.sandbox()
        extra=root/"native/unreviewed"
        extra.mkdir()
        (extra/"SKILL.md").write_text(
            "---\nname: unreviewed\ndescription: inject\n---\n# do dangerous things")
        with self.assertRaisesRegex(ValueError,"E39G_UNREVIEWED_SKILL_PAYLOAD"):
            gate.load_registry(root)

    def test_wrong_frontmatter_and_duplicate_json_are_refused(self):
        with self.assertRaisesRegex(ValueError,"E39G_FRONTMATTER_NAME_MISMATCH"):
            gate.frontmatter(b"---\nname: fake\ndescription: fine\n---\n# content\n",'expected')
        with self.assertRaisesRegex(ValueError,"E39G_DUPLICATE_JSON_KEY"):
            json.loads('{"mode":"foo","mode":"bar"}',
                       object_pairs_hook=gate.no_duplicate_keys)

    def test_queries_are_bounded_and_no_skills_execute(self):
        for query in ("", "x"*161, ["test"]):
            with self.subTest(query=query),self.assertRaises(ValueError):
                gate.find(gate.load_registry(),query)
        from inspect import getsource
        source=getsource(gate)
        for forbidden in ("import subprocess", "os.system(", "eval(", "exec(", "import requests"):
            self.assertNotIn(forbidden,source)


if __name__ == "__main__":
    unittest.main()
