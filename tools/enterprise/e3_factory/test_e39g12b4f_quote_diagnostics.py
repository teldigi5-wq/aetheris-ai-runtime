"""Deterministic adversarial B4F diagnostics; synthetic only, never model execution."""
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile

import e39g12b4f_quote_diagnostics as diagnostic
import e39g12b4e_failure_taxonomy as evidence
import e39g12b2_typed_policy as frozen


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def synthetic_archive(folder, mutate=None):
    entries = {}
    comparisons = {}
    for arm in evidence.ARMS:
        trials = {}
        summaries = []
        for token in evidence.CASES:
            site, fault = token.split("--", 1)
            canonical_old = frozen.RULES[(site, fault)][0]
            source = f"<html><body>OTHER_LIT<style>{canonical_old}</style></body></html>".encode()
            default_old = canonical_old
            response = {
                "site": site, "fault": fault, "operation": "replace_one",
                "old": default_old, "new": "INERT_NEW_VALUE",
                "sourceSha256": sha(source),
            }
            if arm == "original":
                if fault == "reduced-motion":
                    response.pop("sourceSha256")
                else:
                    response["old"] = "OTHER_LIT"
            else:
                if fault == "responsive-mobile-overflow":
                    response["old"] = "UNKNOWN_QUOTE"
                elif fault == "reduced-motion":
                    response["fault"] = "safe-untrusted-text"
            raw = json.dumps(response)
            trials[token] = {
                "site": site, "fault": fault, "modelTag": "qwen2.5-coder:7b",
                "sourceSha256": sha(source),
                "rawModelResponse": raw, "rawResponseSha256": sha(raw.encode()),
            }
            reason = {
                ("original", "reduced-motion"): "E39G12B2_UNTRUSTED_SCHEMA_WRONG",
                ("source-span", "reduced-motion"): "E39G12B4A_CROSS_FAULT_PROPOSAL_DENIED",
                ("source-span", "responsive-mobile-overflow"):
                    "E39G12B2_OLD_NOT_UNIQUE_AUTHORIZED_LITERAL",
                ("source-span", "keyboard-focus-visibility"):
                    "E39G12B2_FOCUS_NOT_VISIBLE_BOUNDED",
                ("source-span", "safe-untrusted-text"): "E39G12B2_DOM_HTML_SINK_NOT_REMOVED",
            }.get((arm, fault), "E39G12B2_OLD_NOT_UNIQUE_AUTHORIZED_LITERAL")
            patch = {
                "site": site, "fault": fault, "policyOutcome": "POLICY_REJECTED",
                "reason": reason, "patchApplied": False,
                "sourceBeforeSha256": sha(source), "sourceAfterSha256": sha(source),
                "modelRawSha256": sha(raw.encode()),
                "stage45Go": False, "ownerApprovalGranted": False,
            }
            base = f"evaluated/{arm}/repairs/{token}"
            entries[base+"/patch.json"] = json.dumps(patch).encode()
            entries[base+"/index.html"] = source
            summaries.append({"token": token, "accepted": False, "browserVerified": False})
        incoming = {
            "evidenceClass": "E39G12B4A_FOUR_REAL_LOCAL_OLLAMA_COMPLETIONS",
            "modelTag": "qwen2.5-coder:7b", "realModelCalls": 4,
            "modelTextExecuted": False, "ownerApprovalGranted": False,
            "productionPublished": False, "goldenReferenceAvailable": False,
            "trials": trials,
        }
        entries[f"evaluated/{arm}/incoming/proposals.json"] = json.dumps(incoming).encode()
        comparisons[arm] = {
            "policyAccepted": 0, "browserVerifiedRepairs": 0,
            "perFault": summaries,
        }
    entries["comparison.json"] = json.dumps({
        "evidenceClass": "E39G12B4D_TWO_ARM_MODEL_REPAIR_COMPARISON",
        "modelTag": "qwen2.5-coder:7b",
        "realModelCallsFromUntrustedReceipt": 8,
        "genuinelyHeldOut": False,
        "stage45Go": False, "autonomousWebsiteFactoryCertified": False,
        "arms": comparisons,
    }).encode()
    if mutate:
        mutate(entries)
    p = folder/"evidence.zip"
    with ZipFile(p, "w") as z:
        for name, data in entries.items():
            z.writestr(name, data)
    return p


