"""Adversarial Stage 46.15 worker and Windows network evidence-policy tests."""
import copy
import unittest

from stage46_worker_trust_evidence import (
    DENY_COMPLETION, EXPLICIT_DENY, REQUIRED, SUCCESS_COMPLETION, TIMEOUT, review,
)

A = "a" * 64
B = "b" * 64


def fixture():
    return {
        "schema_version": 1,
        "worker_sha256_before": A,
        "worker_sha256_after": A,
        "tampered_fixture_sha256": B,
        "authenticode_status": "NotSigned",
        "appcontainer_lab_exit_code": 0,
        "observed_markers": sorted(REQUIRED | {
            DENY_COMPLETION, SUCCESS_COMPLETION, TIMEOUT
        }),
    }


class Stage4615TrustEvidenceTest(unittest.TestCase):
    def assert_no_release(self, outcome):
        self.assertEqual(outcome["decision"], "NO_GO")
        self.assertIs(outcome["production_authorized"], False)
        self.assertIs(outcome["physical_owner_pc_pilot_authorized"], False)
        self.assertIs(outcome["desktop_actions_authorized"], False)
        self.assertIs(outcome["network_egress_certified"], False)

    def test_correct_synthetic_timeout_is_valid_lab_not_network_certification(self):
        out = review(fixture())
        self.assertTrue(out["test_evidence_valid"])
        self.assertEqual(out["network_probe"], "TIMEOUT_INCONCLUSIVE")
        self.assertEqual(out["worker_provenance"], "UNSIGNED_TEST_FIXTURE")
        self.assertEqual(out["worker_integrity"], "BUILD_SESSION_SHA256_STABLE_TAMPER_DETECTED")
        self.assert_no_release(out)

    def test_genuine_single_listener_denial_remains_narrow_not_global_egress(self):
        evidence = fixture()
        evidence["observed_markers"].remove(TIMEOUT)
        evidence["observed_markers"].append(EXPLICIT_DENY)
        out = review(evidence)
        self.assertTrue(out["test_evidence_valid"])
        self.assertEqual(out["network_probe"], "EXPLICIT_DENY_SINGLE_LOOPBACK")
        self.assert_no_release(out)

    def test_missing_positive_control_is_rejected(self):
        evidence = fixture()
        evidence["observed_markers"].remove(
            "STAGE46_15_LOOPBACK_POSITIVE_CONTROL_CONNECTED")
        out = review(evidence)
        self.assertFalse(out["test_evidence_valid"])
        self.assert_no_release(out)

    def test_unstable_or_tampered_executable_is_rejected(self):
        for field, value in [
            ("worker_sha256_after", B),
            ("tampered_fixture_sha256", A),
            ("worker_sha256_before", "too-short"),
        ]:
            with self.subTest(field=field):
                evidence = fixture()
                evidence[field] = value
                out = review(evidence)
                self.assertFalse(out["test_evidence_valid"])
                self.assert_no_release(out)

    def test_worker_crash_and_missing_kernel_job_proof_are_rejected(self):
        crashed = fixture()
        crashed["appcontainer_lab_exit_code"] = 5
        self.assertFalse(review(crashed)["test_evidence_valid"])
        missing = fixture()
        missing["observed_markers"].remove("STAGE46_14_KERNEL_JOB_LIMITS_READ_BACK")
        self.assertFalse(review(missing)["test_evidence_valid"])

    def test_ambiguous_or_conflicting_network_markers_are_rejected(self):
        conflict = fixture()
        conflict["observed_markers"].append(EXPLICIT_DENY)
        out = review(conflict)
        self.assertEqual(out["network_probe"], "CONTRADICTORY")
        self.assertFalse(out["test_evidence_valid"])
        self.assert_no_release(out)

    def test_forged_connected_or_new_marker_does_not_pass(self):
        evidence = fixture()
        evidence["observed_markers"].append("STAGE46_14_CHILD_CONNECTED_UNEXPECTEDLY")
        self.assertFalse(review(evidence)["test_evidence_valid"])

    def test_provenance_not_granted_by_valid_authenticode_status_alone(self):
        evidence = fixture()
        evidence["authenticode_status"] = "Valid"
        out = review(evidence)
        self.assertEqual(out["worker_provenance"], "PUBLISHER_AND_CHAIN_NOT_PINNED")
        self.assertTrue(out["test_evidence_valid"])
        self.assert_no_release(out)

    def test_invalid_schema_and_untrusted_fields_are_rejected(self):
        for evidence in (None, [], {}, {"schema_version": 1}, "text"):
            self.assertFalse(review(evidence)["test_evidence_valid"])
        example = fixture()
        example["production_authorized"] = True
        self.assertFalse(review(example)["test_evidence_valid"])

    def test_duplicate_and_oversized_markers_are_rejected(self):
        data = fixture()
        data["observed_markers"].append(data["observed_markers"][0])
        self.assertFalse(review(data)["test_evidence_valid"])
        data = fixture()
        data["observed_markers"].append("x" * 161)
        self.assertFalse(review(data)["test_evidence_valid"])

    def test_missing_file_denial_and_stop_are_not_silently_accepted(self):
        for required in ("STAGE46_14_PROTECTED_SYNTHETIC_FILE_ACCESS_DENIED",
                         "STAGE46_14_APP_CONTAINER_STALL_JOB_KILLED"):
            data = fixture()
            data["observed_markers"].remove(required)
            self.assertFalse(review(data)["test_evidence_valid"])

    def test_schema_boolean_version_cannot_be_equal_to_integer_one(self):
        data = fixture()
        data["schema_version"] = True
        self.assertFalse(review(data)["test_evidence_valid"])

    def test_network_timeout_alone_without_clean_completion_is_invalid(self):
        data = fixture()
        data["observed_markers"].remove(DENY_COMPLETION)
        self.assertFalse(review(data)["test_evidence_valid"])


if __name__ == "__main__":
    unittest.main()
