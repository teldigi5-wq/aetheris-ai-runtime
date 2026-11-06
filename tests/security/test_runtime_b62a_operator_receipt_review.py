"""Offline regressions for S-B62-A receipt content integrity; never release GO."""
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "scripts/security"))
import runtime_b62_operator_fencing_preflight as base
import runtime_b62a_operator_receipt_review as check


def template():
    return {"schema": base.SCHEMA, "scope": "operator-redacted-review",
            "runtime_commit": "a" * 40, "platform_commit": "b" * 40,
            "release_decision": "NO_GO",
            "roles": {name: {"host_domain": f"host-{i}", "admin_domain": f"admin-{i}"}
                      for i, name in enumerate(base.ROLES)},
            "checks": {name: {"result": "NOT_RUN", "evidence_sha256": None}
                       for name in base.CHECKS}}


def add_receipt(manifest, directory, check_name, payload=b"SANITIZED ARTIFICIAL REVIEW FIXTURE\n"):
    target = directory / (check_name + check.SUFFIX)
    target.write_bytes(payload)
    manifest["checks"][check_name] = {
        "result": "DENIED", "evidence_sha256": hashlib.sha256(payload).hexdigest()}
    return target


@unittest.skipUnless(os.name == "posix", "secure directory-relative open is POSIX/WSL-only")
class ReceiptReview(unittest.TestCase):
    def test_matching_synthetic_receipts_never_certify(self):
        with tempfile.TemporaryDirectory() as td:
            d = Path(td)
            m = template()
            for name in base.CHECKS:
                add_receipt(m, d, name, name.encode())
            result = check.review_receipts(m, d)
            self.assertTrue(result["local_receipt_hashes_match"])
            self.assertEqual(result["receipt_files_checked"], len(base.CHECKS))
            self.assertFalse(result["independent_review_verified"])
            self.assertFalse(result["physical_fencing_certified"])
            self.assertEqual(result["release_decision"], "STAGE45_NO_GO")

    def test_blank_receipts_do_not_claim_match(self):
        with tempfile.TemporaryDirectory() as td:
            result = check.review_receipts(template(), Path(td))
            self.assertFalse(result["local_receipt_hashes_match"])
            self.assertEqual(result["receipt_files_checked"], 0)

    def test_tamper_after_hash_is_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            d = Path(td)
            m = template()
            target = add_receipt(m, d, base.CHECKS[0])
            target.write_bytes(b"altered evidence")
            with self.assertRaises(base.InventoryError):
                check.review_receipts(m, d)

    def test_missing_and_extra_files_are_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            d = Path(td)
            m = template()
            path = add_receipt(m, d, base.CHECKS[0])
            path.unlink()
            with self.assertRaises(base.InventoryError):
                check.review_receipts(m, d)
            m = template()
            (d / (base.CHECKS[0] + check.SUFFIX)).write_bytes(b"not run")
            with self.assertRaises(base.InventoryError):
                check.review_receipts(m, d)

    def test_symlinks_are_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            d = Path(td)
            inner = d / "receipts"
            inner.mkdir()
            m = template()
            target = add_receipt(m, inner, base.CHECKS[0])
            target.unlink()
            target.symlink_to(d / "external")
            with self.assertRaises((base.InventoryError, OSError)):
                check.review_receipts(m, inner)
            directory_link = d / "directory-link"
            directory_link.symlink_to(inner, target_is_directory=True)
            with self.assertRaises((base.InventoryError, OSError)):
                check.review_receipts(m, directory_link)

    def test_hardlink_and_oversize_are_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            d = Path(td)
            m = template()
            target = add_receipt(m, d, base.CHECKS[0])
            os.link(target, d / "hardlink")
            fd = check._open_checked_dir(d)
            try:
                with self.assertRaises(base.InventoryError):
                    check._hash_checked_receipt(fd, target.name)
            finally:
                os.close(fd)
            (d / "hardlink").unlink()
            target.write_bytes(b"x" * (check.MAX_RECEIPT_BYTES + 1))
            with self.assertRaises(base.InventoryError):
                check.review_receipts(m, d)

    def test_regular_file_swap_at_open_is_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            d = Path(td)
            m = template()
            target = add_receipt(m, d, base.CHECKS[0])
            replacement = d / "replacement"
            replacement.write_bytes(target.read_bytes())
            fd = check._open_checked_dir(d)
            original_open = check.os.open
            def swap(path, flags, **kw):
                os.replace(replacement, target)
                return original_open(path, flags, **kw)
            try:
                with patch.object(check.os, "open", side_effect=swap):
                    with self.assertRaises(base.InventoryError):
                        check._hash_checked_receipt(fd, target.name)
            finally:
                os.close(fd)

    def test_cli_full_hash_match_returns_nonzero(self):
        with tempfile.TemporaryDirectory() as td:
            d = Path(td)
            receipts = d / "receipts"
            receipts.mkdir()
            manifest = template()
            for name in base.CHECKS:
                add_receipt(manifest, receipts, name)
            path = d / "manifest.json"
            path.write_text(json.dumps(manifest), encoding="utf-8")
            out = io.StringIO()
            with patch("sys.argv", ["review", "--manifest", str(path),
                                    "--receipts-dir", str(receipts)]), \
                    contextlib.redirect_stdout(out):
                code = check.main()
            self.assertEqual(code, 1)
            self.assertEqual(json.loads(out.getvalue())["release_decision"], "STAGE45_NO_GO")


if __name__ == "__main__":
    unittest.main()