def rewrite_json(entries, name, modify):
    obj = json.loads(entries[name])
    modify(obj)
    entries[name] = json.dumps(obj).encode()


class ExactSourceQuoteForensicTest(unittest.TestCase):
    def test_categorizes_all_eight_without_claiming_acceptance(self):
        with tempfile.TemporaryDirectory() as d:
            report = diagnostic.check_replies(synthetic_archive(Path(d)))
        self.assertEqual(report["attempts"], 8)
        self.assertEqual(report["acceptedPatches"], 0)
        self.assertEqual(report["browserVerifiedRepairs"], 0)
        self.assertEqual(report["arms"]["original"]["categories"], {
            "wrong_authorized_literal": 3, "schema_incomplete_or_malformed": 1,
        })
        self.assertEqual(report["arms"]["source-span"]["categories"], {
            "correct_source_quote_but_policy_rejected": 2,
            "old_literal_not_unique_in_source": 1,
            "wrong_case_or_fault": 1,
        })
        for name in ("stage45Go", "automatedCodingCertified", "genuinelyHeldOut",
                     "ownerAuthorizationGranted", "frozenPolicyModified",
                     "originalSourceModified", "untrustedResponseTextEmitted"):
            self.assertIs(report[name], False, name)
        self.assertEqual(report["modelCallsPerformed"], 0)

    def test_tampered_original_source_is_rejected_before_classification(self):
        with tempfile.TemporaryDirectory() as d:
            def tamper(entries):
                key = "evaluated/original/repairs/studynest--keyboard-focus-visibility/index.html"
                entries[key] += b"PROHIBITED_MODIFICATION"
            with self.assertRaisesRegex(ValueError, "B4F_UNTRUSTED_SOURCE_OR_RECEIPT_DRIFT"):
                diagnostic.check_replies(synthetic_archive(Path(d), tamper))

    def test_spoofed_model_response_digest_is_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            key = "evaluated/source-span/incoming/proposals.json"
            def tamper(entries):
                def m(obj):
                    obj["trials"]["cityline--safe-untrusted-text"]["rawResponseSha256"] = "0"*64
                rewrite_json(entries, key, m)
            with self.assertRaisesRegex(ValueError, "B4F_UNTRUSTED_SOURCE_OR_RECEIPT_DRIFT"):
                diagnostic.check_replies(synthetic_archive(Path(d), tamper))

    def test_never_accepts_patch_or_claims_browser_correction(self):
        with tempfile.TemporaryDirectory() as d:
            key = "evaluated/original/repairs/cityline--reduced-motion/patch.json"
            def tamper(entries):
                rewrite_json(entries, key, lambda obj: obj.update(
                    policyOutcome="POLICY_APPLIED", patchApplied=True
                ))
            with self.assertRaisesRegex(ValueError, "B4F_REQUIRES_UNMODIFIED_REJECTED_SOURCE"):
                diagnostic.check_replies(synthetic_archive(Path(d), tamper))

    def test_untrusted_reply_cannot_grant_admin_or_stage45(self):
        with tempfile.TemporaryDirectory() as d:
            key = "evaluated/original/incoming/proposals.json"
            def tamper(entries):
                rewrite_json(entries, key, lambda obj: obj.update(ownerApprovalGranted=True))
            with self.assertRaisesRegex(ValueError, "B4F_BAD_MODEL_RECEIPT_BOUNDARY"):
                diagnostic.check_replies(synthetic_archive(Path(d), tamper))

    def test_missing_receipt_or_extra_zip_traversal_fails(self):
        with tempfile.TemporaryDirectory() as d:
            key = "evaluated/original/incoming/proposals.json"
            def tamper(entries):
                entries.pop(key)
            with self.assertRaises((KeyError, ValueError)):
                diagnostic.check_replies(synthetic_archive(Path(d), tamper))


if __name__ == "__main__":
    unittest.main()
