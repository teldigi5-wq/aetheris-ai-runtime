"""Adversarial tests for offline B4E analysis; no model, network or project edits."""
from __future__ import annotations
import io
import json
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

import e39g12b4e_failure_taxonomy as audit


ACTUAL_DENIALS = {
    "original": (
        "E39G12B2_OLD_NOT_UNIQUE_AUTHORIZED_LITERAL",
        "E39G12B2_OLD_NOT_UNIQUE_AUTHORIZED_LITERAL",
        "E39G12B2_UNTRUSTED_SCHEMA_WRONG",
        "E39G12B2_OLD_NOT_UNIQUE_AUTHORIZED_LITERAL",
    ),
    "source-span": (
        "E39G12B2_FOCUS_NOT_VISIBLE_BOUNDED",
        "E39G12B2_OLD_NOT_UNIQUE_AUTHORIZED_LITERAL",
        "E39G12B4A_CROSS_FAULT_PROPOSAL_DENIED",
        "E39G12B2_DOM_HTML_SINK_NOT_REMOVED",
    ),
}


def build(tmp: Path, mutate=None, extra=None) -> Path:
    comparison = {
        "evidenceClass": "E39G12B4D_TWO_ARM_MODEL_REPAIR_COMPARISON",
        "modelTag": "qwen2.5-coder:7b",
        "realModelCallsFromUntrustedReceipt": 8,
        "genuinelyHeldOut": False,
        "stage45Go": False,
        "autonomousWebsiteFactoryCertified": False,
        "arms": {
            arm: {"policyAccepted": 0, "browserVerifiedRepairs": 0,
                  "perFault": [{"token": case, "accepted": False, "browserVerified": False}
                               for case in audit.CASES]}
            for arm in audit.ARMS
        },
    }
    fields = {"comparison.json": comparison}
    for arm in audit.ARMS:
        for case, reason in zip(audit.CASES, ACTUAL_DENIALS[arm]):
            fields[f"evaluated/{arm}/repairs/{case}/patch.json"] = {
                "site": case.split("--", 1)[0],
                "fault": case.split("--", 1)[1],
                "policyOutcome": "POLICY_REJECTED",
                "reason": reason,
                "ownerApprovalGranted": False,
                "stage45Go": False,
            }
    if mutate:
        mutate(fields)
    if extra:
        fields.update(extra)
    target = tmp / "reference.zip"
    with ZipFile(target, "w") as archive:
        for name, value in fields.items():
            archive.writestr(name, json.dumps(value))
    return target


class EvidenceTaxonomyTest(unittest.TestCase):
    def test_actual_eight_denial_classes_are_grounded_without_score_inflation(self):
        with tempfile.TemporaryDirectory() as tmp:
            report = audit.summarize_archive(build(Path(tmp)))
        original = report["arms"]["original"]
        assisted = report["arms"]["source-span"]
        self.assertEqual(original["denialCategories"], {"literal_targeting": 3, "schema_contract": 1})
        self.assertEqual(assisted["denialCategories"], {
            "literal_targeting": 1,
            "semantic_ineffective": 1,
            "semantic_incomplete": 1,
            "wrong_fault": 1,
        })
        self.assertEqual(sum(v["policyAccepted"] for v in report["arms"].values()), 0)
        self.assertEqual(sum(v["browserVerifiedRepairs"] for v in report["arms"].values()), 0)
        for k in ("stage45Go", "autonomousRepairCertified", "newAgentPermissionGranted",
                  "policyChanged", "genuinelyHeldOut", "modelOutputExecuted"):
            self.assertIs(report[k], False, k)
        self.assertEqual(report["modelCallsPerformed"], 0)

    def test_missing_evidence_fails_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            def remove(d):
                d.pop("evaluated/source-span/repairs/cityline--safe-untrusted-text/patch.json")
            with self.assertRaisesRegex(ValueError, "B4E_MISSING_OR_WRONG_EVIDENCE"):
                audit.summarize_archive(build(Path(tmp), remove))

    def test_owner_approval_spoof_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            def spoof(d):
                d["evaluated/original/repairs/cityline--reduced-motion/patch.json"]["ownerApprovalGranted"] = True
            with self.assertRaisesRegex(ValueError, "B4E_POLICY_AUTHORITY_DRIFT"):
                audit.summarize_archive(build(Path(tmp), spoof))

    def test_unknown_reason_and_forged_score_fail(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            def unknown(d):
                d["evaluated/original/repairs/cityline--reduced-motion/patch.json"]["reason"] = "UNRECOGNIZED_SUCCESS"
            with self.assertRaisesRegex(ValueError, "B4E_UNRECOGNIZED_POLICY_REASON"):
                audit.summarize_archive(build(root, unknown))
            def inflate(d):
                d["comparison.json"]["arms"]["source-span"]["policyAccepted"] = 4
            with self.assertRaisesRegex(ValueError, "B4E_SCORER_VS_REPAIR_MISMATCH"):
                audit.summarize_archive(build(root, inflate))

    def test_bad_path_is_rejected_even_if_not_read(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(ValueError, "B4E_UNSAFE_ZIP_MEMBER"):
                audit.summarize_archive(build(Path(tmp), extra={"../secrets.txt": {"x": 1}}))

    def test_wrong_token_or_case_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            def corrupt(d):
                d["evaluated/source-span/repairs/studynest--keyboard-focus-visibility/patch.json"]["site"] = "cityline"
            with self.assertRaisesRegex(ValueError, "B4E_WRONG_CASE_MAPPING"):
                audit.summarize_archive(build(Path(tmp), corrupt))


if __name__ == "__main__":
    unittest.main()
