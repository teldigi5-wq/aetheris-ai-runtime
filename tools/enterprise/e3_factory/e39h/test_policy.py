"""Static guard for inert E3.9H procedural WebGL2 reference only.

These checks do NOT certify actual WebGL hardware, accessibility, AI
model-generated scenes, licensing of future assets, or production readiness.
"""
import re
import unittest
from html.parser import HTMLParser
from pathlib import Path

HERE=Path(__file__).resolve().parent
REF=HERE/"reference"

class Parser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.srcs=[]
        self.external=[]
        self.ids=[]
        self.tags=[]
    def handle_starttag(self,tag,attrs):
        attrs=dict(attrs)
        self.tags.append(tag)
        if "id" in attrs:self.ids.append(attrs["id"])
        if tag in ("script","link"):
            val=attrs.get("src",attrs.get("href",""))
            self.srcs.append(val)
            if val.startswith(("http:","https:","//")):self.external.append(val)

class ReferenceSafetyTest(unittest.TestCase):
    def test_exact_reference_files_and_source_size(self):
        names={p.name for p in REF.iterdir()}
        self.assertEqual(names,{"index.html","styles.css","scene.js"})
        for name in names:
            f=REF/name
            self.assertFalse(f.is_symlink())
            self.assertLess(f.stat().st_size,15000)
            f.read_text(encoding="utf-8")
    def test_local_assets_and_semantic_content(self):
        parser=Parser()
        parser.feed((REF/"index.html").read_text())
        self.assertFalse(parser.external)
        self.assertEqual(set(parser.srcs),{"./styles.css","./scene.js"})
        self.assertEqual(len(parser.ids),len(set(parser.ids)))
        for landmark in ("main","header","nav","footer","h1","h2","canvas"):
            self.assertIn(landmark,parser.tags)
        self.assertIn("static-fallback",parser.ids)
        self.assertIn("study-canvas",parser.ids)
        html=(REF/"index.html").read_text()
        self.assertIn("not an AI-generated luxury customer website",html)
    def test_no_network_or_process_escape(self):
        source=(REF/"scene.js").read_text()
        for prohibited in ("fetch(", "XMLHttpRequest","WebSocket(", "eval(", "Function(",
                           "localStorage","sessionStorage","postMessage(","import(",
                           "navigator.sendBeacon","document.cookie", "window.open("):
            self.assertNotIn(prohibited,source)
        for expected in ("webgl2","drawArrays","requestAnimationFrame",
                         "webglcontextlost","prefers-reduced-motion","no-webgl"):
            self.assertIn(expected,source)
    def test_fallback_and_responsive_layout(self):
        css=(REF/"styles.css").read_text()
        self.assertIn("@media(max-width:800px)",css)
        self.assertIn("@media(prefers-reduced-motion:reduce)",css)
        self.assertIn("[hidden]{display:none!important}",css)
        self.assertIn("overflow-x:clip",css)
    def test_evidence_never_grants_authority(self):
        script=(HERE.parent/"e39h_browser_proof.mjs").read_text()
        for key in ("autonomousModelGeneratedCode:false","realOwnerHardwareBenchmarked:false",
                    "releaseAuthorized:false","stage45Go:false","independentHumanAuthoredDesignProven:false"):
            self.assertIn(key,script)
        self.assertIn("pixelShaLate",script)
        self.assertIn("no-WebGL",script)
if __name__=="__main__":
    unittest.main()
