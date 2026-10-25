"""E3.9G-12B4H synthetic admission contract and hostile receipt regressions."""
from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile

import e39g12b4g_curator_boundary as curator
import e39g12b4h_proposal_admission as h
from test_e39g12b4g_curator_boundary import fixture


def staged(base: Path):
    root = base / "private-curator"
    root.mkdir()
    fixture(root)
    bundle = base / "public-only.zip"
    curator.stage(root, bundle)
    public, _ = curator.inspect(root)
    return root, bundle, public


def receipt_for(bundle: Path, entries: list, changes=None):
    trials = []
    for item in entries:
        i = int(item["id"].split("-")[1])
        result = {
            "site": item["site"], "fault": item["fault"], "operation": "replace_one",
            "old": f"broken-{i}", "new": f"corrected-{i}",
            "sourceSha256": item["brokenSha256"],
        }
        raw = json.dumps(result, separators=(",", ":"))
        trials.append({
            "caseId": item["id"], "sourceSha256": item["brokenSha256"],
            "rawModelResponse": raw, "rawResponseSha256": curator.digest(raw.encode())
        })
    data = {
        "evidenceClass": "E39G12B4H_UNTRUSTED_MODEL_PROPOSALS",
        "sourceBundleSha256": curator.digest(bundle.read_bytes()),
        "modelTag": "synthetic-reply-test-only",
        "modelCallsSelfReported": len(trials),
        "ownerApprovalGranted": False, "goldenReferenceAvailable": False,
        "modelOutputExecuted": False, "trials": trials,
    }
    if changes:
        changes(data)
    return data


def write_receipt(base: Path, value) -> Path:
    path = base / "untrusted-replies.json"
    path.write_text(json.dumps(value), encoding="utf-8")
    return path


def refresh_row(row, value):
    raw = json.dumps(value, separators=(",", ":"))
    row["rawModelResponse"] = raw
    row["rawResponseSha256"] = curator.digest(raw.encode())


