"""E3.9G-12B3 hard negative tests for untrusted model/evaluator bridge."""
from __future__ import annotations
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

import e39g12b3_policy_worker as worker
import e39g12b3_independent_score as scorer
import e39g12b2_typed_policy as frozen
import e39g12b_two_site_corpus as corpus


class IsolatedModelBridgeTests(unittest.TestCase):
    def test_only_four_scoped_tokens(self):
        self.assertEqual(len(worker.FAULTS),4)
        for t in worker.FAULTS:
            self.assertEqual(worker.paths(t)[0],t.split("--")[0])
        for t in ("cityline--owner","cityline--safe-untrusted-text/../../etc",
                  "studynest--reduced-motion","..--keyboard-focus-visibility"):
            with self.assertRaisesRegex(ValueError,"E39G12B3_UNREVIEWED"):
                worker.paths(t)

    def test_frozen_policy_refuses_cross_site_and_bad_js(self):
        original=corpus.checked(corpus.HERE/"cityline"/"index.html",14000)
        old,_=frozen.RULES[("cityline","safe-untrusted-text")]
        proposal={"site":"cityline","fault":"safe-untrusted-text",
                  "operation":"replace_one","old":old,
                  "new":"output.textContent = 'Station: '+name;",
                  "sourceSha256":worker.sha(original)}
        self.assertIn(b"output.textContent",frozen.validate(proposal,original))
        for new in ("output.innerHTML = 'Station: '+name;",
                    "output.textContent = eval(name);"):
            with self.assertRaisesRegex(ValueError,"E39G12B2_"):
                frozen.validate(dict(proposal,new=new),original)
        with self.assertRaisesRegex(ValueError,"E39G12B2_"):
            frozen.validate(dict(proposal,owner=True),original)
        with self.assertRaisesRegex(ValueError,"E39G12B2_"):
            frozen.validate(dict(proposal,site="studynest"),original)

    def test_untrusted_receipt_cannot_forge_model_calls_or_owner(self):
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp)
            (base/"incoming").mkdir()
            manifest={"evidenceClass":"E39G12B3_FOUR_REAL_LOCAL_OLLAMA_COMPLETIONS",
                      "modelTag":worker.MODEL,"realModelCalls":4,
                      "modelTextExecuted":False,"modelSourceEdited":False,
                      "ownerApprovalGranted":True,"productionPublished":False,
                      "goldenReferenceAvailable":False,
                      "ollamaSelfReportedDigest":worker.MODEL_PREFIX+"b"*52,
                      "sizeBytes":986000000,"trials":{}}
            for token in worker.FAULTS:
                site,fault=worker.FAULTS[token]
                src=corpus.checked(corpus.HERE/site/"index.html",14000)
                raw='{"operation":"abstain"}'
                manifest["trials"][token]={
                    "modelTag":worker.MODEL,"site":site,"fault":fault,
                    "realModelCalls":1,"sourceSha256":worker.sha(src),
                    "rawModelResponse":raw,"rawResponseSha256":worker.sha(raw.encode()),
                    "generatedTokens":8,"promptSha256":"a"*64}
            (base/"incoming"/"proposals.json").write_text(json.dumps(manifest))
            with mock.patch.object(worker,"ROOT",base),mock.patch.object(
                 worker.corpus,"BASE",base/"seed"):
                with mock.patch.object(worker,"read",side_effect=lambda p,lim:
                      corpus.checked(corpus.HERE/(worker.FAULTS["cityline--safe-untrusted-text"][0])/"index.html",14000)
                     if p.name=="index.html" else p.read_bytes()):
                    with self.assertRaises((ValueError,FileNotFoundError)):
                        worker.inspect("cityline--safe-untrusted-text")

    def test_scoring_does_not_credit_rejected_candidate(self):
        with tempfile.TemporaryDirectory() as tmp:
            base=Path(tmp)
            folder=base/"repairs"/"cityline--safe-untrusted-text"
            folder.mkdir(parents=True)
            broken=corpus.checked(corpus.HERE/"cityline"/"index.html",14000)
            (folder/"index.html").write_bytes(broken)
            receipt={"evidenceClass":"E39G12B3_NETWORK_NONE_TYPED_SITE_PATCH",
                "site":"cityline","fault":"safe-untrusted-text",
                "modelTag":worker.MODEL,"modelRawSha256":"b"*64,
                "sourceBeforeSha256":worker.sha(broken),
                "sourceAfterSha256":worker.sha(broken),
                "realModelCallsFromUntrustedReceipt":1,
                "ownerApprovalGranted":False,"productionPublished":False,
                "modelAfterBrowserVerified":False,"isolatedNetworkNoneWorker":True,
                "policyOutcome":"POLICY_REJECTED","patchApplied":False}
            (folder/"patch.json").write_text(json.dumps(receipt))
            entry={"rawResponseSha256":"b"*64,"rawModelResponse":"{}"}
            with mock.patch.object(worker,"ROOT",base),mock.patch.object(
                    worker,"inspect",return_value=(entry,broken,{})):
                grade=scorer.grade("cityline--safe-untrusted-text")
            self.assertFalse(grade["realBrowserVerifiedModelRepair"])
            self.assertEqual(grade["actualBrowserViewports"],0)
            self.assertFalse(grade["policyApplied"])

    def test_browser_scorer_no_gold_or_model_imports(self):
        browser=Path(__file__).with_name("e39g12b3_independent_browser.mjs").read_text()
        scorer_src=Path(scorer.__file__).read_text()
        worker_src=Path(worker.__file__).read_text()
        runner=Path(__file__).with_name("run_locked_worker.sh").read_text()
        self.assertIn("chromium.launch",browser)
        self.assertIn("screenshotSha256",browser)
        self.assertNotIn("e39g12b2_typed_policy",browser)
        self.assertNotIn("reference",browser.lower())
        self.assertIn("frozen.validate",worker_src)
        self.assertIn("frozen.validate",scorer_src)
        self.assertNotIn("subprocess",worker_src)
        self.assertNotIn("http.client",worker_src)
        self.assertIn("--network none",runner)
        self.assertIn("e39g12b3-apply",runner)


if __name__=="__main__":unittest.main()
