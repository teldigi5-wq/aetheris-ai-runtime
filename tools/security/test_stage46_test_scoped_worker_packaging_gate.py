"""Adversarial test-scoped synthetic worker Maven packaging and ZIP evidence."""
from __future__ import annotations
import copy
import json
import unittest
from pathlib import Path
from stage46_test_scoped_worker_packaging_gate import review,expected_classes

ROOT=Path(__file__).resolve().parents[2]
SRC_ROOT="io/aetheris/orchestrator/voice/"
SPEC=json.loads((ROOT/"docs/runtime/stage46-minimal-integration-scope.json").read_text(encoding="utf-8"))
POM=(ROOT/"orchestrator-service/pom.xml").read_text(encoding="utf-8")

def item(name):
    return {"name":name,"size":128,"encrypted":False}

def fixture():
    allclasses,mains=expected_classes(SPEC)
    boot=[item(n) for n in sorted(allclasses-mains)]
    boot += [item(n) for n in ("META-INF/","META-INF/MANIFEST.MF","BOOT-INF/","BOOT-INF/classes/","BOOT-INF/lib/spring-boot-3.3.3.jar")]
    worker=[item("META-INF/"),item("META-INF/MANIFEST.MF")]
    worker += [item(n.removeprefix("BOOT-INF/classes/")) for n in sorted(mains)]
    compiled=[n.removeprefix("BOOT-INF/classes/") for n in mains]
    return copy.deepcopy(SPEC),POM,boot,worker,compiled

def run(data):
    s,p,b,w,c = data
    return review(spec=s,pom=p,boot_entries=b,fixture_entries=w,
                  compiled_entries=c,default_profile_fixture_exists=False)

