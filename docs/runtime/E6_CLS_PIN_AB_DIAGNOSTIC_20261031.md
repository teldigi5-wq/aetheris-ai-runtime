# Aetheris E6 — Owner-local Chrome CLS / GSAP pin-boundary A/B experiment

**31 October 2026. Experimental, unmerged, owner-local, no release sign-off.**

## Observed evidence (screenshots only)

Owner recorded manual scrolling through the unmerged Blender preview with a safe `PerformanceObserver` capturing Chrome `layout-shift` entries. Two very large individual events (scores **1.000** and **0.9989**) occurred while pinned-scene progress was near **0** and **1**, respectively; event sources were unavailable. The same page's automated three-pass scan produced repeated **~1.000** score entries near the same scene endpoints at roughly 3-second intervals. Smaller events included text nodes and one `p.e6-raf-boundary`. Screenshot observations are NOT independently acquired raw trace files or measured official page CLS under representative traffic.

**Hypothesis, not a finding:** ScrollTrigger's switch between non-pinned and pinned layout states at trigger boundaries is associated with the large layout-shift events. Normal browser scrolling is not exempt from CLS the way a recent discrete click/keypress may be. Browser-provided missing source attribution is not a basis to invent a DOM culprit.

## Local research variants

The default experience remains unchanged:

| Variant URL | What changes? | What does it test? |
| --- | --- | --- |
| `http://localhost:8765/blender-preview/` | Existing default GSAP ScrollTrigger `pin:true` with auto pin strategy | Reproduce baseline |
| `http://localhost:8765/blender-preview/?e6_cls_pin=transform` | Same pinning duration/progress, explicitly requests GSAP `pinType:"transform"` | Whether fixed-position pin transitions contribute to shifts |
| `http://localhost:8765/blender-preview/?e6_cls_pin=off` | Disable pinning but preserve scroll-trigger progress | Whether removing pinning also removes large shifts |

`transform` and `off` are **research-only counterfactuals**. They are not proposed shipping fixes: transforms can behave differently across devices, and disabling pinning changes page height/visual storytelling and may make camera motion inaccessible while visible. **Do not run the three-pass rAF benchmark in either variant**; the capture now refuses experimental modes. This is not a performance regression or UI acceptance pass.

The query parameter is accepted **only** for the `blender` asset variant on a loopback hostname. Unrecognized values, external hostnames and all standard research routes always use the default implementation. No script, CSS or production deployment is changed by query parameters in normal routes.

## Reproducible comparison on native Windows Chrome

1. Check out this draft branch and rebuild with `NEXT_PUBLIC_E6_BLENDER_PREVIEW=1`. Serve its generated `out` locally in WSL2. Keep real browser/window dimensions, DevTools docking, display scale, browser profile/extensions, reduced-motion preference and optional Lenis setting consistent across runs. Use a private incognito/clean-profile comparison if feasible.
2. Navigate to **baseline** URL, hard-refresh, open DevTools Console. Verify `document.getElementById("studio-story")?.dataset.e6ClsPin` reads `"default"`. Install the corrected nodeType-guarded `PerformanceObserver` from the owner investigation; **clear the entries on every page navigation**. Follow the same manual scroll pattern across start, middle, end, then reverse, without clicking the automated capture button.
3. Run in Console:

```js
console.table(window.__e6Shifts.filter(s=>!s.recentInput)
  .sort((a,b)=>b.score-a.score).slice(0,12)
  .map(s=>({score:s.score,timeMs:s.timeMs,
    scrollY:s.scrollYAtDelivery,progress:s.progressAtDelivery,
    sources:s.sources.map(n=>n.element).join(', ')})));
```

Save a screenshot locally, recording whether **score >= 0.5** occurs near each pin entry/exit and the manual scene appearance. Individual score entries are NOT the official page-load CLS; layout-shift session windows and shifts near recent input need proper treatment to calculate web vitals. A DOM source may be unavailable, and `scrollYAtDelivery` / `progressAtDelivery` is observer callback time rather than exact event time.
4. Open the **transform** variant in the same tab, hard-refresh, reinstall the observer and repeat the **same manual** scroll sequence. Confirm `dataset.e6ClsPin==="transform"`. Record shift scores and whether the scene remains pinned/visually usable.
5. Open the **off** variant, hard-refresh, reinstall observer and repeat. Confirm `dataset.e6ClsPin==="off"`. The section no longer pins and may move off-screen: compare only if severe CLS disappears, **not equivalent story quality or frame pacing**.
6. Return to default URL and confirm `dataset.e6ClsPin==="default"`, the preview still functions and the opt-in V2 benchmark can still start.

## Interpretation

- **Default high CLS, transform low CLS, off low CLS:** fixed-style pin transition likely relevant; inspect CSS containing blocks, spacer height and visual screenshots before attempting a permanent change.
- **Default and transform high, off low:** GSAP pinning/spacer or story layout generally implicated, not necessarily fixed-position mode.
- **All high:** another source, Chrome measurement issue or shared page content; investigate with DevTools snapshots and source attributions.
- **All low:** reproduce with previous tab dimensions or enabled extensions to isolate environment.
- **Unavailable sources** do not imply the event is false.

Never label A/B outcomes as hardware-certified or as meeting WCAG/Core Web Vitals. Real physical GPU/VRAM/thermal/mobile evidence and Task 8 independent acceptance remain open. **Task 7 incomplete, Task 8 incomplete, Stage45 NO_GO.**
