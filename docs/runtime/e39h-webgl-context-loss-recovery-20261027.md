# E3.9H — Genuine WebGL context-loss behavior and explicit reload recovery

**27 October 2026 · DRAFT · NOT MODEL-AUTHORED · NOT CERTIFIED**

This independent QA slice uses the existing E3.9H human-coded reference site. A browser GPU device/context loss must never leave an unresponsive blank canvas, continue a stale animation, or silently claim that software has recovered. An actual production system would need hardware-specific context restoration and GPU benchmarks, not just a pass on software-emulated Chromium.

Using real Playwright Chromium with software WebGL2, the test loads the local source on desktop 1280×800 and mobile 390×844. It obtains the genuine WebGL `WEBGL_lose_context` extension and calls `loseContext()`. It then checks `webglcontextlost` event handling, visible **static fallback**, hidden canvas, readable status, zero subsequent frames on scroll/resize, no JavaScript errors, no permitted external network requests, and evidence screenshot hashes.

The current reference intentionally does not implement context restoration in place: after a **context lost** event, the static fallback remains available. A **reload** is required to initialize a fresh WebGL2 context. The test explicitly reloads and observes a newly rendered view. It separately verifies `prefers-reduced-motion: reduce` does not even try to allocate WebGL2.

**zero model calls**, zero new model-authored repairs, no customer design or asset licensing, no measured owner-device GPU/thermals, no independent held-out brief, and no production agent permissions. This is a trusted reference quality control, not an E3.9H capability graduation. E4–E9 remain NOT CERTIFIED; Stage45 is NO_GO. Only real Chromium observations and artifact hashes may be reported as verified.
