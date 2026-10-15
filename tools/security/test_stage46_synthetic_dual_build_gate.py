"""Stage 46.18 attack cases for unsigned same-run dual build evidence."""
import unittest
from stage46_synthetic_dual_build_gate import review

A = "a" * 64
B = "b" * 64
C = "c" * 64


def example():
    return {
        "schema_version": 1,
        "fixture_kind": "FIXED_UNSIGNED_SYNTHETIC_CI_ONLY",
        "build_a_sha256": A, "build_b_sha256": A,
        "mutated_copy_sha256": B,
        "build_a_exit_code": 0, "build_b_exit_code": 0,
        "self_test_exit_code": 0, "authenticode_status": "NotSigned",
        "approved_publisher_certificate_sha256": None,
        "independently_approved_artifact_sha256": None,
        "signed_release_manifest": False,
    }


class Stage4618DualBuildGateTest(unittest.TestCase):
    def closed(self, result):
        self.assertEqual(result["decision"], "NO_GO")
        for name in ("publisher_signature_approved",
                     "independent_build_attestation_verified",
                     "immutable_verified_launch_verified",
                     "worker_install_authorized",
                     "microphone_authorized",
                     "production_authorized",
                     "owner_pc_pilot_authorized"):
            self.assertIs(result[name], False, name)

    def test_identical_twice_on_same_runner_only_validates_fixture(self):
        r = review(example())
        self.assertTrue(r["same_runner_dual_build_verified"])
        self.assertEqual(r["fixture_artifact_type"], "UNSIGNED_SAME_RUN_IDENTICAL_HASHES_ONLY")
        self.closed(r)

    def test_different_binary_hash_rejected(self):
        data = example()
        data["build_b_sha256"] = C
        self.assertFalse(review(data)["same_runner_dual_build_verified"])
        self.closed(review(data))

    def test_fake_tamper_copy_same_hash_rejected(self):
        data = example()
        data["mutated_copy_sha256"] = A
        self.assertFalse(review(data)["same_runner_dual_build_verified"])

    def test_unsigned_fixture_must_not_present_valid_signer_status(self):
        data = example()
        data["authenticode_status"] = "Valid"
        self.assertFalse(review(data)["same_runner_dual_build_verified"])

    def test_forged_certificate_approval_rejected(self):
        data = example()
        data["approved_publisher_certificate_sha256"] = C
        self.assertFalse(review(data)["same_runner_dual_build_verified"])

    def test_forged_release_artifact_approval_rejected(self):
        data = example()
        data["independently_approved_artifact_sha256"] = A
        self.assertFalse(review(data)["same_runner_dual_build_verified"])

    def test_forged_signed_manifest_rejected(self):
        data = example()
        data["signed_release_manifest"] = True
        self.assertFalse(review(data)["same_runner_dual_build_verified"])

    def test_any_compiler_or_self_test_failure_is_rejected(self):
        for name in ("build_a_exit_code", "build_b_exit_code", "self_test_exit_code"):
            with self.subTest(name=name):
                data = example()
                data[name] = 1
                self.assertFalse(review(data)["same_runner_dual_build_verified"])

    def test_schema_bool_not_integer_version(self):
        data = example()
        data["schema_version"] = True
        self.assertFalse(review(data)["same_runner_dual_build_verified"])

    def test_wrong_artifact_class_rejected(self):
        data = example()
        data["fixture_kind"] = "PRODUCTION_SIGNED_WORKER"
        self.assertFalse(review(data)["same_runner_dual_build_verified"])

    def test_extra_production_authorization_claim_rejected(self):
        data = example()
        data["production_authorized"] = True
        self.assertFalse(review(data)["same_runner_dual_build_verified"])

    def test_missing_version_or_bad_hashes_rejected(self):
        for bad in ("", "A" * 64, "z" * 64, "aa"):
            with self.subTest(hash=bad[:8]):
                data = example()
                data["build_a_sha256"] = bad
                self.assertFalse(review(data)["same_runner_dual_build_verified"])

    def test_wrong_types_are_rejected_in_exit_codes(self):
        for wrong in (True, 0.0, "0", None):
            data = example()
            data["self_test_exit_code"] = wrong
            self.assertFalse(review(data)["same_runner_dual_build_verified"])

    def test_empty_or_foreign_receipts_are_not_authority(self):
        for receipt in (None, [], {}, "signed", {"schema_version": 1}):
            r = review(receipt)
            self.assertFalse(r["same_runner_dual_build_verified"])
            self.closed(r)


if __name__ == "__main__":
    unittest.main()
