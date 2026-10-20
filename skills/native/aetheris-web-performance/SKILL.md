---
name: aetheris-web-performance
description: Audit UI runtime costs, rendering performance, 3D/WebGL budgets and responsible asset delivery without guessing metrics.
---

# Frontend and Motion Performance
Identify navigation lifecycle, image/video asset weights, font strategy, responsive srcsets, lazy loading, script cost, hydration, layout thrashing, animation paint/composite properties, long tasks, layout shifts and GPU-heavy 3D.

Prefer CSS/WAAPI and composited transforms where possible. Use GSAP/ScrollTrigger only for interactions that require it; gate scroll handling, dispose listeners and canvases, and provide non-WebGL/reduced-motion alternatives.

Collect actual lab traces / DevTools / Lighthouse or Playwright timings on disposable CI when authorized. Record exact browser, hardware class, viewport, cache state, run count and trace artifact. Never invent Core Web Vitals or 60fps guarantees. Proposed optimizations must retain keyboard accessibility and the existing UI intent.