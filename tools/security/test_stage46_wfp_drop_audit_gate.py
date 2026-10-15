"""Stage 46.17 adversarial tests: WFP observations are not correlated proof."""
from __future__ import annotations
import copy
import json
import unittest
from pathlib import Path

from stage46_wfp_drop_audit_gate import REQUIRED, SNAPSHOT_MARKERS, review
from test_stage46_wfp_provenance_gate import receipt as old_receipt


SPEC = (Path(__file__).resolve().parents[2] / "docs" / "runtime"
        / "stage46-trusted-signed-worker-release-spec.json")
DEFAULT = "STAGE46_17_WFP_SNAPSHOT_UNAVAILABLE"


def evidence(status=DEFAULT):
    return {
        "schema_version": 1,
        "stage46_16_evidence": old_receipt(),
        "wfp_drop_probe_exit_code": 0,
        "wfp_drop_observed_markers": sorted(REQUIRED | {status}),
    }


def spec():
    return json.loads(SPEC.read_text(encoding="utf-8"))


class Stage4617WfpDropAuditTest(unittest.TestCase):
    def assert_closed(self, report):
        self.assertEqual(report["decision"], "NO_GO")
        for name in ("app_specific_drop_verified", "network_egress_certified",
                     "binary_signature_approved", "release_provenance_approved",
                     "worker_install_authorized", "owner_pc_pilot_authorized",
                     "microphone_authorized", "desktop_actions_authorized",
                     "production_authorized"):
            self.assertIs(report[name], False, name)

    def test_no_wfp_event_permission_is_a_valid_no_go_diagnostic(self):
        result = review(evidence(), spec())
        self.assertTrue(result["synthetic_read_only_audit_valid"])
        self.assertEqual(result["wfp_snapshot_status"], "SNAPSHOT_UNAVAILABLE")
        self.assert_closed(result)

    def test_logged_classify_drops_do_not_prove_which_application_was_blocked(self):
        result = review(evidence("STAGE46_17_WFP_UNCORRELATED_DROP_SNAPSHOT_PRESENT"), spec())
        self.assertTrue(result["synthetic_read_only_audit_valid"])
        self.assert_closed(result)

    def test_disabled_wfp_logging_is_not_secretly_enabled_or_interpreted_as_block(self):
        result = review(evidence("STAGE46_17_WFP_EVENT_LOGGING_DISABLED"), spec())
        self.assertTrue(result["synthetic_read_only_audit_valid"])
        self.assert_closed(result)

    def test_empty_snapshot_is_not_evidence_of_all_network_traffic_blocked(self):
        result = review(evidence("STAGE46_17_WFP_SNAPSHOT_EMPTY"), spec())
        self.assertTrue(result["synthetic_read_only_audit_valid"])
        self.assert_closed(result)

    def test_missing_no_mutation_evidence_rejected(self):
        r = evidence()
        r["wfp_drop_observed_markers"].remove(
            "STAGE46_17_NO_WFP_FILTER_OR_AUDIT_CONFIGURATION_CHANGED")
        self.assertFalse(review(r, spec())["synthetic_read_only_audit_valid"])

    def test_contradictory_snapshot_statuses_rejected(self):
        r = evidence()
        r["wfp_drop_observed_markers"].append("STAGE46_17_WFP_SNAPSHOT_EMPTY")
        self.assertFalse(review(r, spec())["synthetic_read_only_audit_valid"])

    def test_forged_process_id_and_kernel_block_event_are_not_accepted(self):
        r = evidence()
        r["correlated_process_id"] = 424242
        self.assertFalse(review(r, spec())["synthetic_read_only_audit_valid"])
        r = evidence()
        r["wfp_drop_observed_markers"].append("STAGE46_17_APP_CONTAINER_BLOCK_VERIFIED")
        self.assertFalse(review(r, spec())["synthetic_read_only_audit_valid"])

    def test_network_and_release_approval_in_receipt_cannot_grant_release(self):
        r = evidence()
        r["production_authorized"] = True
        self.assertFalse(review(r, spec())["synthetic_read_only_audit_valid"])
        self.assert_closed(review(r, spec()))

    def test_unsigned_worker_cannot_be_approved_by_modifying_spec(self):
        s = spec()
        s["candidate_worker"]["approved_code_signing_leaf_certificate_sha256"] = "a"*64
        self.assertFalse(review(evidence(), s)["synthetic_read_only_audit_valid"])

    def test_fake_app_specific_match_in_spec_is_rejected(self):
        s = spec()
        s["required_network_evidence"]["per_process_app_identifier_verified"] = True
        self.assertFalse(review(evidence(), s)["synthetic_read_only_audit_valid"])

    def test_logging_claim_in_spec_rejected(self):
        s = spec()
        s["required_network_evidence"]["logging_available_and_complete"] = True
        self.assertFalse(review(evidence(), s)["synthetic_read_only_audit_valid"])

    def test_valid_signed_eku_claim_without_approved_artifact_rejected(self):
        s = spec()
        s["candidate_worker"]["code_signing_eku_verified"] = True
        self.assertFalse(review(evidence(), s)["synthetic_read_only_audit_valid"])

    def test_firewall_rule_mutation_claim_in_spec_rejected(self):
        s = spec()
        s["enforcement_scope"]["installed_firewall_rules"] = True
        self.assertFalse(review(evidence(), s)["synthetic_read_only_audit_valid"])

    def test_preserves_prior_16_no_go_and_non_correlated_release_policy(self):
        e = evidence()
        e["stage46_16_evidence"]["wfp_observed_markers"].remove(
            "STAGE46_16_NO_CORRELATED_BLOCK_EVENT_EGRESS_UNVERIFIED")
        self.assertFalse(review(e, spec())["synthetic_read_only_audit_valid"])

    def test_snapshot_probe_failures_are_not_silently_accepted(self):
        r = evidence()
        r["wfp_drop_probe_exit_code"] = 9
        self.assertFalse(review(r, spec())["synthetic_read_only_audit_valid"])

    def test_extra_event_log_contains_no_user_identifiers_and_is_rejected(self):
        r = evidence()
        r["wfp_drop_observed_markers"].append("user@example.test 10.0.0.1")
        self.assertFalse(review(r, spec())["synthetic_read_only_audit_valid"])

    def test_malformed_receipts_schema_and_duplicate_markers_rejected(self):
        self.assertFalse(review(None, spec())["synthetic_read_only_audit_valid"])
        r = evidence()
        r["schema_version"] = True
        self.assertFalse(review(r, spec())["synthetic_read_only_audit_valid"])
        r = evidence()
        r["wfp_drop_observed_markers"].append(DEFAULT)
        self.assertFalse(review(r, spec())["synthetic_read_only_audit_valid"])

    def test_spec_is_explicitly_synthetic_and_release_is_no_go(self):
        s = spec()
        self.assertEqual(s["release_decision"], "NO_GO")
        self.assertEqual(s["artifact_class"], "SYNTHETIC_TEST_ONLY")
        self.assertFalse(s["enforcement_scope"]["real_microphone_enabled"])
        self.assert_closed(review(evidence(), s))


if __name__ == "__main__":
    unittest.main()
