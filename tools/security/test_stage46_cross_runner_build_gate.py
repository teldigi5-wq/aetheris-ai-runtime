"""Stage 46.19 adversarial tests — two hosted runners aren't a signed release."""
import copy
import unittest
from stage46_cross_runner_build_gate import review

D1 = "a" * 64
D2 = "b" * 64
D3 = "c" * 64
SHA = "f" * 40


def fixture():
    return {
        "schema_version": 1,
        "fixture_kind": "INERT_UNSIGNED_TWO_HOSTED_RUNNERS",
        "expected_head_sha": SHA,
        "signed_release_approved": False,
        "owner_pc_pilot_authorized": False,
        "production_authorized": False,
        "build_a": {
            "runner_role": "A", "artifact_sha256": D1,
            "source_sha256": D2, "authenticode_status": "NotSigned",
            "checkout_head_sha": SHA, "builder_status": "PASSED_FIXED_SELFTEST",
        },
        "build_b": {
            "runner_role": "B", "artifact_sha256": D1,
            "source_sha256": D2, "authenticode_status": "NotSigned",
            "checkout_head_sha": SHA, "builder_status": "PASSED_FIXED_SELFTEST",
        },
    }


class Stage4619CrossRunnerBuildTest(unittest.TestCase):
    def no_release(self, result):
        self.assertEqual(result["decision"], "NO_GO")
        for key in (
            "separate_trust_domains_verified",
            "reproducible_build_independent_attestation",
            "trusted_signing_certificate_approved",
            "release_binary_authorized", "owner_pc_pilot_authorized",
            "production_authorized",
        ):
            self.assertIs(result[key], False, key)

    def test_exact_two_runner_digests_pass_as_untrusted_build_evidence(self):
        r = review(fixture())
        self.assertTrue(r["cross_runner_hashes_verified"])
        self.assertTrue(r["same_source_revision_verified"])
        self.assertEqual(r["runner_count"], 2)
        self.no_release(r)

    def test_differing_artifact_hashes_are_not_reproducible(self):
        d = fixture()
        d["build_b"]["artifact_sha256"] = D3
        r = review(d)
        self.assertFalse(r["cross_runner_hashes_verified"])
        self.no_release(r)

    def test_differing_source_hashes_must_fail_before_binary_comparison(self):
        d = fixture()
        d["build_b"]["source_sha256"] = D3
        self.assertFalse(review(d)["cross_runner_hashes_verified"])

    def test_distinct_source_checkout_commit_is_rejected(self):
        d = fixture()
        d["build_b"]["checkout_head_sha"] = "0" * 40
        self.assertFalse(review(d)["cross_runner_hashes_verified"])

    def test_same_builder_role_fails_independent_runner_requirement(self):
        d = fixture()
        d["build_b"]["runner_role"] = "A"
        self.assertFalse(review(d)["cross_runner_hashes_verified"])

    def test_signed_release_marker_cannot_be_injected(self):
        d = fixture()
        d["signed_release_approved"] = True
        r = review(d)
        self.assertFalse(r["cross_runner_hashes_verified"])
        self.no_release(r)

    def test_valid_signature_claim_is_not_allowed_for_unsigned_fixture(self):
        d = fixture()
        d["build_a"]["authenticode_status"] = "Valid"
        self.assertFalse(review(d)["cross_runner_hashes_verified"])

    def test_case_noncanonical_digests_are_rejected(self):
        for replacement in ("A" * 64, "z" * 64, "a" * 63, "not-a-digest"):
            with self.subTest(hash=replacement[:8]):
                d = fixture()
                d["build_a"]["artifact_sha256"] = replacement
                self.assertFalse(review(d)["cross_runner_hashes_verified"])

    def test_subprocess_failure_not_accepted(self):
        d = fixture()
        d["build_a"]["builder_status"] = "FAILED"
        self.assertFalse(review(d)["cross_runner_hashes_verified"])

    def test_boolean_schema_version_rejected(self):
        d = fixture()
        d["schema_version"] = True
        self.assertFalse(review(d)["cross_runner_hashes_verified"])

    def test_pr_sha_length_and_content_checked(self):
        for bad in ("x" * 40, "f" * 39, ""):
            d = fixture()
            d["expected_head_sha"] = bad
            self.assertFalse(review(d)["cross_runner_hashes_verified"])

    def test_owner_pilot_authority_cannot_be_set_by_receipt(self):
        d = fixture()
        d["owner_pc_pilot_authorized"] = True
        self.assertFalse(review(d)["cross_runner_hashes_verified"])

    def test_unknown_fields_cannot_add_an_attestation(self):
        d = fixture()
        d["independently_signed_attestation"] = True
        self.assertFalse(review(d)["cross_runner_hashes_verified"])

    def test_unknown_builder_fields_rejected(self):
        d = fixture()
        d["build_a"]["trusted_signer"] = "fake"
        self.assertFalse(review(d)["cross_runner_hashes_verified"])

    def test_foreign_json_type_rejected_and_no_release(self):
        for v in (None, [], {}, "signed", {"schema_version": 1}):
            with self.subTest(value=str(v)[:15]):
                r = review(v)
                self.assertFalse(r["cross_runner_hashes_verified"])
                self.no_release(r)

    def test_production_authorized_is_always_false(self):
        d = fixture()
        d["production_authorized"] = True
        r = review(d)
        self.assertFalse(r["cross_runner_hashes_verified"])
        self.no_release(r)


if __name__ == "__main__":
    unittest.main()
