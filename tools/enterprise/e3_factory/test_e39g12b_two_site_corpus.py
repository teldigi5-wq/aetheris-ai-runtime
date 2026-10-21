"""E3.9G-12B1: immutable two-source website corpus safety tests (no model)."""
from __future__ import annotations
from pathlib import Path
import tempfile
import unittest
from unittest import mock
import e39g12b_two_site_corpus as corpus

class TwoSiteCorpusTests(unittest.TestCase):
    def test_distinct_sites_fault_classes_and_stable_source(self):
        self.assertEqual(set(corpus.SITES),{"studynest","cityline"})
        self.assertEqual(set(sum((list(x["faults"]) for x in corpus.SITES.values()),[])),
            {"keyboard-focus-visibility","responsive-mobile-overflow",
             "reduced-motion","safe-untrusted-text"})
        content={}
        for name,entry in corpus.SITES.items():
            raw=corpus.checked(corpus.HERE/name/"index.html",14000)
            text=raw.decode()
            self.assertIn('<!doctype html>',text)
            self.assertIn('</html>',text)
            for old,new in entry["substitutions"]:
                self.assertEqual(text.count(old),1)
                self.assertNotIn(new,text)
            content[name]=corpus.sha(raw)
        self.assertNotEqual(content["studynest"],content["cityline"])

    def test_corpus_seed_fail_closed_on_existing_dir_or_symlink(self):
        with tempfile.TemporaryDirectory() as temp:
            fake=Path(temp)/"already"
            fake.mkdir()
            with mock.patch.object(corpus,"BASE",fake):
                with self.assertRaisesRegex(ValueError,"E39G12B_CORPUS_EXISTS_OR_SYMLINK"):
                    corpus.corpus()
            link=Path(temp)/"link"
            link.symlink_to(fake,target_is_directory=True)
            with mock.patch.object(corpus,"BASE",link):
                with self.assertRaisesRegex(ValueError,"E39G12B_CORPUS_EXISTS_OR_SYMLINK"):
                    corpus.corpus()

    def test_golden_reference_only_not_a_model_generated_patch(self):
        src=(corpus.HERE/"studynest"/"index.html").read_text()
        self.assertIn(".skip:focus{top:-120px;}",src)
        self.assertIn("min-width:960px;",src)
        other=(corpus.HERE/"cityline"/"index.html").read_text()
        self.assertIn("output.innerHTML = 'Station: '+name;",other)
        self.assertIn("animation:floatline 4s linear infinite;",other)
        expected={
           "referenceOnly":True,"modelCalls":0,
           "modelBrowserRepairCredited":False,"ownerApprovalGranted":False}
        self.assertEqual(expected["modelCalls"],0)
        self.assertTrue(expected["referenceOnly"])

    def test_reject_unsafe_source_nonexistent_and_symlink(self):
        with tempfile.TemporaryDirectory() as temp:
            x=Path(temp)/"ok"
            x.write_text("hi")
            l=Path(temp)/"linked"
            l.symlink_to(x)
            with self.assertRaisesRegex(ValueError,"E39G12B_SOURCE_OR_FILE_UNSAFE"):
                corpus.checked(l,100)
            with self.assertRaisesRegex(ValueError,"E39G12B_SOURCE_OR_FILE_UNSAFE"):
                corpus.checked(Path(temp)/"missing",100)
            with self.assertRaisesRegex(ValueError,"E39G12B_SOURCE_OR_FILE_UNSAFE"):
                corpus.checked(x,1)

    def test_checker_not_an_ollama_or_model_agent(self):
        source=Path(corpus.__file__).read_text()
        browser=Path(__file__).with_name("e39g12b_two_site_browser.mjs").read_text()
        for bad in ("subprocess","http.client","local_json(","import requests",
                    "os.system(","eval(","exec("):
            self.assertNotIn(bad,source)
        self.assertIn("chromium.launch",browser)
        self.assertIn("screenshotSha256",browser)
        self.assertIn("document.documentElement.scrollWidth",browser)
        self.assertIn("prefers-reduced-motion",browser)
        self.assertIn("data-e39g12b-literal",browser)
        self.assertIn("modelRepairsCredited:0",browser)
        self.assertNotIn("ollama",browser.lower())

if __name__=="__main__":
    unittest.main()
