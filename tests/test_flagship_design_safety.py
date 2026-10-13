"""Static verification of flagship design skill scope and quarantined reference libraries."""
import json
import shutil
import tempfile
import unittest
from pathlib import Path
from scripts.validate_agent_skills import validate_skills,ROOT
from scripts.validate_flagship_sources import validate,ALLOWED

THREE={"realistic-globe-experience-review","agent-journey-trust-review",
       "arrival-companion-ux-review"}

class FlagshipSafety(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.root=Path(self.tmp.name)
        shutil.copytree(ROOT/"experience-design",self.root/"experience-design")
    def tearDown(self):self.tmp.cleanup()
    def tamper(self,change):
        path=self.root/"experience-design"/"flagship-sources.json"
        d=json.loads(path.read_text())
        change(d)
        path.write_text(json.dumps(d))
    def test_static_catalog_and_templates(self):
        self.assertEqual([],validate(self.root))
        self.assertEqual([],validate_skills(ROOT))
        data=json.loads((ROOT/"skills/catalog.json").read_text())
        self.assertEqual(13,len(data["skills"]))
        selected=[x for x in data["skills"] if x["name"] in THREE]
        self.assertEqual(3,len(selected))
        for x in selected:
            self.assertEqual("READ_ONLY",x["effect"])
            self.assertEqual("TEMPLATE_ONLY",x["status"])
            self.assertEqual([],x["external_hosts"])
            self.assertFalse(x["execution_enabled"])
            self.assertTrue(x["owner_approval_required_for_mutations"])
    def test_cannot_fetch_or_install_references(self):
        self.tamper(lambda d:d["projects"][0].update(installed=True,enabled=True))
        self.assertTrue(validate(self.root))
    def test_cannot_fake_approved_license(self):
        self.tamper(lambda d:d.update(licenseApproved=True))
        self.assertTrue(validate(self.root))
    def test_cannot_swap_source_to_attacker(self):
        self.tamper(lambda d:d["projects"][1].update(url="https://attacker.invalid/map"))
        self.assertTrue(validate(self.root))
    def test_cannot_claim_live_integration(self):
        self.tamper(lambda d:d.update(projectDeploymentChanged=True,liveAPIDataClaimed=True))
        self.assertTrue(validate(self.root))
    def test_cannot_add_unreviewed_external_library(self):
        self.tamper(lambda d:d["projects"].append(dict(d["projects"][0],id="fake")))
        self.assertTrue(validate(self.root))
    def test_executable_payload_in_quarantine_rejected(self):
        (self.root/"experience-design"/"install.py").write_text("print('bad')")
        self.assertTrue(validate(self.root))
    def test_cannot_expand_skill_tools(self):
        with tempfile.TemporaryDirectory() as root:
            tmp=Path(root)
            shutil.copytree(ROOT/"skills",tmp/"skills")
            path=tmp/"skills/catalog.json"
            d=json.loads(path.read_text())
            next(x for x in d["skills"] if x["name"]=="agent-journey-trust-review")["allowed_tools"].append("shell.run")
            path.write_text(json.dumps(d))
            self.assertTrue(validate_skills(tmp))

if __name__=="__main__":unittest.main()
