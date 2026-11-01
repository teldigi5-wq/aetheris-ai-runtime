# E6 W4-D — Owner-device review worksheet (unfilled, intentionally)

**Status: NO_GO / NOT MEASURED.** This file is a worksheet, not evidence of a completed test. No values should be invented or filled by CI.

## Hardware identification and isolation

Record date, exact W4-D Git SHA, OS, browser version, device model, GPU model, power profile (AC or battery), display scale, browser flags, measured viewport and network condition. In Chrome, inspect `chrome://gpu` to verify that the active renderer uses real hardware or is software fallback. Record whether the actual physical GPU is used; do not infer it from `WebGLRenderingContext` availability alone. Never upload GPU serial numbers, secrets, personal data or private screenshots.

Use a **new detached W4-D worktree**, not an existing W2/W3/W4 preview. Regenerate W3-A/B, W4-B/C only as described in their source-checked docs. Review the W4-C preview from `target/e6-w4c-arcloom` using a localhost-only Python server. W4-D's CI measurement runner must not be mistaken for a physical-device test.

## Run protocol, three repetitions per device/profile

1. **Before 3D activation:** capture DOM content visibility, 320/390/768/1024/1440 widths where supported, static Blender still, JS enabled/disabled, zoom to 200%, `prefers-reduced-motion: reduce`, focus/Tab order, absence of horizontal overflow and blocked external connections. Note evidence file names.
2. **After intentional activation:** record 3D startup time in Chrome DevTools Performance using the actual GPU, memory use (before, after and after context loss), frame pacing under at least three native-scroll traversals, and interaction responsiveness with keyboard/touch. Capture p50/p95 **frame-time distributions** from the same profiling tool and state whether rasterization is GPU-backed. A 60fps target is aspirational, not a pass inferred from screenshots.
3. **Reliability cases:** deny WebGL, trigger actual context loss (where possible), disconnect network after page loaded, corrupt an isolated research copy of the GLB, reload, and verify static content, clear status, navigability and cleanup. No random production URLs or unsafe privilege manipulations.
4. **Accessibility:** manual keyboard-only focus and activation, screen-reader narration (NVDA on Windows, VoiceOver on macOS/iOS or TalkBack on Android if present), logical chapter landmarks, reduced-motion behavior, text contrast, 200% zoom and touch targets. WCAG 2.2 AA remains unverified until independent audit.
5. **Network and Core Web Vitals:** with device and throttling profile documented, run Lighthouse/DevTools multiple times and record raw LCP/CLS/INP (if available). INP must be gathered from appropriate real user interaction/field telemetry; do not claim synthetic click latency is genuine production INP. Real-world percentile Core Web Vitals require representative field evidence.
6. **Creative review:** verify the model is visibly coherent, materially convincing, lighting is intentional, fallback still is not noisy/low-resolution, scroll transitions are natural, no brand mismatch, no cartoonish aesthetic, and still content is worthwhile without 3D. Review actual screenshots; CI screenshot hashes only prove frames differ.
7. **Decision:** reviewer signs `APPROVED_FOR_NEXT_RESEARCH_PREVIEW` or `BLOCKED`, with exact evidence, deficiencies, corrective issues and verified hardware detail. No automatic promotion to main, preview deployment, commercial licensing approval or Stage45 production GO.

## Fill only from actual owner-run evidence

| Field | Actual measured value / evidence link |
|---|---|
| Device / OS / browser / viewport | NOT MEASURED |
| Real GPU / driver / renderer verification | NOT MEASURED |
| Network profile / cache state | NOT MEASURED |
| Initial LCP / CLS / INP source | NOT MEASURED |
| 3D activation cost / memory change | NOT MEASURED |
| Scroll p50 / p95 frame times | NOT MEASURED |
| Reduced-motion/keyboard/screen reader | NOT MEASURED |
| Physical thermal/battery observation | NOT MEASURED |
| Human-art-direction review | NOT APPROVED |
| Exact W4-D commit and test logs | NOT SUBMITTED |
| Reviewer decision | BLOCKED_PENDING_OWNER_HARDWARE |

**Never claim performance or visual approval from GitHub's SwiftShader-only CI runner.** W5 genuine model-generated website capability remains an independent requirement.
