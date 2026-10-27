"""E3.9H context loss and recovery source safety/provenance controls."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[3]
HERE=Path(__file__).resolve().parent
SCRIPT=(HERE/"e39h_context_loss_browser.mjs").read_text()
SCENE=(HERE/"e39h/reference/scene.js").read_text()
WF=(ROOT/".github/workflows/runtime-e39h-webgl-context-recovery.yml").read_text()
DOC=(ROOT/"docs/runtime/e39h-webgl-context-loss-recovery-20261027.md").read_text()

class WebglContextRecovery(unittest.TestCase):
    def test_existing_context_loss_remains_safe_by_default(self):
        for field in (
            "event.preventDefault()", "disposed=true;",
            "showFallback('context-lost')",
            "cancelAnimationFrame(frame)",
            "canvas.addEventListener('webglcontextlost',lost,false)",
            "if(disposed || !gl || gl.isContextLost()) return;",
            "if (reduce.matches) { showFallback('reduced-static'); return; }",
        ):
            self.assertIn(field,SCENE)

    def test_actual_browser_loss_reload_and_no_network_exfiltration(self):
        for field in (
            "chromium.launch(", "WEBGL_lose_context",
            "ext.loseContext()", "page.waitForFunction",
            "canvasHiddenDuringLoss", "stoppedFrames",
            "page.reload(", "recoveredAfterReload",
            "await page.route('**/*'", "route.abort()",
            "reducedMotion:'reduce'", "webglAllocationAttempts",
            "genuineModelCalls:0", "stage45Go:false",
            "ownerHardwareBenchmarked:false", "performanceSloProven:false",
            "explicitReloadRequired:true"
        ):
            self.assertIn(field,SCRIPT)
        self.assertNotIn("GenUI",SCRIPT)
        self.assertNotIn("child_process",SCRIPT)
        self.assertNotIn("execSync",SCRIPT)
        self.assertNotIn("https://",SCRIPT)
        self.assertNotIn("process.env.API_KEY",SCRIPT)

    def test_exact_head_bounded_browser_ci_and_honest_claims(self):
        for field in (
            "github.event.pull_request.head.sha",
            "persist-credentials: false",
            "node --check", "e39h_context_loss_browser.mjs",
            "npm install --prefix tools/enterprise/e3_factory",
            "playwright install --with-deps chromium"
        ):
            self.assertIn(field,WF)
        for field in ("NOT MODEL-AUTHORED","NOT CERTIFIED",
                      "reload", "static fallback", "context lost",
                      "zero model calls", "owner-device",
                      "production"):
            self.assertIn(field,DOC)

if __name__=="__main__":
    unittest.main()
