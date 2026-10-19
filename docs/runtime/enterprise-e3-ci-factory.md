# Stage E3.0 — Real cinematic website factory, CI-only

**DRAFT / NOT OWNER-PC CERTIFIED / NO AUTONOMOUS AI MODEL / NO PRODUCTION DEPLOYMENT**

## What the worker ACTUALLY does

Run `python tools/enterprise/e3_factory/worker.py` from this Git repository on a disposable CI runner. The Python-stdlib-only worker reads a **frozen, allowlisted project brief** and generates a genuinely functional multi-file website in the fixed repository-local directory `target/e3-factory/site/`.

Generated files:
- `index.html` — semantic responsive microsite with accessible navigation and a scroll-controlled reveal stage.
- `styles.css` — premium dark editorial design, a constructed laptop made from CSS layers with perspective, and reduced-motion behavior.
- `main.js` — real `requestAnimationFrame` scroll calculations, progress/ARIA updates and CSS 3D product rotation **in both scroll directions**.
- `manifest.json` — exact generated file hashes and truthful implementation/evidence claims.
- `SHA256SUMS` — independently recomputable UTF-8 file checksums.

The output is a **working HTML/CSS/JavaScript website prototype**. It contains *real responsive scroll interaction*, not a static graphic pretending to animate. The laptop is constructed with **CSS 3D transforms**, not Blender meshes, pre-rendered frames, physically accurate 3D material or WebGL. Those are E5/E6 work and must never be marked implemented by this slice.

The deterministic worker executes without LLM calls, shell command execution, remote downloads, API keys, GitHub writes, network requests, secrets, private home-directory access, paid services or production publishing. It accepts no arbitrary file or output destination parameters. It refuses overwrite or symlinked target directories. Output is limited to exactly five named files. The fixture schema rejects unknown executable instruction fields.

## What CI verifies

**Ubuntu 24.04 and Windows 2022**:
- Run the actual Python worker and check the output was generated.
- Check output scope and SHA256 checksum integrity.
- Reject hostile `../` project IDs, unknown fixture keys, malformed descriptions and template injection.
- Check that the site JavaScript parses with Node.

**Ubuntu headless Chromium, via Playwright 1.55.0**:
- Start an HTTP server bound only to `127.0.0.1` on the disposable runner.
- Confirm title, content, accessibility progress role.
- Scroll into the experience at early/late progress and check CSS 3D `transform` and ARIA values truly change.
- Scroll back up and verify progress/transform truly reverse.
- Test 390-pixel mobile viewport, real interactive progress and absence of horizontal overflow.
- Test `prefers-reduced-motion: reduce` and confirm animation is disabled while semantic content/skip link remains.
- Reject browser JavaScript exceptions/console errors.
- Upload actual generated HTML/CSS/JS, desktop/mobile screenshots and `qa-report.json` as short-lived GitHub Actions artifacts.

The browser QA report contains direct measured browser observations only. It does **not** assert measured Core Web Vitals, photorealistic fidelity, GPU budgets, host runtime security, user acceptance or production availability.

**Security static guard**:
- Worker lacks shell/network/owner-device integration.
- Frontend contains no network telemetry or arbitrary code evaluation.
- Zero owner action approvals, token grants, financial tools or external publishing.
- Stage45 actual 13-blocker release tests are **not available on this E2.2D-derived branch** and have **not** been executed here. Real owner PC / production remains NO_GO.

## What this proves and does NOT prove

This is the first implemented **CI-only deterministic software factory**: given the checked-in specification, it creates executable source files and has a real-browser verification pipeline. It is a meaningful step past our static E2.2A storyboard.

It is **not** an LLM-driven autonomous engineering company. It does not perform iterative model-led code generation, production DevOps, a real backend, authenticated ownership, automatic fix loops, WebGL/Three.js rendering, Blender frame sequences or physical Windows-machine actions.

The previously implemented 13-company / 100-division organizational blueprint stays PROPOSED and non-executing. The 26 identities on protected main / 30 in unrelated unmerged draft stacks must not be conflated with running agent workers.

## Next E3 engineering gates

1. Real browser QA must be green on exact-head CI. Use the stored screenshots/report to identify and fix visual, mobile and accessibility defects rather than claiming quality from source alone.
2. Add a strict **disposable sandbox worker supervisor**, CPU/memory/runtime/output controls, deterministic cancellation and approved test-only tool mapping; no owner PC runtime.
3. Add a bounded build→test→diagnose→fix→retest loop with verifiable staged PR artifacts, initially using fixed test fixtures.
4. Enable a separately measured local AI adapter only after the executor, data isolation and Stage45/owner trust boundaries are verified.
5. Expand from CSS 3D to a real user-approved photoreal sequence/Blender pipeline and/or Three.js scenes with browser rendering measurements in E5/E6.

Dependent review sequence: E0 #102 → E1 #104 → E2.1 #106 → E2.2A #107 → E2.2B-1 #109 → PostgreSQL #110 → signed proof #112 → nonce #113 → candidate key #114 → this E3 draft. Protected `main` is untouched.
