"""Stage 46.16 read-only WFP and unapproved release artifact security tests."""
import copy
import unittest

from stage46_wfp_provenance_gate import (
    APPROVED_PUBLISHER_CERT_SHA256,
    APPROVED_RELEASE_ARTIFACT_SHA256,
    EXPECTED_WFP_MARKERS,
    review,
)
from stage46_worker_trust_evidence import (
    REQUIRED, TIMEOUT, DENY_COMPLETION, SUCCESS_COMPLETION, EXPLICIT_DENY,
)

HASH_A = "a" * 64
HASH_B = "b" * 64


def receipt():
    return {
        "schema_version": 1,
        "stage46_15_evidence": {
            "schema_version": 1,
            "worker_sha256_before": HASH_A,
            "worker_sha256_after": HASH_A,
            "tampered_fixture_sha256": HASH_B,
            "authenticode_status": "NotSigned",
            "appcontainer_lab_exit_code": 0,
            "observed_markers": sorted(REQUIRED | {TIMEOUT, DENY_COMPLETION, SUCCESS_COMPLETION}),
        },
        "wfp_probe_exit_code": 0,
        "wfp_observed_markers": sorted(EXPECTED_WFP_MARKERS),
        "publisher_certificate_sha256": None,
    }


class Stage4616WfpReleaseGateTest(unittest.TestCase):
    def assert_not_authorized(self, result):
        self.assertEqual(result["decision"], "NO_GO")
        for key in (
            "network_egress_certified", "correlated_wfp_denial",
            "signing_key_or_release_artifact_approved",
            "production_authorized", "owner_pc_pilot_authorized",
            "microphone_authorized", "desktop_actions_authorized",
        ):
            self.assertIs(result[key], False, key)

    def test_verified_wfp_layers_are_not_a_network_firewall_denial(self):
        result = review(receipt())
        self.assertTrue(result["synthetic_diagnostics_valid"])
        self.assertEqual(result["wfp_observation"], "READ_ONLY_ALE_V4_V6_LAYERS_PRESENT")
        self.assert_not_authorized(result)

    def test_sha256_only_detects_local_build_stability_not_publisher_identity(self):
        result = review(receipt())
        self.assertEqual(result["worker_build_integrity"],
                         "BUILD_SESSION_SHA256_STABLE_TAMPER_DETECTED")
        self.assertEqual(result["worker_provenance"], "UNSIGNED_TEST_FIXTURE")
        self.assertIs(result["trusted_worker_publisher"], False)
        self.assertIs(result["trusted_worker_artifact"], False)
        self.assert_not_authorized(result)

    def test_no_approved_signers_or_release_artifacts_have_been_enrolled(self):
        self.assertEqual(len(APPROVED_PUBLISHER_CERT_SHA256), 0)
        self.assertEqual(len(APPROVED_RELEASE_ARTIFACT_SHA256), 0)

    def test_fake_valid_authenticode_status_does_not_make_worker_trusted(self):
        evidence = receipt()
        evidence["stage46_15_evidence"]["authenticode_status"] = "Valid"
        evidence["publisher_certificate_sha256"] = HASH_B
        output = review(evidence)
        self.assertTrue(output["synthetic_diagnostics_valid"])
        self.assertFalse(output["trusted_worker_publisher"])
        self.assertFalse(output["trusted_worker_artifact"])
        self.assert_not_authorized(output)

    def test_fake_publisher_allowlist_in_receipt_is_rejected(self):
        evidence = receipt()
        evidence["allowed_signers"] = [HASH_B]
        self.assertFalse(review(evidence)["synthetic_diagnostics_valid"])

    def test_wfp_observation_cannot_be_replaced_by_a_claimed_block(self):
        evidence = receipt()
        evidence["wfp_block_event_correlated"] = True
        self.assertFalse(review(evidence)["synthetic_diagnostics_valid"])
        self.assert_not_authorized(review(evidence))

    def test_missing_ipv6_layer_invalidates_diagnostic(self):
        evidence = receipt()
        evidence["wfp_observed_markers"].remove(
            "STAGE46_16_WFP_ALE_AUTH_CONNECT_V6_READ_ONLY_LAYER_PRESENT")
        self.assertFalse(review(evidence)["synthetic_diagnostics_valid"])

    def test_missing_ipv4_layer_invalidates_diagnostic(self):
        evidence = receipt()
        evidence["wfp_observed_markers"].remove(
            "STAGE46_16_WFP_ALE_AUTH_CONNECT_V4_READ_ONLY_LAYER_PRESENT")
        self.assertFalse(review(evidence)["synthetic_diagnostics_valid"])

    def test_engine_failure_cannot_be_downgraded_to_success(self):
        evidence = receipt()
        evidence["wfp_probe_exit_code"] = 5
        self.assertFalse(review(evidence)["synthetic_diagnostics_valid"])

    def test_duplicate_or_extra_wfp_claims_do_not_validate(self):
        evidence = receipt()
        evidence["wfp_observed_markers"].append(evidence["wfp_observed_markers"][0])
        self.assertFalse(review(evidence)["synthetic_diagnostics_valid"])
        evidence = receipt()
        evidence["wfp_observed_markers"].append("STAGE46_16_WFP_GLOBAL_EGRESS_CERTIFIED")
        self.assertFalse(review(evidence)["synthetic_diagnostics_valid"])

    def test_child_hash_tampering_invalidates_previous_trust_receipt(self):
        evidence = receipt()
        evidence["stage46_15_evidence"]["worker_sha256_after"] = HASH_B
        output = review(evidence)
        self.assertFalse(output["synthetic_diagnostics_valid"])
        self.assert_not_authorized(output)

    def test_parent_control_missing_invalidates_previous_evidence(self):
        evidence = receipt()
        evidence["stage46_15_evidence"]["observed_markers"].remove(
            "STAGE46_15_LOOPBACK_POSITIVE_CONTROL_CONNECTED")
        self.assertFalse(review(evidence)["synthetic_diagnostics_valid"])

    def test_conflicting_timeout_and_explicit_denial_invalidates_evidence(self):
        evidence = receipt()
        evidence["stage46_15_evidence"]["observed_markers"].append(EXPLICIT_DENY)
        self.assertFalse(review(evidence)["synthetic_diagnostics_valid"])

    def test_fake_successful_network_connection_marker_is_rejected(self):
        evidence = receipt()
        evidence["stage46_15_evidence"]["observed_markers"].append(
            "STAGE46_14_LOOPBACK_CHILD_CONNECTED")
        self.assertFalse(review(evidence)["synthetic_diagnostics_valid"])

    def test_schema_boolean_or_unbounded_markers_rejected(self):
        evidence = receipt()
        evidence["schema_version"] = True
        self.assertFalse(review(evidence)["synthetic_diagnostics_valid"])
        evidence = receipt()
        evidence["wfp_observed_markers"] = ["A" * 500]
        self.assertFalse(review(evidence)["synthetic_diagnostics_valid"])

    def test_malformed_thumbprint_or_receipt_rejected(self):
        evidence = receipt()
        evidence["publisher_certificate_sha256"] = "not-an-approved-signature"
        self.assertFalse(review(evidence)["synthetic_diagnostics_valid"])
        for malformed in (None, [], {}, {"schema_version": 1}):
            self.assertFalse(review(malformed)["synthetic_diagnostics_valid"])

    def test_pr_supplied_production_approval_rejected(self):
        evidence = receipt()
        evidence["production_authorized"] = True
        out = review(evidence)
        self.assertFalse(out["synthetic_diagnostics_valid"])
        self.assert_not_authorized(out)

    def test_unsigned_fixture_never_approves_microphone_even_with_explicit_loopback_denial(self):
        evidence = receipt()
        evidence["stage46_15_evidence"]["observed_markers"].remove(TIMEOUT)
        evidence["stage46_15_evidence"]["observed_markers"].append(EXPLICIT_DENY)
        out = review(evidence)
        self.assertTrue(out["synthetic_diagnostics_valid"])
        self.assert_not_authorized(out)


if __name__ == "__main__":
    unittest.main()
