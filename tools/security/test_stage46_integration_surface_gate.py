"""Stage 46.21 adversarial compiled-synthetic vs production-wiring policy."""
from __future__ import annotations

import copy
import json
import unittest
from pathlib import Path

from stage46_integration_surface_gate import review_inventory

SPEC = (Path(__file__).resolve().parents[2] / "docs/runtime"
        / "stage46-minimal-integration-scope.json")


def fixtures():
    spec = json.loads(SPEC.read_text(encoding="utf-8"))
    java = {
        path: f"package io.aetheris.orchestrator.voice;\n"
              f"public final class {Path(path).stem} {{"
              + (" public static void main(String[] args) {} "
                 if path in spec["allowed_synthetic_main_entrypoints"] else "") + "}\n"
        for path in spec["compiled_synthetic_java_sources"]
    }
    java["orchestrator-service/src/main/java/io/aetheris/orchestrator/Main.java"] = (
        "package io.aetheris.orchestrator; public class Main { }\n"
    )
    scripts = {"workstation-agent/packaging/install.ps1":
               "# Existing host launcher — needs independent manual review.\n"}
    return spec, java, scripts


def check(spec, java, scripts):
    return review_inventory(spec, java_sources=java, script_sources=scripts)


class Stage4621IntegrationSurfaceGateTest(unittest.TestCase):
    def assert_no_release(self, report):
        self.assertEqual(report["decision"], "NO_GO")
        self.assertFalse(report["excluded_from_production_artifact"])
        self.assertTrue(report["compiled_on_main_classpath"])
        for field in ("signed_release_artifacts_verified", "integration_candidate_created",
                      "merge_authorized", "owner_pc_pilot_authorized",
                      "microphone_authorized", "desktop_actions_authorized",
                      "production_authorized"):
            self.assertIs(report[field], False, field)

    def test_compiled_java_inventory_is_not_a_signed_or_excluded_release(self):
        s,j,p=fixtures()
        report=check(s,j,p)
        self.assertTrue(report["integration_review_valid"])
        self.assertEqual(report["compiled_synthetic_java_count"],9)
        self.assertEqual(report["java_main_entrypoint_count"],2)
        self.assert_no_release(report)

    def test_spring_component_registration_in_lab_fails(self):
        s,j,p=fixtures()
        path=s["compiled_synthetic_java_sources"][0]
        j[path]="@Component\n" + j[path]
        r=check(s,j,p)
        self.assertFalse(r["integration_review_valid"])
        self.assertIn(path,r["unexpected_main_class_registration"])

    def test_spring_rest_controller_registration_in_lab_fails(self):
        s,j,p=fixtures()
        path=s["compiled_synthetic_java_sources"][1]
        j[path]="@RestController\n" + j[path]
        self.assertFalse(check(s,j,p)["integration_review_valid"])

    def test_java_microphone_audio_import_in_compiled_synthetic_fails(self):
        s,j,p=fixtures()
        path=s["compiled_synthetic_java_sources"][1]
        j[path]="import javax.sound.sampled.TargetDataLine;\n"+j[path]
        self.assertFalse(check(s,j,p)["integration_review_valid"])

    def test_java_robot_in_compiled_synthetic_fails(self):
        s,j,p=fixtures()
        path=s["compiled_synthetic_java_sources"][1]
        j[path]="import java.awt.Robot;\n"+j[path]
        self.assertFalse(check(s,j,p)["integration_review_valid"])

    def test_system_native_library_load_fails(self):
        s,j,p=fixtures()
        path=s["compiled_synthetic_java_sources"][1]
        j[path]+="\nSystem.loadLibrary(\"mystery\");"
        self.assertFalse(check(s,j,p)["integration_review_valid"])

    def test_mistaken_extra_worker_main_fails(self):
        s,j,p=fixtures()
        path=next(q for q in s["compiled_synthetic_java_sources"]
                  if q not in s["allowed_synthetic_main_entrypoints"])
        j[path] += " public static void main(String[] args) {}\n"
        self.assertFalse(check(s,j,p)["integration_review_valid"])

    def test_missing_one_of_the_two_fixed_child_mains_fails(self):
        s,j,p=fixtures()
        path=s["allowed_synthetic_main_entrypoints"][0]
        j[path]=j[path].replace("static void main", "static void notmain")
        self.assertFalse(check(s,j,p)["integration_review_valid"])

    def test_new_java_synthetic_main_class_must_be_reviewed(self):
        s,j,p=fixtures()
        j["orchestrator-service/src/main/java/io/aetheris/orchestrator/voice/SyntraSyntheticSurprise.java"] = (
            "public class SyntraSyntheticSurprise {}")
        self.assertFalse(check(s,j,p)["integration_review_valid"])

    def test_absent_compiled_helper_source_fails(self):
        s,j,p=fixtures()
        del j[s["compiled_synthetic_java_sources"][1]]
        self.assertFalse(check(s,j,p)["integration_review_valid"])

    def test_unapproved_production_java_caller_is_detected(self):
        s,j,p=fixtures()
        j["orchestrator-service/src/main/java/io/aetheris/orchestrator/Main.java"] = (
            "package io.aetheris.orchestrator;\n"
            "class Main { SyntraSyntheticProcessSpeechBoundary risky; }\n")
        result=check(s,j,p)
        self.assertFalse(result["integration_review_valid"])
        self.assertEqual(len(result["unapproved_production_java_callers"]),1)

    def test_unapproved_workstation_installer_stage46_wiring_detected(self):
        s,j,p=fixtures()
        p["workstation-agent/packaging/install.ps1"] += (
            ". './stage46/Test-SyntraWindowsJobLab.ps1'\n")
        result=check(s,j,p)
        self.assertFalse(result["integration_review_valid"])
        self.assertIn("workstation-agent/packaging/install.ps1",
                      result["unapproved_production_packaging_wiring"])

    def test_comments_do_not_indicate_real_java_registration(self):
        s,j,p=fixtures()
        j["orchestrator-service/src/main/java/io/aetheris/orchestrator/Main.java"] += (
            "/* SyntraSyntheticProcessSpeechBoundary review only */\n")
        self.assertTrue(check(s,j,p)["integration_review_valid"])

    def test_user_cannot_enable_install_by_changing_spec_flag(self):
        s,j,p=fixtures()
        s["controls"]["merge_authorized"]=True
        r=check(s,j,p)
        self.assertFalse(r["integration_review_valid"])
        self.assert_no_release(r)

    def test_user_cannot_mark_production_class_excluded(self):
        s,j,p=fixtures()
        s["controls"]["production_authorized"]=True
        self.assertFalse(check(s,j,p)["integration_review_valid"])

    def test_spec_cannot_enroll_a_tenth_compiled_class(self):
        s,j,p=fixtures()
        s["compiled_synthetic_java_sources"].append(
            "orchestrator-service/src/main/java/io/aetheris/orchestrator/voice/SyntraSyntheticExfil.java")
        self.assertFalse(check(s,j,p)["integration_review_valid"])

    def test_missing_unreviewed_installer_flag_is_rejected(self):
        s,j,p=fixtures()
        s["unreviewed_production_entrypoints"].remove(
            "workstation-agent/packaging/install.ps1")
        self.assertFalse(check(s,j,p)["integration_review_valid"])

    def test_forged_release_decision_fails_review(self):
        s,j,p=fixtures()
        s["release_decision"]="GO"
        r=check(s,j,p)
        self.assertFalse(r["integration_review_valid"])
        self.assert_no_release(r)

    def test_schema_bool_does_not_equal_integer(self):
        s,j,p=fixtures()
        s["schema_version"]=True
        self.assertFalse(check(s,j,p)["integration_review_valid"])

    def test_malformed_spec_and_source_fail_closed(self):
        s,j,p=fixtures()
        for bad in (None,{},[],{"schema_version":1},"synthetic"):
            self.assertFalse(check(bad,j,p)["integration_review_valid"])
        j[s["compiled_synthetic_java_sources"][0]]=None
        self.assertFalse(check(s,j,p)["integration_review_valid"])

    def test_untrusted_job_shell_outside_lab_is_detected(self):
        s,j,p=fixtures()
        p["workstation-agent/packaging/activate.ps1"] = (
            '& "./packaging/stage46/Test-SyntraWindowsWfpReadOnly.ps1"'
        )
        self.assertFalse(check(s,j,p)["integration_review_valid"])


if __name__ == "__main__":
    unittest.main()
