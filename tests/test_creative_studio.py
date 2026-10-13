"""Fail-closed creative skill and library metadata safety tests, no hardware/network."""
import json
import shutil
import tempfile
import unittest
from pathlib import Path
from scripts.validate_creative_studio import ROOT, PINNED, validate
from scripts.validate_agent_skills import validate_skills

EXPECTED_SKILLS = {
    "ui-ux-audit", "motion-experience-review","3d-web-experience-review",
    "frontend-quality-review","creative-production-review"
}

class CreativeStudioTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.root=Path(self.tmp.name)
        shutil.copytree(ROOT/"creative-studio",self.root/"creative-studio")
    def tearDown(self):
        self.tmp.cleanup()
    def mutate(self, fn):
        f=self.root/"creative-studio"/"sources.json"
        data=json.loads(f.read_text(encoding="utf-8"))
        fn(data)
        f.write_text(json.dumps(data),encoding="utf-8")

    def test_safe_catalog(self):
        self.assertEqual([],validate(self.root))
        self.assertEqual(12,len(PINNED))
        data=json.loads((self.root/"creative-studio"/"sources.json").read_text())
        self.assertEqual(12,len(data["sources"]))
        self.assertTrue(all(not x["executionEnabled"] and x["sourceSha"] is None for x in data["sources"]))
    def test_no_third_party_install_or_credential_grants(self):
        for key in ("automaticDownload","executionEnabled","dependencyAndAssetLicensesReviewed"):
            with self.subTest(key=key):
                self.mutate(lambda d:d["sources"][0].update({key:True}))
                self.assertTrue(validate(self.root))
                shutil.copy2(ROOT/"creative-studio"/"sources.json",self.root/"creative-studio"/"sources.json")
        self.mutate(lambda d:d["sources"][1].update(requestedCredentials=["token"]))
        self.assertTrue(validate(self.root))
    def test_mismatched_license_and_repo_rejected(self):
        self.mutate(lambda d:d["sources"][0].update(reportedGithubLicense="MIT-CERTIFIED",repository="attacker/repo"))
        self.assertTrue(validate(self.root))
    def test_unknown_plugins_not_autoaccepted(self):
        self.mutate(lambda d:d["sources"].append(dict(d["sources"][0],id="unknown")))
        self.assertTrue(validate(self.root))
    def test_hardware_truth_cannot_be_changed(self):
        self.mutate(lambda d:d.update(physicalPcStatus="CERTIFIED",automaticCodeExecution=True))
        self.assertTrue(validate(self.root))
    def test_extra_python_scripts_fail(self):
        (self.root/"creative-studio"/"boot.py").write_text("print('unsafe')")
        self.assertTrue(validate(self.root))
    def test_five_creative_read_only_skills_still_fail_closed(self):
        self.assertEqual([],validate_skills(ROOT))
        skills=json.loads((ROOT/"skills"/"catalog.json").read_text())["skills"]
        creative=[x for x in skills if x["name"] in EXPECTED_SKILLS]
        self.assertEqual(5,len(creative))
        for s in creative:
            self.assertEqual("TEMPLATE_ONLY",s["status"])
            self.assertEqual("READ_ONLY",s["effect"])
            self.assertFalse(s["execution_enabled"])
            self.assertEqual([],s["external_hosts"])
            self.assertTrue(s["owner_approval_required_for_mutations"])
    def test_tamper_skills_unapproved_tool(self):
        with tempfile.TemporaryDirectory() as d:
            scratch=Path(d)
            shutil.copytree(ROOT/"skills",scratch/"skills")
            c=scratch/"skills"/"catalog.json"
            data=json.loads(c.read_text())
            skill=next(x for x in data["skills"] if x["name"]=="ui-ux-audit")
            skill["allowed_tools"].append("shell.run")
            c.write_text(json.dumps(data))
            self.assertTrue(validate_skills(scratch))

if __name__=="__main__":
    unittest.main()
