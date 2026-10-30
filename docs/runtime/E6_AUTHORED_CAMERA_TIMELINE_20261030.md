# Enterprise E6 Task 4 — Authored cinematic camera timeline

**30 October 2026. Draft research. NO PROD / NO MAIN MERGE / Stage45 NO_GO.**

## Lineage and precise scope

Stacks on Runtime PR #239, verified `f92c832ebfa0e6a6704145b613ba832ea9324fbf`. The existing R3F app already used GSAP ScrollTrigger to pin a three-act story and a camera path driven by scroll progress; this increment formalizes a three-keyframe cinematic score rather than installing Theatre.js, a second scroll engine or a new dependency. It does not change the GLB asset, automatically adopt the separately created Blender model, edit the license/optimization stacks or deploy a site.

## Authored camera score

| Playhead | Act | Position (world units) | Perspective fov |
| --- | --- | --- | --- |
| 0.0 | Arrival | (-2.25, 1.10, 4.65) | 38 degrees |
| 0.5 | Material | (2.10, 0.90, 3.90) | 34 degrees |
| 1.0 | Precision | (0.95, 0.70, 2.85) | 30 degrees |

The pure `cameraTimeline.ts` module clamps all finite progress to [0,1], maps invalid input to arrival, and samples keyframe sections with cubic smoothstep interpolation. This is deterministic and reversible: the same scroll progress produces the same camera pose. R3F remains `frameloop="demand"`; camera pose, look-at and perspective-projection updates are applied inside the existing useFrame callback, with no autonomous RAF or simulated physics loop. The only progress owner is GSAP ScrollTrigger. Shot labels and observed camera coordinates/fov are also represented as page data attributes for browser tests and evidence inspection.

## Accessibility and quality requirements

- The previously accessible semantic chapters, skip navigation, focus styles and native scroll defaults remain. Optional Lenis remains off until an explicit visitor action. No scroll hijack, autoplay or forced waiting.
- If the visitor prefers reduced motion, there is no R3F Canvas allocation. Story stays readable in the static silhouette fallback; smooth scrolling stays disabled and the timeline defaults to Arrival.
- Source invariant checks compile and execute the **actual TypeScript sampler** in Node and verify all three authored coordinates/fov, 101 contiguous samples, monotonic fov, bounded per-step camera displacement, deterministic reverse behavior and invalid inputs.
- Software-Chromium E2E tests drive the **actual Next.js static export** in desktop/mobile, check all three acts, reverse, real canvas changes, no horizontal overflow and zero external requests; reduced-motion must not allocate a canvas. Re-run inherited R3F browser regressions.

## Not included / gates still open

No vendor asset authoring import, Theatre.js installation, shader/postprocess/bloom/DoF, measured visual camera-composition approval, hand-sculpted production hero, physical NVIDIA/AMD/Apple GPU profiling, 60-fps/frame-time percentiles, VRAM/thermal budget, independent WCAG verification or real-device browser matrix. The stored research screenshots are software-WebGL only, not independent design approval. Stage45 remains 0/9 certified, **NO_GO**. This is not E7/E8/E9 production capability, a main merge, or a deployment.

## Visual QA correction after the first browser pass

The initial screenshots (CI run 41073126863) passed functional/scroll tests but exposed **insufficient text separation on mobile** and an oversized Precision composition crossing desktop copy. Passing E2E tests alone is not visual design approval. This draft adds an opaque CSS editorial luminance scrim over the render (desktop left-to-right; mobile top-to-bottom) without glassmorphism or content blurring. On narrow viewports the visual panel starts at 34% of the story height, leaving the top readable without covering the 3D with text. Browser tests additionally assert the scrim exists and the mobile 3D stage is below the editorial header, including reduced-motion visitors.

The adjustments must pass a new exact-head Chromium test and be inspected visually before Task 4 can be considered a satisfactory research milestone. A deterministic contrast scrim is not an independent WCAG 2.2 AA audit or professional design acceptance.

## Full-viewport review evidence

The first pass after the contrast/safe mobile composition fix (run #41073876873) succeeded on exact-head CI but the earlier screenshot harness recorded only WebGL canvas crops. The subsequent increment captures **six full-viewport screenshots** (Material, Precision, reverse Arrival on desktop and mobile) alongside six canvas-only samples, so the rendered object, text, scrim and progress labels can be reviewed together. The new final exact-head CI is required to validate 12 screenshot artifacts. Human visual review and physical-device GPU benchmarking remain separate open gates.
