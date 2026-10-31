# Enterprise E6 Task 7 — Owner-clicked native Windows Chrome rAF diagnostic

**31 October 2026 · Browser-side field instrumentation only, Stage45 NO_GO.**

## What this implements

The opt-in Blender research preview (`NEXT_PUBLIC_E6_BLENDER_PREVIEW=1`, route `/blender-preview/`) now contains an optional, visibly labelled **Local browser frame pacing** panel after the storyboard. It appears only when opened on `localhost`, `127.0.0.1` or IPv6 loopback. You can serve the previously generated WSL static export at `127.0.0.1:8765` and use **native Windows Google Chrome** to run this capture. Unlike `owner_gpu_benchmark.mjs`, this path does not require installing Chrome, Git or Node in Windows.

The panel never runs automatically. It requires an explicit user click and a visible browser tab with the real `r3f-loaded` WebGL2 scene. The capture scrolls the actual scene forward and backward three times, measuring **360 `requestAnimationFrame` intervals per run**, sampled actual `data-progress` values, optional browser long-task observations and unexpected JavaScript/WebGL errors. A Cancel control aborts sampling; visibility loss also aborts. The original scroll position is restored and the owner must separately click **Save JSON on this device** to download the results. No telemetry, image, source file, browser history or collected result is uploaded.

## Run on your existing WSL/Windows configuration

1. Inspect tracked modifications with `git status --short` and `git diff -- tools/enterprise/e6_studio/next-studio/next-env.d.ts`. If the only modification is a generated Next.js reference and no intentional edit, restore that **single** file. **Do not delete** local `target/` Blender assets or untracked dependencies.
2. In WSL, fetch and switch to `feat/e6-native-browser-raf-capture-20261031`. Confirm `git rev-parse HEAD` and the GitHub origin point to `teldigi5-wq/aetheris-ai-runtime`.
3. Rebuild the isolated preview using the verified Node 22 environment: `cd tools/enterprise/e6_studio/next-studio && npm run typecheck && NEXT_PUBLIC_E6_BLENDER_PREVIEW=1 npm run build`. Previously verified original Blender `.blend`, staged `e6-blender-preview.glb`, `target/e6-studio/blender-preview-stage.json` and installed npm packages should remain local, but verify those files and SHA match after rebuild.
4. Serve the `out` directory from the repo root with `python3 -m http.server 8765 --bind 127.0.0.1 --directory tools/enterprise/e6_studio/next-studio/out`. Open `http://localhost:8765/blender-preview/` in **native Windows Chrome** and confirm actual `r3f-loaded` scene.
5. Scroll to the bottom local capture panel, click **Start three-pass local trace**, keep tab visible, then click **Save JSON on this device**. A file named `e6-local-raf-*.json` is saved under your browser's ordinary download location. No credentials are requested.
6. Inspect the JSON for browser version and unmasked renderer string before sharing. The file may reveal your device/GPU identity and local-performance information; keep it private unless you intentionally share a redacted copy. Preserve the original raw file locally for later forensic review.

## 31 October owner capture postmortem — stationary scene rejected

A genuine native Windows Chrome localhost report exposed a flaw in the original test harness (PR #248, original V1): all **three runs** recorded `progressMinimum=progressMaximum=1`, `reverseObserved=false`, even though each collected 360 `requestAnimationFrame` intervals close to 16.7ms. The submitted assessment correctly returned `diagnosticValid=false`. That sample is a **stationary-scene timing observation**, not a successful scroll benchmark and not proof of smooth 3D motion. The raw report was **not** committed, published or copied to the repository.

The root-cause candidate was computing `start = window.scrollY + story.getBoundingClientRect().top` while GSAP pinning was active. An element's transformed/pinned client rectangle does **not** reliably indicate ScrollTrigger's scroll start. The owner-click capture now selects the existing trigger by `trigger.trigger === #studio-story` and reads **actual numeric** `ScrollTrigger.start` and `ScrollTrigger.end`. If the trigger/range is unavailable, the optional Lenis mode is on, or the scene fails to reach start progress near zero, capture **stops with a diagnostic** rather than collecting idle intervals. Every run now separately checks start <=0.15, midpoint >=0.85, end <=0.15, actual `scrollY` traversal >=80% of measured pin range, and reverse observation.

**Schema bumped to `E6_LOCAL_BROWSER_RAF_DIAGNOSTIC_V2`, `schemaVersion:2`.** Do not rewrite or relabel previous V1 files as V2. The new V2 policy rejects stationary and forged-progress synthetic fixtures. The change has automated CI coverage, but does not prove the physical Windows Chrome capture works until the owner repeats the experiment with the updated build.

## Interpretation and strict boundaries

The pure offline `local_browser_raf_policy.mjs` checks three bounded 360-sample runs, real progress minimum/maximum and reverse observation, browser error/context-loss counts, WebGL2, opt-in localhost scope, SHA256 of the locally fetched original GLB file and supported long-task observer. It computes nearest-rank rAF p50, p95, p99 and >33.3ms ratio. Default **preliminary screen**: p95 <=33.3ms, p99 <=50ms, <=5% frames >33.3ms, no long task >200ms. A good diagnostic indicates only a particular browser window's scheduling experience; DevTools recording, optional extensions, tab focus, refresh rate and OS power modes affect it.

**CRITICAL:** A native Chrome rAF interval is *not* actual GPU draw-call time or a reliable 60 FPS certification. A report's renderer string and local hash are self-reported, and a self-calculated file hash is not independent build provenance. This browser report deliberately omits `exactGitCommit` certification and `cleanSourceCheckout` attestation: those must be separately checked from the verified git working tree. It cannot be passed off as an `owner_gpu_benchmark.mjs` evidence package and must not be fed to that stricter policy as if the formats were interchangeable.

Task 7 additionally requires separate hardware evidence (desktop and real mobile, GPU/VRAM/thermal/power/sustained performance). Task 8 still requires authenticated independent accessibility, security, licensing and design review. All result objects **permanently** return `physicalHardwareCertified=false`, `task7Completed=false`, `task8Completed=false`, `stage45Go=false`, `releaseGo=false`, even when preliminary browser pacing passes.

## Verification

CI runs Node 22 synthetic positive/negative policy tests, verifies no-go invariants, validates the dependency lock and performs strict Next.js TypeScript/static export checks. A cloud CI runner cannot verify native Windows browser hardware or the above owner's actual video/device identity. No customer data, runtime agent, model access, deployment or protected branch changed.
