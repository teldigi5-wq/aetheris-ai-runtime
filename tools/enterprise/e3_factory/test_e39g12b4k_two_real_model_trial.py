"""B4K model-input separation, forged receipts, typed-policy invariants."""
from __future__ import annotations
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile

import e39g12b4k_two_real_model_trial as t
import e39g12b4j_typed_control_worker as gate


def receipt(bundle: bytes):
    rows={}
    expected=t.expected()
    for case in t.CASES:
        src=t.safe_read(gate.FIX/(case+"-negative.html"),5000)
        old,new=gate.RULES[case]
        raw=json.dumps({"case":case,"operation":"replace_one","sourceSha256":t.sha(src),
                        "old":old,"new":new},sort_keys=True)
        row=expected["trials"][case]
        rows[case]={"case":case,"promptSha256":row["promptSha256"],
                    "sourceSha256":row["sourceSha256"],"rawModelResponse":raw,
                    "rawResponseSha256":t.sha(raw.encode()),"generatedTokens":12,
                    "elapsedWallMs":120}
    return {
        "evidenceClass":"E39G12B4K_UNTRUSTED_TWO_GENUINE_LOCAL_RESPONSES",
        "modelTag":t.MODEL,"realModelCalls":2,
        "promptBundleSha256":t.sha(bundle),
        "modelDigestSelfReported":t.DIGEST_PREFIX+"a"*52,
        "modelSizeBytes":4700000000,
        "ownerApprovalGranted":False,"referenceAvailableToModel":False,
        "productionAuthorized":False,"modelSourceEdited":False,"trials":rows,
    }


class TwoRealModelContract(unittest.TestCase):
    def test_public_packet_has_broken_html_only_never_solution(self):
        data=t.packed()
        with ZipFile(__import__("io").BytesIO(data)) as z:
            self.assertEqual(z.namelist(),["prompts.json"])
            p=t.parse(z.read("prompts.json"),30000)
        self.assertEqual(set(p["trials"]),set(t.CASES))
        self.assertFalse(p["referencesIncluded"])
        self.assertFalse(p["goldenSolutionsIncluded"])
        self.assertFalse(p["genuinelyHeldOut"])
        self.assertEqual(p["modelCallsPerformed"],0)
        for case in t.CASES:
            row=p["trials"][case]
            broken=(gate.FIX/(case+"-negative.html")).read_bytes()
            self.assertIn(broken.decode(),row["prompt"])
            self.assertEqual(row["sourceSha256"],t.sha(broken))
            self.assertEqual(row["promptSha256"],t.sha(row["prompt"].encode()))
            self.assertNotIn("B4I — Trusted "+case+" positive control",row["prompt"])
            self.assertNotIn(gate.RULES[case][1],row["prompt"])

    def test_local_receipt_verified_but_no_attested_model_identity(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            zip_path=root/"public-model-prompts.zip"
            data=t.packed()
            zip_path.write_bytes(data)
            incoming=root/"incoming"
            incoming.mkdir()
            receipt_path=incoming/"model-receipt.json"
            receipt_path.write_text(json.dumps(receipt(data)))
            with patch.object(t,"ROOT",root),patch.object(t,"PACKET",zip_path),patch.object(t,"RECEIPT",receipt_path):
                valid=t.verify()
                self.assertEqual(valid["realModelCalls"],2)
                t.prepare("focus")
                admitted=json.loads((root/"admission/focus.json").read_text())
                self.assertTrue(admitted["formatEligible"])
                self.assertFalse(admitted["policyAccepted"])
                p=json.loads((root/"incoming-proposals/focus.json").read_text())
                self.assertEqual(set(p),t.JSON_FIELDS)
                with self.assertRaisesRegex(ValueError,"B4K_ADMISSION_REPLAY"):
                    t.prepare("focus")

    def test_forged_authority_or_receipt_hash_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            zip_path=root/"public-model-prompts.zip"
            zip_path.write_bytes(t.packed())
            incoming=root/"incoming"
            incoming.mkdir()
            rpath=incoming/"model-receipt.json"
            good=receipt(zip_path.read_bytes())
            with patch.object(t,"PACKET",zip_path),patch.object(t,"RECEIPT",rpath):
                for mutation in (
                    lambda r: r.update(ownerApprovalGranted=True),
                    lambda r: r.update(referenceAvailableToModel=True),
                    lambda r: r.update(promptBundleSha256="0"*64),
                    lambda r: r["trials"]["focus"].update(rawResponseSha256="f"*64),
                ):
                    r=json.loads(json.dumps(good))
                    mutation(r)
                    rpath.write_text(json.dumps(r))
                    with self.assertRaisesRegex(ValueError,"B4K_UNTRUSTED_MODEL_RECEIPT_CONTRACT|B4K_RECEIPT_ROW_DRIFT"):
                        t.verify()

    def test_literal_not_matching_reviewed_policy_fails_even_if_format_ok(self):
        source=(gate.FIX/"focus-negative.html").read_bytes()
        golden=(gate.FIX/"focus-positive.html").read_bytes()
        old,new=gate.RULES["focus"]
        proposal=dict(case="focus",operation="replace_one",
                      sourceSha256=t.sha(source),old=old,new=new)
        self.assertEqual(gate.review("focus",proposal,source,golden),
                         gate.title_normalize(golden,"focus"))
        for bad in ('<script>alert(1)</script>',new+" /*evil*/",
                    '.skip:focus{top:0;}'):
            with self.assertRaisesRegex(ValueError,"B4J_UNAUTHORIZED_EDIT"):
                gate.review("focus",dict(proposal,new=bad),source,golden)

    def test_command_allowlist_does_not_enable_arbitrary_model_path(self):
        sh=(Path(__file__).resolve().parent/"run_locked_worker.sh").read_text()
        self.assertIn("e39g12b4k-apply)",sh)
        self.assertIn('case "$2" in focus|motion)',sh)
        for val in ("--network none","--read-only","--cap-drop ALL",
                    "--security-opt no-new-privileges","--memory 256m"):
            self.assertIn(val,sh)
        script=(Path(__file__).resolve().parent/"e39g12b4k_model_after_browser.mjs").read_text()
        self.assertIn("B4K_EXACT_REVIEWED_HTML_ONLY",script)
        self.assertIn("javaScriptEnabled:false",script)
        self.assertIn("route.abort()",script)
        self.assertNotIn("http://127.0.0.1",script)


if __name__=="__main__":
    unittest.main()
