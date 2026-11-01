# E6 W4-D — Software performance evidence and physical-device acceptance gate

**1 November 2026 · Research only · Stage45 NO_GO · No merge or deployment**

## Recovery

W4-D is **stacked on W4-C draft PR #264**, verified parent HEAD `bf00a3e4c7ef414f20f2ae110066f991837bbad7`. W1/W2-A/B/C/W3-A/B/W4-A/B/C remain draft/unmerged with their previous target directories unchanged. Runtime protected main `65a6262717adcd52ac8d8a16ed6f223e299fd74d`; Platform main `57ad3e0e602efa6feb275328254151b959a3499b` remain independent.

## What W4-D demonstrably measures

- `w4d_run_browser_audit.mjs`: uses an exact, fixed local W4-C site on 127.0.0.1, a read-only software Chromium renderer (SwiftShader), and a locked first-party GLB/model/assets integrity check. Profiles: desktop 1440×900, mobile 390×844, compact 320×700, reduced-motion 390×844, and JavaScript-disabled 390×844.
- Collects browser navigation `loadEventEnd` duration, first-contentful-paint candidate, instrumented LCP candidate (if observed), CLS during the test journey, real static Blender-fallback screenshots, exact locally requested resource names, and scroll-to-WebGL-*render observed* durations. These timings describe **CI browser sessions only**. They do NOT establish laboratory-comparable GPU frame times, user-device INP, 75th-percentile Core Web Vitals or real-world production telemetry.
- Tests that the heavy 738KB-ish Three/GSAP engine and original GLB were never fetched before explicit user activation; that optional 3D then loads exactly once and renders at three genuinely distinct camera frames with source-validated GLB hash. The compact profile uses keyboard-only activation.
- Leaves reduced-motion and JavaScript-disabled profiles static without heavy scene/GLB requests. All views must retain chapters, existing real Blender still, no browser errors, no unexpected external requests and no horizontal overflow beyond 2 CSS pixels.
- Emits **write-once**, review-only `target/e6-studio/w4d-browser-evidence/w4d-software-browser-evidence.json` and per-profile static WebP/PNG-supported browser screenshots (PNG in this run) plus three WebGL camera captures each where enabled. The evidence document explicitly sets `physicalGpuMeasured=false`, `ownerDeviceVerified=false`, `wcagCertified=false`, `independentVisualApproval=false`, `aiModelAuthored=false`, `productionReady=false`, `stage45Go=false`.
- `w4d_evidence_contract.mjs` enforces claim integrity, deterministic five-profile completeness, resource budgets, no eager engine/model fetch, exception-free browser navigation, and bounded output. `test_w4d_evidence_contract.mjs` includes six negative/policy tests, including forged owner approval and premature production claims.
- `.github/workflows/runtime-e6-w4d-performance.yml`: read-only exact-head CI re-runs all W1-W4C Blender/website/Next/static/real Chromium steps plus W4-D tests. CI retains only 7-day transient research artifacts. No device hardware access is inferred from GitHub runner hardware.

## Resource budgets

Source-verified W4-C starting point: **1,139-byte initial controller**, **738,079-byte optional 3D module**, **72,724-byte GLB** (all uncompressed, model/source SHA checked). Those are engineering observations from previously verified CI, not necessarily total network transfer size. Initial 3D engine bytes are not downloaded until explicit activation. The optional module remains substantial and requires further profiling/compression/splitting. No unmeasured FPS or Core Web Vitals improvements may be claimed.

## Physical-device exit gate — not completed by W4-D CI

See `docs/runtime/E6_W4D_OWNER_DEVICE_REVIEW_TEMPLATE.md`. It provides a reproducible owner-browser performance and a11y checklist. The actual Windows GPU renderer/driver, CPU/GPU usage, memory, thermal behavior, Safari/Firefox/Chrome variations, real touch/mobile behavior, physical keyboard/screen-reader review, creative merit and load time under real networks **cannot be established by SwiftShader tests**.

After obtaining owner measurements and art-direction signoff, a reviewer may decide on a separate device-quality GO, not a Stage45/production release GO. Until then W4-D remains `HARDWARE_EVIDENCE_PENDING`, with owner authorization, independent visuals, genuine AI-authored website code and runtime/platform security issues still blocked.

## Honest next work

Complete physical-device W4-D gate and visual quality review with actual WSL2/Windows/Android hardware evidence; independently review accessibility and optimized module sizes. Then begin W5 **real model-generated design/code from held-out briefs**, with forbidden template memorization and source-evidence validation. E6 Task7/8, Runtime owner/project SSE auth #23, Platform service auth #251, nine Stage45 production attestations and owner-PC GO remain outstanding. **Do not merge or deploy.**
