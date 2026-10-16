"""Stage 46.22 synthetic fixture JAR-index adversarial security regressions."""
from __future__ import annotations

import copy
import json
import unittest
from pathlib import Path

from stage46_jar_packaging_evidence import review, expected_classes

MANIFEST = (Path(__file__).resolve().parents[2] /
            "docs/runtime/stage46-minimal-integration-scope.json")
HASH_A = "a" * 64
HASH_B = "b" * 64


def entry(name, size=1, encrypted=False):
    return {"name": name, "size": size, "encrypted": encrypted}


def fixture():
    scope = json.loads(MANIFEST.read_text(encoding="utf-8"))
    java_classes, _ = expected_classes(scope)
    orchestrator = [
        entry("META-INF/"), entry("META-INF/MANIFEST.MF"),
        entry("BOOT-INF/"), entry("BOOT-INF/classes/"),
        entry("BOOT-INF/classes/io/aetheris/orchestrator/OrchestratorApplication.class"),
        entry("BOOT-INF/lib/spring-boot-3.3.3.jar"),
        *[entry(c) for c in sorted(java_classes)],
    ]
    workstation = [
        entry("META-INF/"), entry("META-INF/MANIFEST.MF"),
        entry("io/aetheris/workstation/AetherisHostAgent.class"),
        entry("com/fasterxml/jackson/databind/ObjectMapper.class"),
    ]
    return scope, orchestrator, workstation


def check(spec, orch, workstation, digest_a=HASH_A, digest_b=HASH_B):
    return review(spec, orch, workstation,
                  orchestrator_digest=digest_a,
                  workstation_digest=digest_b)


