# Aetheris Enterprise E6 — Chrome Performance trace with verified 3D scroll markers

**31 October 2026 · Task 7 diagnostic only · NO-GO release gate**

## Purpose and difference from the successful V2 rAF screen

Two prior owner-local V2 captures (6 forward/backward runs, 2,160 intervals) passed their **browser callback pacing** screen. A requestAnimationFrame callback timestamp is **not** the measured duration of a GPU rendering task, actual monitor-presented FPS, sustained thermal capacity or energy usage.

This branch adds *explicit User Timing markers* to the **existing user-clicked V2 capture** and a **read-only local trace inspector** for Chrome DevTools exports. There is no new autonomous capture, customer-facing page, network request or telemetry upload. The browser controls remain on the build-flagged `/blender-preview/` localhost research page only. Current device has self-reported Intel UHD Graphics / D3D11; do not assume NVIDIA hardware.

## Windows Chrome walkthrough

1. **Rebuild this exact draft branch** with Node 22 and `NEXT_PUBLIC_E6_BLENDER_PREVIEW=1 npm run build`. Confirm the browser bundle contains `E6_TRACE_V1_RUN_`; preserve the existing optimized 83,140-byte Blender GLB and its SHA256.
2. Serve `out` over `python3 -m http.server 8765 --bind 127.0.0.1 --directory out` in WSL2. Open `http://localhost:8765/blender-preview/` in native Windows Chrome, hard refresh and confirm the real 3D scene is visible. Ensure optional Lenis smooth scroll **Off** and reduced motion **Off** for this motion diagnostic.
3. Press F12 → **Performance**. Enable **User timings** in track settings if needed; for separate GPU activity select **GPU** track if available. Use a clean Chrome profile without optional extensions for the comparison. Leave power configuration unchanged during each comparison and record it manually.
4. Click **Record**, scroll to the local `Measure the scroll, not a claim.` panel, and click **Start three-pass local trace**. Keep the Chrome tab visible until the page reports completion. The app emits exactly four fixed markers for each verified run: `E6_TRACE_V1_RUN_1_START/APEX/END/VERIFIED` and corresponding runs 2, 3.
5. Stop the DevTools recording. Inspect Frames and Main timeline; do not infer actual GPU render-time solely from Chrome's event names. Select **Save trace**. **Uncheck Include resource content and Include script source maps**; annotations should contain no sensitive text. Keep raw trace private—it can include file paths, page URLs, stack traces, source, headers, extension metadata or user data.
6. Move the raw `.json` or `.json.gz` file to a **private local folder not tracked by Git**. With Node 22 in WSL, run the read-only command (replace the placeholder with your actual filename):

```bash
cd ~/projects/aetheris-ai-runtime
nvm use 22
node tools/enterprise/e6_studio/next-studio/review_local_chrome_trace.mjs \
  --trace "$HOME/e6-private-evidence/<your-devtools-trace.json.gz>"
```

The CLI writes only a **redacted aggregate JSON summary to stdout** and does not write files or make network requests. Inspect it before sharing. Never commit your raw traces, source maps, URLs, tokens or private filesystem paths.

## Trace coverage and failure handling

The inspector supports common DevTools `traceEvents` object and event-array JSON forms, including gzip, with strict size caps (64 MiB input, 128 MiB after decompression), regular-file checks and exact marker names. It **requires** three non-overlapping ordered run windows `START < APEX < END < VERIFIED`, each 1–45 seconds. Duplicate, missing or forged-sequence markers reject the trace. If Chrome omits user timings, the review **blocks** with a missing-marker diagnostic, not a passing assessment.

The inspector identifies renderer Main thread when metadata explicitly names `CrRendererMain`, `RendererMain` or `MainThread` and summarizes sampled complete-duration `RunTask` / `ThreadControllerImpl::RunTask` events within marker windows. It reports counts over 50ms and over 200ms plus maximum observed task duration. It **never sums nested durations**, claims exhaustive tasks or equates these events to GPU draw time. If the expected event category or Main thread metadata is missing, it declares task coverage **INSUFFICIENT**, *not* a low-jank pass. The raw trace is not a complete or authenticated record of all GPU operations; DevTools recording overhead can distort performance. Recent Chrome builds may change the trace format; the CLI must fail closed rather than fabricate conclusions.

No reporting format or supplied metadata can flip `task7Completed=false`, `task8Completed=false`, `stage45Go=false`, `releaseGo=false`. Trace review does not fill the remaining 9-domain Task 8 independently authenticated evidence matrix.

## Separate required physical work

- Capture a **sustained** real-device GPU and thermal/power log using vendor/OS tooling with reliable timestamps and a manually recorded device/power-mode profile. Disclose that Chrome's DevTools GPU activity track alone is not a direct temperature/VRAM measurement.
- Test physical Android and iOS devices, landscape/touch/keyboard, reduced motion, WebGL loss/recovery and cross-browser behavior.
- Complete actual independent accessibility, security, dependency/license provenance and original visual-design reviews before any release promotion.

**Only local research has been verified; no release authorization, no production deployment, Stage45 NO_GO.**
