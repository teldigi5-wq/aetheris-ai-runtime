"""B4I static trust boundary tests. Browser capability is verified separately.

The pages are fixed assistant-authored research fixtures, not hidden model tests.
"""
from __future__ import annotations
from html.parser import HTMLParser
from pathlib import Path
import re
import unittest

HERE=Path(__file__).resolve().parent
FIX=HERE/"e39g12b4i"/"fixtures"
BROWSER=HERE/"e39g12b4i_browser_controls.mjs"
CASES=("focus","motion")
VARIANTS=("negative","positive")


class Reader(HTMLParser):
    def __init__(self):
        super().__init__()
        self.tags=[]
        self.ids=[]
        self.attrs=[]
    def handle_starttag(self,tag,attrs):
        a=dict(attrs)
        self.tags.append(tag)
        self.attrs.append((tag,a))
        if "id" in a:self.ids.append(a["id"])


class BrowserControlPolicyTest(unittest.TestCase):
    def test_fixed_four_local_fixtures_only(self):
        self.assertEqual(
            {x.name for x in FIX.iterdir()},
            {f"{kind}-{variant}.html" for kind in CASES for variant in VARIANTS}
        )
        for f in FIX.iterdir():
            self.assertTrue(f.is_file())
            self.assertFalse(f.is_symlink())
            self.assertLess(f.stat().st_size,5_000)
            html=f.read_text(encoding="utf-8")
            reader=Reader()
            reader.feed(html)
            self.assertIn("lang=\"en\"",html)
            self.assertIn("default-src 'none'",html)
            self.assertIn("connect-src 'none'",html)
            self.assertNotIn("<script",html.lower())
            self.assertNotIn("@import",html.lower())
            self.assertNotIn("url(",html.lower())
            self.assertNotIn("onerror=",html.lower())
            self.assertIn("main",reader.tags)
            self.assertIn("h1",reader.tags)
            self.assertEqual(len(reader.ids),len(set(reader.ids)))
            for tag, attrs in reader.attrs:
                self.assertNotIn(tag,("iframe","object","embed","form","script","video","audio","img"))
                for key in ("src","action","srcset"):
                    self.assertNotIn(key,attrs)
                if "href" in attrs:
                    self.assertTrue(attrs["href"].startswith("#"))

    def test_only_reviewed_css_defect_is_corrected(self):
        for kind in CASES:
            first=(FIX/f"{kind}-negative.html").read_text(encoding="utf-8")
            second=(FIX/f"{kind}-positive.html").read_text(encoding="utf-8")
            first=re.sub(r"<title>.*?</title>","<title>CONTROL</title>",first)
            second=re.sub(r"<title>.*?</title>","<title>CONTROL</title>",second)
            if kind=="focus":
                bad=".skip:focus{top:-120px;}"
                good=".skip:focus{top:16px;}"
            else:
                bad="@media(prefers-reduced-motion:reduce){.ticker{animation:tickerPulse 2s linear infinite;}}"
                good="@media(prefers-reduced-motion:reduce){.ticker{animation:none;}}"
            self.assertEqual(first.count(bad),1)
            self.assertEqual(second.count(good),1)
            self.assertEqual(first.replace(bad,good),second)

    def test_browser_script_rejects_model_or_arbitrary_file_loading(self):
        src=BROWSER.read_text(encoding="utf-8")
        for expected in (
            "process.argv.length !== 2","javaScriptEnabled:false",
            "serviceWorkers:'block'","context.route('**/*'",
            "route.abort()","page.setContent(html",
            "page.keyboard.press('Tab')","390,height:844",
            "1280,height:800","stage45Go:false",
            "modelOutputExecuted:false","browserVerifiedRepairs",
        ):
            if expected=="browserVerifiedRepairs":
                # Static fixture can prove positive controls but NO model repairs.
                self.assertIn("modelRepairCredited:false",src)
            else:
                self.assertIn(expected,src)
        for prohibited in ("eval(", "new Function(", "child_process",
                           "spawn(", "execSync(", "page.goto(url",
                           "file://", "http://127.0.0.1"):
            self.assertNotIn(prohibited,src)
        self.assertTrue(BROWSER.is_file())

if __name__=="__main__":
    unittest.main()
