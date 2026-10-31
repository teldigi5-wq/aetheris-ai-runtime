"""Source-level negative guards for E6 accessible WebGL failure/retry research."""
from pathlib import Path
import unittest

ROOT = Path(__file__).parent / "next-studio"


class E6ResilienceControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.scene = (ROOT/"components/Scene3D.tsx").read_text(encoding="utf-8")
        cls.story = (ROOT/"components/CinematicExperience.tsx").read_text(encoding="utf-8")
        cls.css = (ROOT/"app/globals.css").read_text(encoding="utf-8")
        cls.proof = (ROOT/"browser_resilience_accessibility.mjs").read_text(encoding="utf-8")

    def test_explicit_context_loss_and_cleanup(self):
        self.assertIn('"webglcontextlost"', self.scene)
        self.assertIn("event.preventDefault()", self.scene)
        self.assertIn('removeEventListener("webglcontextlost"', self.scene)
        self.assertIn("onUnavailable();", self.scene)
        self.assertIn('frameloop="demand"', self.scene)

    def test_user_controlled_fallback_no_retry_loop(self):
        self.assertIn('data-recovery={unavailable?"needed":"none"}', self.story)
        self.assertIn("setSmooth(false);", self.story)
        self.assertIn("setSceneRevision(n=>n+1)", self.story)
        self.assertIn("Retry 3D view", self.story)
        self.assertIn('class SceneErrorBoundary', self.story)
        self.assertNotIn("setInterval(", self.story)
        self.assertNotIn("setTimeout(", self.story)
        self.assertNotIn("window.location.reload(", self.story)

    def test_keyboard_accessible_story_and_retry(self):
        self.assertIn('tabIndex={-1}', self.story)
        self.assertIn('href="#studio-story"', self.story)
        self.assertIn('className="scene-retry" type="button"', self.story)
        self.assertIn("pointer-events:auto", self.css)
        self.assertIn("min-height:44px", self.css)
        self.assertIn(".scene-retry:focus-visible", self.css)

    def test_negative_external_loading_and_a11y_proof(self):
        self.assertIn("axe.run(document", self.proof)
        self.assertIn("wcag22aa", self.proof)
        self.assertIn("webglcontextlost", self.proof)
        self.assertIn("contextLossPrevented:true", self.proof)
        self.assertIn("noAssetFetch:true", self.proof)
        self.assertIn("untrusted.example", self.proof)
        self.assertIn("assert.deepEqual(outside,[]", self.proof)
        self.assertIn("stage45Go:false", self.proof)


if __name__ == "__main__":
    unittest.main()
