---
name: aetheris-web-performance
description: Audit responsive media, realistic cinematic motion, GSAP and WebGL budgets, Web Vitals and graceful fallback with measured evidence.
---

# Aetheris Cinematic Web Motion & Performance
First-party read-only reference/advice only; no external requests, dependency installation, file mutation, model calls, activation or automatic release.

## Performance budget, not visual gimmicks
Target an *observed* stable 60 fps on specified supported hardware, not a blanket guarantee. Measure p50/p95 frame times, dropped frames, main-thread long tasks, input response, GPU/CPU/memory consumption and thermal/battery behavior where possible. Target (not claim) field Core Web Vitals p75: LCP <= 2.5s, INP <= 200ms and CLS <= 0.1 on representative traffic. Record exact device/browser, viewport, connection, run count, source SHA, trace and reproducibility; synthetic CI is not production field data.

## Choose the right motion and rendering primitive
- CSS transforms/opacity, WAAPI, or light Motion/React Spring for uncomplicated interactions; composited changes over layout/paint thrashing.
- GSAP timelines and ScrollTrigger only when complex choreography justifies the code. Bound scroll/resize listeners, refresh after layout changes, clean up on route unmount, prevent duplicate trigger creation, avoid forced reflow and coordinate backward scrolling.
- Optional Lenis smooth-scrolling only after preserving native touch scrolling, anchors, keyboard navigation and back/forward with a tested opt-out.
- Use WebGL/WebGPU/Three.js/R3F and GLSL selectively for actual product/spatial interactions, never gratuitous neon/wireframe backgrounds. Prefer pre-rendered Blender/Cinema4D frame sequences or optimized video over live 3D when fidelity/cost demands it.
- Clamp device pixel ratio (DPR), reduce geometry, shadow passes, particles and post-processing on weaker devices, stop hidden/offscreen loops, lazy-load 3D, dispose geometries/materials/textures/canvases, provide a functional static/2D/no-WebGL fallback.
- Responsive srcset/AVIF/WebP, video posters/adaptive delivery, format/license checks, font subsetting, critical CSS, route code splitting, no unnecessary third-party trackers or mandatory paid rendering APIs.

## Release checks
Measure actual desktop AND mobile browsing, cold/warm loads, 320-1920px responsive layout, touch/keyboard, scroll reversal, resize, reduced motion, high latency, failure to initialize graphics and route cleanup. Check accessible content before scripts load and no layout shifts from animated reveals.
Performance claims must link raw evidence; don't report '60 FPS' or Core Web Vitals as achieved from estimates. Preview, explicit owner approval and independent QA/security review remain required; optimizing must not regress semantics, search indexing, content visibility or existing features.