class StrictAdmissionTest(unittest.TestCase):
    def test_valid_source_quote_remains_review_only_and_never_applied(self):
        with tempfile.TemporaryDirectory() as d:
            base = Path(d)
            root, bundle, public = staged(base)
            receipt = write_receipt(base, receipt_for(bundle, public))
            report = h.admit(root, bundle, receipt)
            self.assertEqual(report["caseCount"], 4)
            self.assertEqual(report["categories"], {"QUOTE_MATCH_REVIEW_REQUIRED": 4})
            self.assertEqual(report["patchesApplied"], 0)
            self.assertEqual(report["policyAcceptedPatches"], 0)
            self.assertEqual(report["browserVerifiedRepairs"], 0)
            self.assertIs(report["independentCuratorVerified"], False)
            self.assertIs(report["modelExecutionVerified"], False)
            self.assertIs(report["stage45Go"], False)
            self.assertIs(report["ownerApprovalGranted"], False)
            self.assertIs(report["productionAuthorized"], False)
            self.assertIn("broken-0", (root / "cases/case-00/broken.html").read_text())
            out = base / "admission-only.json"
            h.save_report(report, out, root)
            self.assertTrue(out.exists())
            self.assertNotIn("corrected-0", out.read_text())
            with self.assertRaisesRegex(ValueError, "B4H_REPORT_SCOPE_OR_REPLAY"):
                h.save_report(report, out, root)

    def test_rejects_forged_owner_authority_and_unknown_case(self):
        with tempfile.TemporaryDirectory() as d:
            base = Path(d)
            root, bundle, public = staged(base)
            data = receipt_for(bundle, public)
            data["ownerApprovalGranted"] = True
            with self.assertRaisesRegex(ValueError, "B4H_UNTRUSTED_RECEIPT_CONTRACT"):
                h.admit(root, bundle, write_receipt(base, data))
            data["ownerApprovalGranted"] = False
            data["trials"][0]["caseId"] = "../private"
            with self.assertRaisesRegex(ValueError, "B4H_DUPLICATE_OR_UNKNOWN_CASE_ID"):
                h.admit(root, bundle, write_receipt(base, data))

    def test_invalid_or_duplicate_literal_is_denied(self):
        with tempfile.TemporaryDirectory() as d:
            base = Path(d)
            root, bundle, public = staged(base)
            data = receipt_for(bundle, public)
            row = data["trials"][0]
            raw = json.loads(row["rawModelResponse"])
            raw["old"] = "DOES_NOT_EXIST"
            refresh_row(row, raw)
            report = h.admit(root, bundle, write_receipt(base, data))
            self.assertEqual(report["categories"]["SOURCE_QUOTE_NOT_UNIQUE"], 1)
            self.assertEqual(report["categories"]["QUOTE_MATCH_REVIEW_REQUIRED"], 3)
            self.assertEqual(report["policyAcceptedPatches"], 0)

    def test_bad_case_identity_or_content_digest_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            base = Path(d)
            root, bundle, public = staged(base)
            data = receipt_for(bundle, public)
            row = data["trials"][0]
            raw = json.loads(row["rawModelResponse"])
            raw["fault"] = "unrelated-fault"
            refresh_row(row, raw)
            row = data["trials"][1]
            raw = json.loads(row["rawModelResponse"])
            raw["sourceSha256"] = "0" * 64
            refresh_row(row, raw)
            report = h.admit(root, bundle, write_receipt(base, data))
            self.assertEqual(report["categories"]["CASE_IDENTITY_REJECTED"], 1)
            self.assertEqual(report["categories"]["SOURCE_DIGEST_REJECTED"], 1)

    def test_tampered_model_response_digest_rejected_before_examining_code(self):
        with tempfile.TemporaryDirectory() as d:
            base = Path(d)
            root, bundle, public = staged(base)
            data = receipt_for(bundle, public)
            data["trials"][0]["rawResponseSha256"] = "0" * 64
            with self.assertRaisesRegex(ValueError, "B4H_UNTRUSTED_ROW_DRIFT"):
                h.admit(root, bundle, write_receipt(base, data))

    def test_duplicate_json_fields_and_new_scripting_never_gain_authority(self):
        with tempfile.TemporaryDirectory() as d:
            base = Path(d)
            root, bundle, public = staged(base)
            data = receipt_for(bundle, public)
            row = data["trials"][0]
            raw = json.loads(row["rawModelResponse"])
            raw["new"] = "<script>alert('untrusted')</script>"
            refresh_row(row, raw)
            report = h.admit(root, bundle, write_receipt(base, data))
            # Quote validity MUST NEVER authorize unsafe content execution.
            self.assertEqual(report["results"][0]["outcome"], "QUOTE_MATCH_REVIEW_REQUIRED")
            self.assertEqual(report["policyAcceptedPatches"], 0)
            self.assertEqual(report["patchesApplied"], 0)
            row["rawModelResponse"] = '{"site":"a","site":"b"}'
            row["rawResponseSha256"] = curator.digest(row["rawModelResponse"].encode())
            report = h.admit(root, bundle, write_receipt(base, data))
            self.assertEqual(report["categories"]["SCHEMA_REJECTED"], 1)

    def test_rejects_reference_mutation_and_failed_public_commitment(self):
        with tempfile.TemporaryDirectory() as d:
            base = Path(d)
            root, bundle, public = staged(base)
            receipt = write_receipt(base, receipt_for(bundle, public))
            private = root / "cases/case-00/reference.html"
            private.write_text(private.read_text().replace("corrected", "injected"))
            with self.assertRaisesRegex(ValueError, "B4G_PRIVATE_EVIDENCE_DRIFT"):
                h.admit(root, bundle, receipt)

    def test_rejects_untrusted_receipt_inside_private_curator_root(self):
        with tempfile.TemporaryDirectory() as d:
            base = Path(d)
            root, bundle, public = staged(base)
            private_receipt = root / "bad-model-receipt.json"
            private_receipt.write_text(json.dumps(receipt_for(bundle, public)))
            with self.assertRaisesRegex(ValueError, "B4H_UNTRUSTED_RECEIPT_LOCATION"):
                h.admit(root, bundle, private_receipt)

    def test_abstention_is_not_success(self):
        with tempfile.TemporaryDirectory() as d:
            base = Path(d)
            root, bundle, public = staged(base)
            data = receipt_for(bundle, public)
            row = data["trials"][0]
            refresh_row(row, {"operation": "abstain"})
            report = h.admit(root, bundle, write_receipt(base, data))
            self.assertEqual(report["categories"]["MODEL_ABSTAINED"], 1)
            self.assertEqual(report["browserVerifiedRepairs"], 0)


if __name__ == "__main__":
    unittest.main()
