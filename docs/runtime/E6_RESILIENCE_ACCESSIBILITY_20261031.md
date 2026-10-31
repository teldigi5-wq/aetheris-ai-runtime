# E6 Task 6 — Accessibility and WebGL context-loss browser resilience

**31 October 2026 · Research only · stacked on PR #242 · NO_GO**

## Observed gap and safe incremental change

The opt-in Blender research preview passed software-Chromium source and rendering checks, but it did not explicitly recover from a lost WebGL context or guarantee keyboard focus when the skip link was followed. Both are release-risk issues on mobile/low-memory browsers. The preview still must not claim production WCAG 2.2 AA certification or hardware GPU reliability.

`Scene3D.tsx` adds a WebGL context-loss listener with cleanup, cancels the default context-loss event, and notifies the parent to unmount the unusable Canvas. `CinematicExperience.tsx` preserves a readable static silhouette, disables optional Lenis smoothing, exposes status text and an explicit accessible `Retry 3D view` button, and recreates a new Canvas only on user action. It also adds a React error boundary for scene-rendering exceptions. The pinned ScrollTrigger playhead remains the single timeline owner; no automatic retry timer, forced wait, additional animation engine, new production dependency or remote network request is introduced.

Skip navigation targets a programmatically focusable `#studio-story` (`tabIndex={-1}`) and a minimum 44px focusable retry target. Motion-reduced visitors still receive static content with no WebGL allocation or model fetch.

## Automated browser evidence required

New Playwright Chromium/SwiftShader CI covers 1440×900 desktop, 820×1180 tablet, 320×700 small mobile, 390×844 mobile and 390×844 reduced-motion. It tests semantic main/h1/chapters, skip-link keyboard focus transfer, no horizontal overflow, deny-all external requests including a malicious URL query parameter, automatic WCAG-tagged axe-core checks, a cancelable `webglcontextlost` dispatch, Canvas teardown/status/manual 44px retry keyboard access, and real Canvas reinitialization. Includes eight full-viewport screenshots of degraded and recovered states, plus a source-backed report.

CI runs the inherited real Blender 4.0.2 source/regenerated GLB PBR, geometry/parity, source/negative tests and tracked-lock Next.js build before browser checks; the prior detailed GLB and original root experience remain unchanged. `axe-core@4.10.3` is installed only in the ephemeral CI browser-test directory, not the research Next.js production package lock.

## Evidence boundaries

An axe-core WCAG-tagged pass is **automated rule coverage only**, not manual screen-reader, keyboard, contrast-under-all-3D-frames or independent WCAG 2.2 AA certification. Dispatching synthetic WebGL context loss does not reproduce every GPU driver crash, browser context-restoration path, device memory kill, FPS/VRAM/thermal load or physical mobile GPU. Failure recovery may still require human device acceptance, browser matrix and assistive technology review. The Stage45 release gate remains **NO_GO**, no protected main merge, no site deployment and no virtual company activation.
