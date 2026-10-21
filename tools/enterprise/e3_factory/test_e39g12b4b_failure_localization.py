"""E3.9G-12B4B forensic replay and source-window adversarial checks."""
from __future__ import annotations
from pathlib import Path
from unittest import TestCase
import io
import json
import zipfile
from unittest import mock

import e39g12b_two_site_corpus as corpus
import e39g12b4b_failure_audit as audit
import e39g12b4b_source_locator as locator


class Real7BFailureAuditTests(TestCase):
    def test_original_four_model_outputs_match_original_sha256(self):
        result=audit.replay()
        self.assertEqual(result["historicalModelCompletions"],4)
        self.assertEqual(result["newModelCalls"],0)
        self.assertEqual(result["wellFormedInScopeProposalJson"],4)
        self.assertEqual(result["exactReviewedOldDeclarationMatches"],0)
        self.assertEqual(result["rejectedWrongSourceTarget"],4)
        self.assertEqual(result["realModelBrowserVerifiedRepairs"],0)
        self.assertEqual(result["rejectionsByClass"],{
            "AMBIGUOUS_FRAGMENT_OF_TARGET":1,
            "PARTIAL_TARGET_DECLARATION":1,
            "UNRELATED_SOURCE_TEXT":2,
        })
        self.assertEqual({v["frozenPolicyReason"] for v in result["perCase"].values()},
                         {"E39G12B2_OLD_NOT_UNIQUE_AUTHORIZED_LITERAL"})
        self.assertFalse(result["policyWeakened"])

    def test_replay_tamper_including_fake_owner_is_denied(self):
        original=audit.EVIDENCE
        data=json.loads(original.read_text())
        with mock.patch.object(audit,"EVIDENCE",original):
            self.assertEqual(len(audit.replay()["perCase"]),4)
        import tempfile
        with tempfile.TemporaryDirectory() as tmp:
            file=Path(tmp)/"replay.json"
            bad=json.loads(original.read_text())
            bad["trials"]["cityline--reduced-motion"]["rawModelResponse"]+="   "
            file.write_text(json.dumps(bad))
            with mock.patch.object(audit,"EVIDENCE",file):
                with self.assertRaisesRegex(ValueError,"E39G12B4B_HISTORICAL_RAW_HASH"):
                    audit.replay()
            bad=json.loads(original.read_text())
            bad["ownerApproved"]=True
            file.write_text(json.dumps(bad))
            with mock.patch.object(audit,"EVIDENCE",file):
                with self.assertRaisesRegex(ValueError,"E39G12B4B_HISTORICAL_EVIDENCE"):
                    audit.replay()

    def test_four_source_spans_from_broken_source_only(self):
        expected={
          ("studynest","keyboard-focus-visibility"):".skip:focus{top:-120px;}",
          ("studynest","responsive-mobile-overflow"):
             "grid-template-columns:1fr;min-width:960px;",
          ("cityline","reduced-motion"):
             "@media(prefers-reduced-motion:reduce){.ticker{animation:floatline 4s linear infinite;}}",
          ("cityline","safe-untrusted-text"):"output.innerHTML = 'Station: '+name;",
        }
        for (site,fault),old in expected.items():
            with self.subTest(fault=fault):
                source=corpus.checked(corpus.HERE/site/"index.html",14000)
                self.assertEqual(locator.extract(site,fault,source),old)
                self.assertEqual(source.count(old.encode()),1)
        self.assertEqual(len(locator.FAULTS),2)
        self.assertEqual(sum(len(x) for x in locator.FAULTS.values()),4)

    def test_unknown_fault_missing_original_span_and_oversize_denied(self):
        s=corpus.checked(corpus.HERE/"cityline"/"index.html",14000)
        with self.assertRaisesRegex(ValueError,"E39G12B4B_UNREVIEWED"):
            locator.extract("cityline","owner-overrides",s)
        with self.assertRaisesRegex(ValueError,"E39G12B4B_SOURCE_WINDOW"):
            locator.extract("cityline","safe-untrusted-text",s.replace(b"output.innerHTML",b"output.removedSINK"))
        with self.assertRaisesRegex(ValueError,"E39G12B4B_SOURCE_TOO_LONG"):
            locator.extract("cityline","safe-untrusted-text",b"x"*15000)

    def test_model_archive_exact_four_files_no_golden_content(self):
        fixture={}
        manifest={"evidenceClass":"E39G12B2_MODEL_FACING_ONLY_BROKEN_PUBLIC_SYNTHETIC",
                  "referenceVariantsIncluded":False,"goldenReplacementIncluded":False,"sites":{}}
        for site in ("studynest","cityline"):
            source=corpus.checked(corpus.HERE/site/"index.html",14000)
            fixture[f"sites/{site}/index.html"]=source
            manifest["sites"][site]={"sha256":locator.sha(source)}
        fixture["proposer-manifest.json"]=json.dumps(manifest).encode()
        output=io.BytesIO()
        with zipfile.ZipFile(output,"w",compression=zipfile.ZIP_DEFLATED) as z:
            for name,raw in fixture.items():z.writestr(name,raw)
        package,observed=locator.produce(output.getvalue())
        self.assertEqual(len(observed["observations"]),4)
        with zipfile.ZipFile(io.BytesIO(package)) as z:
            self.assertEqual(sorted(z.namelist()),sorted(locator.OUTPUTS))
            self.assertTrue(all("reference" not in s and "gold" not in s for s in z.namelist()))
            self.assertEqual(z.read("sites/cityline/index.html"),fixture["sites/cityline/index.html"])
            added=json.loads(z.read("observed-declarations.json"))
            self.assertIs(added["referenceInputsAvailable"],False)
            self.assertIs(added["replacementAnswersProvided"],False)
            for row in added["observations"].values():
                self.assertIsNone(row["proposedReplacement"])
                self.assertFalse(row["ownerApprovalGranted"])
        extras=io.BytesIO()
        with zipfile.ZipFile(extras,"w") as z:
            z.writestr("unreviewed-golden-answer.txt","never pass to model")
        with self.assertRaisesRegex(ValueError,"E39G12B4B_UNEXPECTED_INPUT"):
            locator.produce(extras.getvalue())

    def test_locator_contains_no_frozen_policy_or_model_network_code(self):
        locator_source=Path(locator.__file__).read_text()
        audit_source=Path(audit.__file__).read_text()
        self.assertNotIn("import e39g12b2_typed_policy",locator_source)
        self.assertNotIn("import e39g12b_two_site_corpus",locator_source)
        self.assertIn("import e39g12b2_typed_policy",audit_source)
        self.assertNotIn("local_json(",locator_source)
        self.assertNotIn("subprocess",locator_source)
        self.assertNotIn("urllib",locator_source)
        self.assertNotIn("requests",locator_source)