class Stage4622PackagedJarEvidenceTest(unittest.TestCase):
    def never_approve_release(self, result):
        self.assertEqual(result["decision"], "NO_GO")
        self.assertIs(result["synthetic_classes_are_excluded_from_orchestrator_jar"], False)
        for prop in ("release_signer_verified", "minimum_integration_candidate_approved",
                     "real_microphone_authorized", "desktop_actions_authorized",
                     "owner_pc_pilot_authorized", "production_authorized"):
            self.assertIs(result[prop], False, prop)

    def test_realistic_boot_and_shade_jars_report_nine_compiled_synthetic_classes(self):
        spec, a, b = fixture()
        out = check(spec, a, b)
        self.assertTrue(out["packaged_artifacts_inspected"])
        self.assertEqual(out["compiled_synthetic_classes_embedded_count"], 9)
        self.assertEqual(out["compiled_synthetic_main_entrypoints_embedded_count"], 2)
        self.assertEqual(out["orchestrator_nested_dependency_count"], 1)
        self.assertEqual(out["workstation_class_entry_count"], 2)
        self.assertFalse(out["test_only_classes_or_dependencies_detected"])
        self.never_approve_release(out)

    def test_missing_synthetic_class_does_not_validate_inventory(self):
        s, a, b = fixture()
        a.pop()
        r = check(s, a, b)
        self.assertFalse(r["packaged_artifacts_inspected"])
        self.never_approve_release(r)

    def test_real_synthetic_workermains_remain_embedded_until_separated(self):
        s, a, b = fixture()
        mainpath = s["allowed_synthetic_main_entrypoints"][0]
        name = "BOOT-INF/classes/" + mainpath.removeprefix(
            "orchestrator-service/src/main/java/").removesuffix(".java") + ".class"
        a = [item for item in a if item["name"] != name]
        r = check(s, a, b)
        self.assertEqual(r["compiled_synthetic_main_entrypoints_embedded_count"], 1)
        self.assertFalse(r["packaged_artifacts_inspected"])

    def test_testing_bytecode_cannot_be_published_inside_orchestrator_jar(self):
        s, a, b = fixture()
        a.append(entry("BOOT-INF/classes/io/aetheris/orchestrator/voice/"
                       "SyntraSyntheticAudioGateTest.class"))
        r = check(s, a, b)
        self.assertFalse(r["packaged_artifacts_inspected"])
        self.assertTrue(r["test_only_classes_or_dependencies_detected"])

    def test_testing_bytecode_cannot_be_published_inside_host_jar(self):
        s, a, b = fixture()
        b.append(entry("io/aetheris/workstation/HostAgentSecurityTest.class"))
        self.assertFalse(check(s, a, b)["packaged_artifacts_inspected"])

    def test_junit_jar_in_boot_lib_rejected(self):
        s, a, b = fixture()
        a.append(entry("BOOT-INF/lib/junit-jupiter-api-5.10.3.jar"))
        r = check(s, a, b)
        self.assertFalse(r["packaged_artifacts_inspected"])
        self.assertIn("BOOT-INF/lib/junit-jupiter-api-5.10.3.jar",
                      r["test_only_classes_or_dependencies_detected"])

    def test_mockito_test_dependency_in_archive_rejected(self):
        s, a, b = fixture()
        a.append(entry("BOOT-INF/lib/mockito-core-5.1.jar"))
        self.assertFalse(check(s, a, b)["packaged_artifacts_inspected"])

    def test_test_h2_database_dependency_in_production_archive_rejected(self):
        s, a, b = fixture()
        a.append(entry("BOOT-INF/lib/h2-2.2.224.jar"))
        self.assertFalse(check(s, a, b)["packaged_artifacts_inspected"])

    def test_signed_and_private_key_material_is_never_allowed_in_lab_evidence(self):
        s, a, b = fixture()
        for name in ("BOOT-INF/classes/owner.private.key",
                     "BOOT-INF/classes/config/secrets.pem",
                     "BOOT-INF/classes/.env", "BOOT-INF/classes/signing.p12"):
            with self.subTest(name=name):
                data = a + [entry(name)]
                r = check(s, data, b)
                self.assertFalse(r["packaged_artifacts_inspected"])
                self.assertTrue(r["prohibited_sensitive_resource_entries"])
                self.never_approve_release(r)

    def test_shaded_host_package_rejects_native_windows_lab_payload(self):
        s, a, b = fixture()
        b.append(entry("io/aetheris/workstation/WindowsSyntheticAppContainerLab.exe"))
        r = check(s, a, b)
        self.assertFalse(r["packaged_artifacts_inspected"])
        self.assertTrue(r["unexpected_lab_native_payload_entries"])

    def test_missing_spring_boot_marker_or_library_is_unverified(self):
        s, a, b = fixture()
        for suffix in ("BOOT-INF/", "BOOT-INF/classes/",
                       "BOOT-INF/lib/spring-boot-3.3.3.jar"):
            with self.subTest(suffix=suffix):
                copy_a = [item for item in a if item["name"] != suffix]
                self.assertFalse(check(s, copy_a, b)["packaged_artifacts_inspected"])

    def test_missing_shaded_host_entrypoint_fails(self):
        s, a, b = fixture()
        b = [item for item in b
             if item["name"] != "io/aetheris/workstation/AetherisHostAgent.class"]
        self.assertFalse(check(s, a, b)["packaged_artifacts_inspected"])

    def test_duplicate_jar_entry_rejected_before_claiming_valid_evidence(self):
        s, a, b = fixture()
        a.append(copy.deepcopy(a[0]))
        r = check(s, a, b)
        self.assertFalse(r["packaged_artifacts_inspected"])
        self.never_approve_release(r)

    def test_zip_path_traversal_and_absolute_entries_rejected(self):
        s, a, b = fixture()
        for path in ("../evil", "/etc/passwd", "\\Windows\\secret",
                     "BOOT-INF/../unsafe", "BOOT-INF\\classes\\Bad.class",
                     "BOOT-INF/classes/fake\nname", "BOOT-INF/classes/bad\x00name"):
            with self.subTest(path=repr(path)):
                self.assertFalse(check(s, a + [entry(path)], b)
                                 ["packaged_artifacts_inspected"])

    def test_encrypted_or_negative_size_entry_rejected(self):
        s, a, b = fixture()
        self.assertFalse(check(s, a + [entry("BOOT-INF/classes/a", encrypted=True)], b)
                         ["packaged_artifacts_inspected"])
        self.assertFalse(check(s, a + [entry("BOOT-INF/classes/a", size=-1)], b)
                         ["packaged_artifacts_inspected"])

    def test_zip_uncompressed_bomb_is_not_accepted(self):
        s, a, b = fixture()
        self.assertFalse(check(s, a + [entry("BOOT-INF/classes/huge.dat",size=3_000_000_001)], b)
                         ["packaged_artifacts_inspected"])

    def test_forged_digest_never_validates_claimed_binary(self):
        s, a, b = fixture()
        for digest in ("wrong", "A" * 64, "", None):
            self.assertFalse(check(s, a, b, digest_a=digest)
                             ["packaged_artifacts_inspected"])

    def test_source_manifest_missing_class_or_unsigned_go_claim_rejected(self):
        s, a, b = fixture()
        s["release_decision"] = "GO"
        self.assertFalse(check(s, a, b)["packaged_artifacts_inspected"])
        s, a, b = fixture()
        s["compiled_synthetic_java_sources"] = []
        self.assertFalse(check(s, a, b)["packaged_artifacts_inspected"])

    def test_malformed_input_never_authorizes_a_release(self):
        s, a, b = fixture()
        for x in (None, {}, [], "jar", [{"name":"x"}]):
            with self.subTest(data=str(x)[:15]):
                r = check(s, x, b)
                self.assertFalse(r["packaged_artifacts_inspected"])
                self.never_approve_release(r)

    def test_no_real_distributed_artifact_is_created_by_the_policy(self):
        s, a, b = fixture()
        report = check(s, a, b)
        self.assertFalse(report["minimum_integration_candidate_approved"])
        self.assertFalse(report["release_signer_verified"])
        self.assertFalse(report["production_authorized"])
        self.never_approve_release(report)


if __name__ == "__main__":
    unittest.main()
