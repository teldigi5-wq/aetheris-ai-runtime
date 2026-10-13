"""Negative contract tests: references are not installations."""
import copy
import json
import unittest
from scripts.validate_design_skills_sources import PATH, EXPECTED, validate_data, validate_file

class DesignSkillsReviewTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.data = json.loads(PATH.read_text(encoding="utf-8"))

    def test_exact_five_sources_are_staged_only(self):
        self.assertEqual(validate_file(), [])
        self.assertEqual(set(EXPECTED), {x["id"] for x in self.data["sources"]})

    def test_installation_and_execution_promotions_rejected(self):
        for key in ("auto_fetch", "auto_install", "auto_execute", "credential_grants", "filesystem_grants", "ci_runs_external_tools"):
            bad = copy.deepcopy(self.data)
            bad[key] = True
            self.assertTrue(validate_data(bad), key)
        for key in ("installed","enabled","execution_enabled","runtime_verified"):
            bad = copy.deepcopy(self.data)
            bad["sources"][0][key] = True
            self.assertTrue(validate_data(bad), key)

    def test_source_substitution_and_unreviewed_extra_fields_rejected(self):
        for key,value in (("revision","0"*40),("repository","random/fake"),
                          ("url","http://example.com"),("license","UNKNOWN"),
                          ("trust","TRUSTED"),("license_review","APPROVED")):
            bad = copy.deepcopy(self.data)
            bad["sources"][0][key] = value
            self.assertTrue(validate_data(bad), key)
        bad = copy.deepcopy(self.data)
        bad["sources"][0]["install_command"] = "curl | bash"
        self.assertTrue(validate_data(bad))

    def test_missing_and_duplicate_sources_rejected(self):
        bad = copy.deepcopy(self.data)
        bad["sources"].append(copy.deepcopy(bad["sources"][0]))
        self.assertTrue(validate_data(bad))
        bad = copy.deepcopy(self.data)
        bad["sources"][0]["id"] = bad["sources"][1]["id"]
        self.assertTrue(validate_data(bad))

if __name__=="__main__":
    unittest.main()
