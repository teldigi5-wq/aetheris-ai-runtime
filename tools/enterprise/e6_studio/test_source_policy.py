"""E6 cinematic storyboard source contract: research-only, no model/privileges."""
from __future__ import annotations

import hashlib
from html.parser import HTMLParser
from pathlib import Path
from unittest import TestCase, main

SITE = Path(__file__).parent / "reference"
HTML = SITE / "index.html"
CSS = SITE / "studio.css"
JS = SITE / "studio.js"


class OfflineAssets(HTMLParser):
    def __init__(self):
        super().__init__()
        self.resources = []
        self.main = 0
        self.canvas = 0
        self.articles = 0
        self.skip = False

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if tag == "main":
            self.main += 1
        if tag == "canvas":
            self.canvas += 1
        if tag == "article":
            self.articles += 1
        if tag == "a" and values.get("href") == "#main":
            self.skip = True
        if tag == "script":
            self.resources.append(values.get("src"))
        if tag == "link" and values.get("rel") == "stylesheet":
            self.resources.append(values.get("href"))


class E6StoryboardSourcePolicy(TestCase):
    def test_exact_local_assets_and_three_readable_story_chapters(self):
        html = HTML.read_text()
        parser = OfflineAssets()
        parser.feed(html)
        self.assertEqual(parser.resources, ["./studio.css", "./studio.js"])
        self.assertEqual(parser.main, 1)
        self.assertEqual(parser.canvas, 1)
        self.assertEqual(parser.articles, 3)
        self.assertTrue(parser.skip)
        for id_ in ("id=\"story\"", "id=\"stage-canvas\"", "id=\"static-stage\"",
                    "id=\"render-status\"", "id=\"shot-label\""):
            self.assertIn(id_, html)
        self.assertIn("human-coded research artifact", html)
        self.assertIn("STAGE45 NO_GO", html)

    def test_no_remote_scripts_fonts_network_or_product_auth_claims(self):
        for file in (HTML, CSS, JS):
            source = file.read_text()
            for marker in ("https://", "http://", "@import", "fetch(",
                           "XMLHttpRequest", "WebSocket", "localStorage",
                           "sessionStorage", "navigator.sendBeacon",
                           "document.cookie", "eval(", "new Function(",
                           "model.generate(", "deploy(", "child_process"):
                self.assertNotIn(marker, source, (file.name, marker))
        self.assertNotIn("<iframe", HTML.read_text())

    def test_budget_on_demand_and_not_infinite_animation(self):
        code = JS.read_text()
        for marker in ("requestAnimationFrame(", "if(disposed||pending||document.hidden)",
                       "1200000/area", "1.5", "document.hidden",
                       "Math.min(1,", "data-frame-count", "gl.drawArrays"):
            if marker == "data-frame-count":
                self.assertIn("dataset.frameCount", code)
            else:
                self.assertIn(marker, code)
        self.assertNotIn("setInterval(", code)
        self.assertNotIn("setTimeout(", code)

    def test_fallback_context_loss_reduced_and_lifecycle_cleanup(self):
        code = JS.read_text()
        for marker in ("prefers-reduced-motion: reduce", "if(reduce.matches)",
                       "if(!gl)", "setFallback('no-webgl')",
                       "webglcontextlost", "context-lost",
                       "cancelAnimationFrame(", "removeEventListener(",
                       "deleteBuffer(", "deleteProgram(", "visibilitychange"):
            self.assertIn(marker, code)
        css = CSS.read_text()
        for marker in (":focus-visible", "@media(max-width:720px)",
                       "@media(prefers-reduced-motion:reduce)", ".skip:focus",
                       "#stage-canvas[hidden]", ".static-stage[hidden]"):
            self.assertIn(marker, css)

    def test_three_distinct_author_defined_camera_shots(self):
        code = JS.read_text()
        for name in ("arrival", "material", "precision"):
            self.assertIn("name:'" + name + "'", code)
        self.assertIn("const ease=f*f*(3-2*f)", code)
        self.assertIn("const idx", code) if "const idx" in code else self.assertIn("const i=t<.33",code)
        self.assertIn("progress=clamp(-section.getBoundingClientRect().top/span)", code)

    def test_assets_have_nonempty_hashes_and_are_not_models(self):
        digests={f.name:hashlib.sha256(f.read_bytes()).hexdigest()
                 for f in (HTML,CSS,JS)}
        self.assertEqual(len(set(digests.values())),3)
        for name,sha in digests.items():
            self.assertEqual(len(sha),64,name)
        self.assertNotIn("autonomous AI-generated", JS.read_text())
        self.assertIn("No models, remote assets or deployment", JS.read_text())


if __name__ == "__main__":
    main()
