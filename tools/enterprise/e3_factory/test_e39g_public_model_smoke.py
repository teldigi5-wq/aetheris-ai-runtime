"""Known-public corpus and untrusted score tests, with no real model calls."""
import json
from pathlib import Path
import tempfile
import unittest
import e39g12b4g_curator_boundary as b4g
import e39g_public_model_smoke as public

class PublicCorpusTests(unittest.TestCase):
    def test_four_pinned_public_sources_three_sites(self):
        packet=public.packet()
        self.assertEqual(len(packet["cases"]),4)
        self.assertEqual(len(set(x["site"] for x in packet["cases"])),3)
        self.assertFalse(packet["independentCuratorVerified"])
        self.assertFalse(packet["referenceSolutionsIncluded"])
        self.assertFalse(packet["genuinelyBlindOutcomeVerified"])
        self.assertEqual(sorted(x["id"] for x in packet["cases"]),
                         [x["id"] for x in packet["cases"]])
    def test_packet_has_no_reference_or_reference_hash_fields(self):
        body=json.dumps(public.packet())
        self.assertNotIn("referenceHtml",body)
        self.assertNotIn("referenceSha256",body)
        self.assertNotIn("privateSeal",body)
    def test_score_counts_all_attempts_without_browser_credit(self):
        original=public.ROOT
        with tempfile.TemporaryDirectory() as folder:
            public.ROOT=Path(folder)
            try:
                public.pack()
                result=public.packet()
                rows=[]
                for case in result["cases"]:
                    answer='{"operation":"abstain"}'
                    rows.append({
                        "caseId":case["id"],"sourceSha256":case["brokenSha256"],
                        "rawModelResponse":answer,
                        "rawResponseSha256":b4g.digest(answer.encode()),
                    })
                receipt={"evidenceClass":"E39G12B4H_UNTRUSTED_MODEL_PROPOSALS",
                    "sourceBundleSha256":b4g.digest(public.ZIP.read_bytes()),
                    "modelTag":"qwen2.5-coder:7b","modelCallsSelfReported":4,
                    "ownerApprovalGranted":False,"goldenReferenceAvailable":False,
                    "modelOutputExecuted":False,"trials":rows}
                public.RECEIPT.write_bytes(b4g.canonical(receipt))
                report=public.score()
                self.assertEqual(report["attemptsCounted"],4)
                self.assertEqual(report["categories"],{"MODEL_ABSTAINED":4})
                self.assertEqual(report["browserVerifiedRepairs"],0)
                self.assertFalse(report["genuinelyHeldOut"])
            finally:
                public.ROOT=original

if __name__=="__main__":
    unittest.main()
