"""E3.9G-12B2: hard refusal of arbitrary repairs and leak of evaluator gold."""
from __future__ import annotations
from dataclasses import replace
import io
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest import mock
import zipfile

import e39g12b_two_site_corpus as baseline
import e39g12b2_proposer_bundle as bundle
import e39g12b2_typed_policy as gate


class E39G12B2BoundaryTests(unittest.TestCase):
    def proposal(self,site,fault,new):
        old,_=gate.RULES[(site,fault)]
        broken=baseline.checked(baseline.HERE/site/"index.html",14000)
        return broken,{"site":site,"fault":fault,"old":old,"new":new,
                       "operation":"replace_one","sourceSha256":gate.sha(broken)}

    def test_distinct_four_reviewed_fault_contracts(self):
        self.assertEqual(set(gate.RULES),{
            ("studynest","keyboard-focus-visibility"),
            ("studynest","responsive-mobile-overflow"),
            ("cityline","reduced-motion"),
            ("cityline","safe-untrusted-text")})
        approved=gate.independently_test_all_cases()
        self.assertEqual(len(approved),4)
        self.assertTrue(all(r["policyAcceptedDryRun"] and
            r["modelGeneratedBrowserRepair"] is False for r in approved.values()))

    def test_policy_allows_scoped_alternatives_without_hardcoded_one_golden_answer(self):
        cases=[
            ("studynest","keyboard-focus-visibility",".skip:focus{top:0px;}"),
            ("studynest","keyboard-focus-visibility",".skip:focus{top:79px;}"),
            ("studynest","responsive-mobile-overflow","grid-template-columns:1fr;min-width:0;"),
            ("studynest","responsive-mobile-overflow","grid-template-columns:1fr;min-width:300px;"),
            ("cityline","reduced-motion",
             "@media(prefers-reduced-motion:reduce){.ticker{animation:none 0s;}}"),
            ("cityline","safe-untrusted-text","output.innerText = 'Station: '+name;"),
        ]
        for site,fault,new in cases:
            with self.subTest(site=site,fault=fault,new=new):
                raw,p=self.proposal(site,fault,new)
                out=gate.validate(p,raw)
                self.assertNotEqual(out,raw)
                self.assertEqual(out.count(new.encode()),1)

    def test_policy_refuses_controls_html_event_handlers_and_unbounded_css(self):
        cases=[
            ("studynest","keyboard-focus-visibility",".skip:focus{top:-20px;}"),
            ("studynest","keyboard-focus-visibility",".skip:focus{top:81px;}"),
            ("studynest","keyboard-focus-visibility",".skip:focus{top:16px;display:none;}"),
            ("studynest","responsive-mobile-overflow","grid-template-columns:1fr;min-width:1280px;"),
            ("studynest","responsive-mobile-overflow","grid-template-columns:1fr;min-width:391px;"),
            ("cityline","reduced-motion",
              "@media(prefers-reduced-motion:reduce){.ticker{animation:floatline 0s;}}"),
            ("cityline","safe-untrusted-text","output.innerHTML = 'Station: '+name;"),
            ("cityline","safe-untrusted-text","output.textContent = eval(name);"),
            ("cityline","safe-untrusted-text","output.textContent = 'Station: '+name; window.open('https://example.org');"),
        ]
        for site,fault,new in cases:
            with self.subTest(site=site,fault=fault,new=new):
                src,p=self.proposal(site,fault,new)
                with self.assertRaisesRegex(ValueError,"E39G12B2_"):
                    gate.validate(p,src)

    def test_policy_rejects_source_tampering_other_keys_and_wrong_scope(self):
        raw,p=self.proposal("cityline","safe-untrusted-text","output.textContent = 'Station: '+name;")
        examples=[
            dict(p,ownerApproved=True),
            dict(p,operation="chmod_all"),
            dict(p,old="<script>"),
            dict(p,site="other"),
            dict(p,sourceSha256="0"*64),
        ]
        for example in examples:
            with self.subTest(example=repr(example)[-90:]):
                with self.assertRaisesRegex(ValueError,"E39G12B2_"):
                    gate.validate(example,raw)
        with self.assertRaisesRegex(ValueError,"E39G12B2_SOURCE_NOT_EXACT"):
            gate.validate(p,raw+b"<!-- unauthorized -->")
        with self.assertRaisesRegex(ValueError,"E39G12B2_DUPLICATE_UNTRUSTED"):
            gate.decode_json('{"site":"cityline","site":"evil"}')

    def test_bundle_contains_only_two_broken_htmls_not_references(self):
        with TemporaryDirectory() as tmp:
            target=Path(tmp)/"e39g12b"
            with mock.patch.object(baseline,"BASE",target),mock.patch.object(bundle.source,"BASE",target):
                manifest=baseline.corpus()
                receipt=bundle.pack()
                self.assertEqual(len(receipt["exactPaths"]),3)
                arc_path=target/bundle.BUNDLE_NAME
                with zipfile.ZipFile(arc_path) as z:
                    self.assertEqual(sorted(z.namelist()),sorted(bundle.EXPLICIT_EXPORTS))
                    for site in baseline.SITES:
                        broken=(target/"sites"/site/"broken"/"index.html").read_bytes()
                        ref=(target/"sites"/site/"reference"/"index.html").read_bytes()
                        data=z.read("sites/"+site+"/index.html")
                        self.assertEqual(data,broken)
                        self.assertNotEqual(data,ref)
                        self.assertEqual(bundle.sha(data),receipt["sourceSiteHashes"][site])
                    input_manifest=json.loads(z.read("proposer-manifest.json"))
                    self.assertIs(input_manifest["referenceVariantsIncluded"],False)
                    self.assertIs(input_manifest["goldenReplacementIncluded"],False)
                    self.assertEqual(input_manifest["modelCalls"],0)
                    self.assertFalse(any("reference" in n or "gold" in n for n in z.namelist()))
                with self.assertRaisesRegex(ValueError,"E39G12B2_MISSING_OR_DUPLICATE"):
                    bundle.pack()

    def test_no_model_or_file_execution_and_external_privileges(self):
        from pathlib import Path
        proposer=Path(bundle.__file__).read_text()
        gate_src=Path(gate.__file__).read_text()
        for bad in ("import subprocess","os.system(","Popen(","exec(","eval(","local_json("):
            self.assertNotIn(bad,proposer)
            self.assertNotIn(bad,gate_src)
        self.assertNotIn("write_bytes(",gate_src)
        self.assertNotIn("open('wb'",gate_src)
        self.assertIn("exact source",gate.__doc__.lower())
        self.assertIn("gold",proposer.lower())
        self.assertNotIn("input()",gate_src)


if __name__=="__main__":
    unittest.main()
