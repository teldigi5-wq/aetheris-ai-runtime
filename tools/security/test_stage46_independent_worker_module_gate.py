"""Stage 46.24 standalone Maven worker fixture: adversarial and NO_GO tests."""
from __future__ import annotations
import copy
import unittest
from pathlib import Path
from stage46_independent_worker_module_gate import review,CLASSES,SOURCES

ROOT=Path(__file__).resolve().parents[2]
LAB=(ROOT/"synthetic-worker-lab/pom.xml").read_text(encoding="utf-8")
PROD=(ROOT/"orchestrator-service/pom.xml").read_text(encoding="utf-8")
def entry(name):
    return {"name":name,"size":96,"encrypted":False}
def evidence():
    fixed=["META-INF/","META-INF/MANIFEST.MF","io/","io/aetheris/",
           "io/aetheris/orchestrator/","io/aetheris/orchestrator/voice/"]
    lab=[entry(x) for x in fixed + sorted(CLASSES)]
    boot=[entry(x) for x in ("META-INF/","META-INF/MANIFEST.MF",
                           "BOOT-INF/","BOOT-INF/classes/",
                           "BOOT-INF/classes/io/aetheris/orchestrator/OrchestratorApplication.class",
                           "BOOT-INF/lib/spring-boot-3.3.3.jar")]
    return {"lab_pom":LAB,"prod_pom":PROD,"lab_entries":lab,
            "boot_entries":boot,"canonical_source_paths":sorted(SOURCES)}
def audit(value):
    return review(**value)

