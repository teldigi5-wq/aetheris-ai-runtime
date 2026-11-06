"""Offline fail-closed adversarial tests for S-B62-A inventory structure."""
import importlib.util
import json
import contextlib
import io
from unittest.mock import patch
from pathlib import Path
import tempfile
import unittest

FILE = Path(__file__).resolve().parents[2] / "scripts/security/runtime_b62_operator_fencing_preflight.py"
spec = importlib.util.spec_from_file_location("operator_inventory", FILE)
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


def template():
    return {
        "schema": m.SCHEMA, "scope": "operator-redacted-review",
        "runtime_commit": "a" * 40, "platform_commit": "b" * 40,
        "release_decision": "NO_GO",
        "roles": {role: {"host_domain": f"zone-{i}", "admin_domain": f"actor-{i}"}
                  for i, role in enumerate(m.ROLES)},
        "checks": {name: {"result": "NOT_RUN", "evidence_sha256": None}
                   for name in m.CHECKS},
    }


class S_B62_A_Preflight(unittest.TestCase):
    def test_blank_operator_inventory_remains_blocked(self):
        result = m.preflight(template())
        self.assertEqual(result["preflight"], "BLOCKED")
        self.assertEqual(result["release_decision"], "STAGE45_NO_GO")

    def test_even_perfect_self_report_never_authorizes_release(self):
        source = template()
        for item in source["checks"].values():
            item.update(result="DENIED", evidence_sha256="c" * 64)
        result = m.preflight(source)
        self.assertEqual(result["preflight"], "STRUCTURALLY_COMPLETE_UNVERIFIED")
        self.assertFalse(result["independent_review_verified"])
        self.assertFalse(result["physical_fencing_certified"])
        self.assertEqual(result["xerj_publication"], "HOLD")

    def test_cli_perfect_self_report_never_exits_zero(self):
        source = template()
        for item in source["checks"].values():
            item.update(result="DENIED", evidence_sha256="c" * 64)
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "inventory.json"
            src.write_text(json.dumps(source), encoding="utf-8")
            out = io.StringIO()
            with patch("sys.argv", ["inventory", "--manifest", str(src)]), \
                 contextlib.redirect_stdout(out):
                exit_status = m.main()
            self.assertEqual(exit_status, 1)
            value = json.loads(out.getvalue())
            self.assertEqual(value["release_decision"], "STAGE45_NO_GO")
            self.assertFalse(value["physical_fencing_certified"])

    def test_replaying_old_primary_write_blocks(self):
        source = template()
        source["checks"]["old_primary_write_after_promotion"].update(
            result="ALLOWED", evidence_sha256="d" * 64)
        self.assertIn("old_primary_write_after_promotion",
                      m.preflight(source)["unverified_or_failed_checks"])

    def test_privileged_witness_rollback_blocks(self):
        source = template()
        source["checks"]["privileged_witness_epoch_rollback"].update(
            result="ALLOWED", evidence_sha256="d" * 64)
        self.assertIn("privileged_witness_epoch_rollback",
                      m.preflight(source)["unverified_or_failed_checks"])

    def test_same_host_and_admin_domains_block(self):
        source = template()
        source["roles"]["external_fencer"] = source["roles"]["old_primary"].copy()
        checks = m.preflight(source)["unverified_or_failed_checks"]
        self.assertIn("shared_host_failure_domain", checks)
        self.assertIn("shared_administrative_authority", checks)

    def test_release_yes_rejected(self):
        source = template()
        source["release_decision"] = "GO"
        with self.assertRaises(m.InventoryError):
            m.preflight(source)

    def test_secret_field_rejected(self):
        source = template()
        source["db_password"] = "not-a-real-password"
        with self.assertRaises(m.InventoryError):
            m.preflight(source)

    def test_ip_address_in_host_field_rejected(self):
        source = template()
        source["roles"]["old_primary"]["host_domain"] = "10.0.0.1"
        with self.assertRaises(m.InventoryError):
            m.preflight(source)

    def test_missing_hash_rejected(self):
        source = template()
        source["checks"]["old_primary_write_after_promotion"]["result"] = "DENIED"
        with self.assertRaises(m.InventoryError):
            m.preflight(source)

    def test_unsafe_hash_rejected(self):
        source = template()
        source["checks"]["old_primary_write_after_promotion"].update(
            result="DENIED", evidence_sha256="http://example.com/log")
        with self.assertRaises(m.InventoryError):
            m.preflight(source)

    def test_duplicate_json_keys_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "inventory.json"
            src.write_text('{"schema":"a", "schema":"b"}', encoding="utf-8")
            with self.assertRaises(m.InventoryError):
                m.parse_manifest(src)

    def test_symlink_and_large_manifest_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "inventory.json"
            src.write_bytes(b"x" * 20_000)
            with self.assertRaises(m.InventoryError):
                m.parse_manifest(src)
            link = Path(tmp) / "link.json"
            link.symlink_to(src)
            with self.assertRaises(m.InventoryError):
                m.parse_manifest(link)

    def test_valid_json_local_read_does_not_echo_values(self):
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "inventory.json"
            src.write_text(json.dumps(template()), encoding="utf-8")
            parsed = m.parse_manifest(src)
            self.assertEqual(m.preflight(parsed)["preflight"], "BLOCKED")


if __name__ == "__main__":
    unittest.main()
