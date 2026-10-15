"""Adversarial synthetic WFP attribution contract; never authentic kernel data."""
import copy
import unittest
from stage46_wfp_synthetic_match_contract import review

APP = "a" * 64
OTHER = "b" * 64


def sample(v6=False):
    ip = "::1" if v6 else "127.0.0.1"
    version = 6 if v6 else 4
    shared = {
        "app_id_sha256": APP, "destination_ip": ip,
        "destination_port": 49155, "ip_version": version,
        "protocol": "TCP", "direction": "OUTBOUND",
    }
    return {
        "schema_version": 1, "fixture_kind": "SYNTHETIC_NO_REAL_WFP_DATA",
        "clock_kind": "SYNTHETIC_RELATIVE_MS",
        "positive_control_connected": True, "kernel_event_attested": False,
        "attempt": {**shared, "start_ms": 200, "end_ms": 400,
                    "expected_filter_id": 202, "expected_layer_id": 44},
        "candidate_events": [{**shared, "event_id": "fixture-1",
                              "timestamp_ms": 300, "filter_id": 202,
                              "layer_id": 44, "event_type": "CLASSIFY_DROP",
                              "event_source": "SYNTHETIC_FIXTURE_ONLY"}],
    }


class Stage4618SyntheticCorrelationContractTest(unittest.TestCase):
    def assert_no_real_authority(self, value):
        self.assertEqual(value["decision"], "NO_GO")
        for flag in ("real_kernel_event_provenance_verified",
                     "real_app_specific_wfp_drop_verified",
                     "network_egress_certified", "worker_install_authorized",
                     "owner_pc_pilot_authorized", "production_authorized"):
            self.assertIs(value[flag], False, flag)

    def test_exact_ipv4_fixture_matches_without_authenticating_kernel_events(self):
        value = review(sample())
        self.assertTrue(value["contract_valid"])
        self.assertEqual(value["synthetic_correlation"], "EXACTLY_ONE_SYNTHETIC_MATCH")
        self.assertEqual(value["synthetic_match_count"], 1)
        self.assert_no_real_authority(value)

    def test_exact_ipv6_fixture_is_supported_but_not_a_kernel_proof(self):
        value = review(sample(v6=True))
        self.assertTrue(value["contract_valid"])
        self.assertEqual(value["synthetic_match_count"], 1)
        self.assert_no_real_authority(value)

    def test_wrong_app_digest_does_not_match(self):
        data = sample()
        data["candidate_events"][0]["app_id_sha256"] = OTHER
        self.assertEqual(review(data)["synthetic_correlation"], "NO_MATCH")

    def test_wrong_ephemeral_port_does_not_match(self):
        data = sample()
        data["candidate_events"][0]["destination_port"] = 49156
        self.assertEqual(review(data)["synthetic_correlation"], "NO_MATCH")

    def test_wrong_destination_address_does_not_match(self):
        data = sample()
        data["candidate_events"][0]["destination_ip"] = "127.0.0.2"
        self.assertEqual(review(data)["synthetic_match_count"], 0)

    def test_wrong_filter_or_layer_does_not_match(self):
        for key, new in (("filter_id", 203), ("layer_id", 45)):
            with self.subTest(field=key):
                data = sample()
                data["candidate_events"][0][key] = new
                self.assertEqual(review(data)["synthetic_match_count"], 0)

    def test_timestamps_outside_exact_bounded_window_do_not_match(self):
        for stamp in (199, 401):
            with self.subTest(stamp=stamp):
                data = sample()
                data["candidate_events"][0]["timestamp_ms"] = stamp
                self.assertEqual(review(data)["synthetic_match_count"], 0)

    def test_window_boundaries_are_inclusive(self):
        for stamp in (200, 400):
            data = sample()
            data["candidate_events"][0]["timestamp_ms"] = stamp
            self.assertEqual(review(data)["synthetic_match_count"], 1)

    def test_attempt_rejects_windows_longer_than_five_seconds(self):
        data = sample()
        data["attempt"]["end_ms"] = 5_201
        self.assertFalse(review(data)["contract_valid"])

    def test_zero_duration_window_rejected(self):
        data = sample()
        data["attempt"]["end_ms"] = 200
        self.assertFalse(review(data)["contract_valid"])

    def test_untrusted_kernel_event_attestation_cannot_be_inserted(self):
        data = sample()
        data["kernel_event_attested"] = True
        value = review(data)
        self.assertFalse(value["contract_valid"])
        self.assert_no_real_authority(value)

    def test_missing_positive_control_rejected(self):
        data = sample()
        data["positive_control_connected"] = False
        self.assertFalse(review(data)["contract_valid"])

    def test_event_source_cannot_be_misrepresented_as_real_wfp(self):
        data = sample()
        data["candidate_events"][0]["event_source"] = "REAL_KERNEL"
        self.assertFalse(review(data)["contract_valid"])

    def test_extra_claim_of_network_enforcement_fails_schema(self):
        data = sample()
        data["network_egress_certified"] = True
        self.assertFalse(review(data)["contract_valid"])

    def test_wrong_event_type_cannot_be_called_a_drop(self):
        data = sample()
        data["candidate_events"][0]["event_type"] = "ALLOW"
        self.assertFalse(review(data)["contract_valid"])

    def test_multiple_matching_distinct_events_cannot_establish_unique_correlation(self):
        data = sample()
        second = copy.deepcopy(data["candidate_events"][0])
        second["event_id"] = "fixture-2"
        data["candidate_events"].append(second)
        value = review(data)
        self.assertTrue(value["contract_valid"])
        self.assertEqual(value["synthetic_correlation"], "AMBIGUOUS_MULTIPLE_MATCHES")
        self.assert_no_real_authority(value)

    def test_duplicate_event_id_is_rejected(self):
        data = sample()
        data["candidate_events"].append(copy.deepcopy(data["candidate_events"][0]))
        self.assertFalse(review(data)["contract_valid"])

    def test_more_than_sixteen_events_are_rejected(self):
        data = sample()
        for i in range(2, 19):
            extra = copy.deepcopy(data["candidate_events"][0])
            extra["event_id"] = f"fixture-{i}"
            data["candidate_events"].append(extra)
        self.assertFalse(review(data)["contract_valid"])

    def test_oversized_or_noncanonical_identity_is_rejected(self):
        for digest in ("a" * 63, "a" * 65, "A" * 64, "", "a" * 63 + "z"):
            with self.subTest(digest=digest[:10]):
                data = sample()
                data["attempt"]["app_id_sha256"] = digest
                self.assertFalse(review(data)["contract_valid"])

    def test_nonlocal_ip_cannot_be_used_by_this_offline_fixture(self):
        data = sample()
        data["attempt"]["destination_ip"] = "8.8.8.8"
        self.assertFalse(review(data)["contract_valid"])

    def test_wrong_ip_version_rejected_not_coerced(self):
        data = sample()
        data["candidate_events"][0]["ip_version"] = 6
        self.assertFalse(review(data)["contract_valid"])

    def test_boolean_timestamp_and_port_rejected(self):
        for key in ("destination_port", "timestamp_ms", "layer_id"):
            data = sample()
            data["candidate_events"][0][key] = True
            self.assertFalse(review(data)["contract_valid"])

    def test_empty_event_list_is_valid_no_match_and_no_release(self):
        data = sample()
        data["candidate_events"] = []
        result = review(data)
        self.assertTrue(result["contract_valid"])
        self.assertEqual(result["synthetic_correlation"], "NO_MATCH")
        self.assert_no_real_authority(result)

    def test_trailing_pii_fields_rejected(self):
        data = sample()
        data["candidate_events"][0]["owner_email"] = "synthetic@example.test"
        self.assertFalse(review(data)["contract_valid"])

    def test_untrusted_json_types_rejected(self):
        for data in (None, {}, [], "string", {"schema_version": 1}):
            self.assertFalse(review(data)["contract_valid"])
            self.assert_no_real_authority(review(data))


if __name__ == "__main__":
    unittest.main()