class Stage4624IndependentWorkerModuleTest(unittest.TestCase):
    def no_release(self,report):
        self.assertEqual(report["decision"],"NO_GO")
        for name in ("publisher_signature_approved","real_network_egress_denial_verified",
                     "source_migration_completed","module_maven_install_authorized",
                     "module_maven_deploy_authorized","physical_owner_pc_pilot_authorized",
                     "production_authorized"):
            self.assertIs(report[name],False,name)

    def test_standalone_no_dependency_two_canonical_workers(self):
        v=audit(evidence())
        self.assertTrue(v["module_pom_trust_contract_valid"])
        self.assertTrue(v["only_two_canonical_sources_used"])
        self.assertEqual(v["module_worker_bytecode_count"],2)
        self.assertEqual(v["module_dependencies_declared"],0)
        self.no_release(v)

    def test_real_main_archive_contains_no_synthetic_worker_main(self):
        v=audit(evidence())
        self.assertTrue(v["ordinary_spring_boot_contains_no_worker_main"])
        self.assertEqual(v["production_worker_bytecode_count"],0)
        self.no_release(v)

    def test_compiled_fixture_jar_may_only_include_two_worker_bytecodes(self):
        v=audit(evidence())
        self.assertTrue(v["only_two_worker_bytecodes_in_module_jar"])
        self.assertTrue(v["standalone_module_not_a_production_dependency"])

    def test_additional_worker_class_is_rejected(self):
        e=evidence()
        e["lab_entries"].append(entry("io/aetheris/orchestrator/voice/SurpriseMain.class"))
        self.assertFalse(audit(e)["only_two_worker_bytecodes_in_module_jar"])

    def test_unexpected_source_path_disallowed(self):
        e=evidence()
        e["canonical_source_paths"].append("io/aetheris/orchestrator/voice/MoreSource.java")
        self.assertFalse(audit(e)["only_two_canonical_sources_used"])

    def test_missing_single_canonical_source_rejected(self):
        e=evidence()
        e["canonical_source_paths"].pop()
        self.assertFalse(audit(e)["module_pom_trust_contract_valid"])

    def test_worker_returning_to_boot_jar_rejected(self):
        e=evidence()
        e["boot_entries"].append(entry("BOOT-INF/classes/io/aetheris/orchestrator/voice/SyntraSyntheticJobProbeMain.class"))
        v=audit(e)
        self.assertFalse(v["ordinary_spring_boot_contains_no_worker_main"])
        self.no_release(v)

    def test_module_maven_install_cannot_be_reenabled(self):
        e=evidence()
        e["lab_pom"]=e["lab_pom"].replace("<skip>true</skip>","<skip>false</skip>",1)
        self.assertFalse(audit(e)["module_pom_trust_contract_valid"])

    def test_module_maven_deploy_cannot_be_reenabled(self):
        e=evidence()
        e["lab_pom"]=e["lab_pom"].replace("<skip>true</skip>","<skip>false</skip>")
        self.assertFalse(audit(e)["module_pom_trust_contract_valid"])

    def test_production_maven_dependency_on_lab_rejected(self):
        e=evidence()
        e["prod_pom"]=e["prod_pom"].replace("</dependencies>","<dependency><groupId>io.aetheris.synthetic.lab</groupId><artifactId>syntra-synthetic-worker-lab-only</artifactId><version>0.2.0-SNAPSHOT</version></dependency></dependencies>")
        self.assertFalse(audit(e)["module_pom_trust_contract_valid"])

    def test_unapproved_new_maven_dependency_in_lab_rejected(self):
        e=evidence()
        e["lab_pom"]=e["lab_pom"].replace("<build>","<dependencies><dependency><groupId>org.springframework</groupId><artifactId>spring-context</artifactId><version>6.1.12</version></dependency></dependencies><build>")
        self.assertFalse(audit(e)["module_pom_trust_contract_valid"])

    def test_module_cannot_join_the_default_maven_reactor(self):
        e=evidence()
        e["prod_pom"]=e["prod_pom"].replace("<build>","<modules><module>../synthetic-worker-lab</module></modules><build>")
        self.assertFalse(audit(e)["module_pom_trust_contract_valid"])

    def test_additional_compiled_class_include_rejected(self):
        e=evidence()
        e["lab_pom"]=e["lab_pom"].replace("</includes>","<include>io/aetheris/orchestrator/voice/Danger.java</include></includes>",1)
        self.assertFalse(audit(e)["module_pom_trust_contract_valid"])

    def test_maven_compiler_must_target_java21(self):
        e=evidence()
        e["lab_pom"]=e["lab_pom"].replace("<release>21</release>","<release>17</release>")
        self.assertFalse(audit(e)["module_pom_trust_contract_valid"])

    def test_jar_include_allowlist_cannot_expand(self):
        e=evidence()
        e["lab_pom"]=e["lab_pom"].replace("</includes>","<include>**/*</include></includes>",1)
        self.assertFalse(audit(e)["module_pom_trust_contract_valid"])

    def test_zip_entry_duplicate_rejected(self):
        e=evidence()
        e["lab_entries"].append(copy.deepcopy(e["lab_entries"][0]))
        self.assertFalse(audit(e)["only_two_worker_bytecodes_in_module_jar"])

    def test_zip_entry_path_traversal_rejected(self):
        e=evidence()
        e["lab_entries"].append(entry("io/../hidden.class"))
        self.assertFalse(audit(e)["module_pom_trust_contract_valid"])

    def test_zip_embedded_private_key_rejected(self):
        e=evidence()
        e["lab_entries"].append(entry("META-INF/private-key.pem"))
        self.assertFalse(audit(e)["module_pom_trust_contract_valid"])

    def test_unexpected_nested_jar_rejected(self):
        e=evidence()
        e["lab_entries"].append(entry("BOOT-INF/lib/junit.jar"))
        self.assertFalse(audit(e)["module_pom_trust_contract_valid"])

    def test_untrusted_maven_doctype_rejected(self):
        e=evidence()
        e["lab_pom"]='<!DOCTYPE x [<!ENTITY x SYSTEM "file:///private">]>'+e["lab_pom"]
        self.assertFalse(audit(e)["module_pom_trust_contract_valid"])

    def test_bad_pom_type_or_source_input_rejected(self):
        for bad in (None, [], {}, 10):
            e=evidence()
            e["lab_pom"]=bad
            self.assertFalse(audit(e)["module_pom_trust_contract_valid"])

    def test_nonrelease_outcome_preserved_when_invalid(self):
        e=evidence()
        e["lab_entries"]=None
        self.no_release(audit(e))
        self.assertFalse(audit(e)["only_two_worker_bytecodes_in_module_jar"])

    def test_encrypted_jar_entry_rejected(self):
        e=evidence()
        e["lab_entries"][0]["encrypted"]=True
        self.assertFalse(audit(e)["module_pom_trust_contract_valid"])

if __name__=="__main__":
    unittest.main()