class Stage4623PackagingIsolationTest(unittest.TestCase):
    def no_go(self,x):
        self.assertEqual(x["decision"],"NO_GO")
        for key in ("publisher_signature_approved","runtime_isolation_certified",
                    "physical_owner_pc_pilot_authorized","production_authorized",
                    "merge_authorized"):
            self.assertIs(x[key],False,key)

    def test_default_boot_archive_does_not_include_worker_mains(self):
        r=run(fixture())
        self.assertTrue(r["packaging_separation_verified"])
        self.assertTrue(r["production_jar_excludes_two_synthetic_mains"])
        self.assertEqual(r["production_synthetic_helper_count"],7)
        self.assertEqual(r["production_worker_main_count"],0)
        self.no_go(r)

    def test_classpath_still_contains_worker_bytecode_for_tests(self):
        r=run(fixture())
        self.assertTrue(r["test_fixtures_remain_compiled"])
        self.assertEqual(r["test_fixture_worker_main_count"],2)
        self.no_go(r)

    def test_fixture_only_two_worker_classes(self):
        r=run(fixture())
        self.assertTrue(r["opt_in_fixture_jar_contains_only_two_mains"])
        self.assertTrue(r["maven_fixture_nonattached"])

    def test_normal_build_must_not_create_fixture_artifact(self):
        s,p,b,w,c=fixture()
        r=review(spec=s,pom=p,boot_entries=b,fixture_entries=w,
                 compiled_entries=c,default_profile_fixture_exists=True)
        self.assertFalse(r["packaging_separation_verified"])

    def test_reintroduced_pcm_worker_to_production_archive_is_rejected(self):
        d=list(fixture())
        d[2].append(item("BOOT-INF/classes/"+SRC_ROOT+"SyntraSyntheticPcmWorkerMain.class"))
        x=run(d)
        self.assertFalse(x["packaging_separation_verified"])
        self.no_go(x)

    def test_reintroduced_job_probe_to_production_archive_is_rejected(self):
        d=list(fixture())
        d[2].append(item("BOOT-INF/classes/"+SRC_ROOT+"SyntraSyntheticJobProbeMain.class"))
        self.assertFalse(run(d)["packaging_separation_verified"])

    def test_missing_a_reviewed_mainclasspath_synthetic_helper_rejected(self):
        d=list(fixture())
        d[2].pop(0)
        self.assertFalse(run(d)["packaging_separation_verified"])

    def test_unexpected_extra_fixture_bytecode_rejected(self):
        d=list(fixture())
        d[3].append(item(SRC_ROOT+"UnauthorizedMain.class"))
        self.assertFalse(run(d)["packaging_separation_verified"])

    def test_fixture_jar_must_not_contain_spring_boot_libraries(self):
        d=list(fixture())
        d[3].append(item("BOOT-INF/lib/junit.jar"))
        self.assertFalse(run(d)["packaging_separation_verified"])

    def test_compiled_class_missing_prevents_claiming_unchanged_tests(self):
        d=list(fixture())
        d[4].pop()
        self.assertFalse(run(d)["packaging_separation_verified"])

    def test_profile_activation_by_default_is_rejected(self):
        d=list(fixture())
        d[1]=d[1].replace("<id>synthetic-worker-lab</id>","<id>synthetic-worker-lab</id><activation><activeByDefault>true</activeByDefault></activation>")
        self.assertFalse(run(d)["packaging_separation_verified"])

    def test_fixture_maven_artifact_must_remain_nonattached(self):
        d=list(fixture())
        d[1]=d[1].replace("<attach>false</attach>","<attach>true</attach>")
        self.assertFalse(run(d)["packaging_separation_verified"])

    def test_missing_pom_exclusion_rejected(self):
        d=list(fixture())
        d[1]=d[1].replace("<exclude>"+SRC_ROOT+"SyntraSyntheticJobProbeMain.class</exclude>","")
        self.assertFalse(run(d)["packaging_separation_verified"])

    def test_unreviewed_fixture_class_enrollment_rejected(self):
        d=list(fixture())
        d[1]=d[1].replace("</includes>","<include>"+SRC_ROOT+"Dangerous.class</include></includes>")
        self.assertFalse(run(d)["packaging_separation_verified"])

    def test_new_candidate_without_approval_cannot_override_no_go(self):
        d=list(fixture())
        d[0]["controls"]["production_authorized"]=True
        # Original Stage46.21 manifest still has NO_GO; this policy is independent.
        r=run(d)
        self.no_go(r)

    def test_duplicate_zip_entries_rejected(self):
        d=list(fixture())
        d[3].append(copy.deepcopy(d[3][0]))
        self.assertFalse(run(d)["packaging_separation_verified"])

    def test_unsafe_zip_traversal_rejected(self):
        d=list(fixture())
        d[2].append(item("BOOT-INF/classes/../../secrets.env"))
        self.assertFalse(run(d)["packaging_separation_verified"])

    def test_sensitive_private_key_in_fixture_rejected(self):
        d=list(fixture())
        d[3].append(item("META-INF/release-private.pem"))
        self.assertFalse(run(d)["packaging_separation_verified"])

    def test_invalid_pom_dtd_and_external_entity_rejected(self):
        d=list(fixture())
        d[1]='<!DOCTYPE foo [ <!ENTITY e SYSTEM "file:///etc/passwd"> ]>'+d[1]
        self.assertFalse(run(d)["packaging_separation_verified"])

    def test_unexpected_production_nativelab_executable_rejected(self):
        d=list(fixture())
        d[2].append(item("BOOT-INF/classes/evil.exe"))
        self.assertFalse(run(d)["packaging_separation_verified"])

    def test_broken_input_types_and_missing_fixtures_fail(self):
        for bad in (None, {}, [], "archive"):
            d=list(fixture())
            d[3]=bad
            self.assertFalse(run(d)["packaging_separation_verified"])

    def test_prod_archive_cannot_be_mislabeled_as_fixture(self):
        d=list(fixture())
        d[3].append(item("BOOT-INF/"))
        self.assertFalse(run(d)["packaging_separation_verified"])

if __name__=="__main__":
    unittest.main()
