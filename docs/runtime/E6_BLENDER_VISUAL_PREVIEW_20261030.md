# Enterprise E6 Task 5 — Original Blender concept visual preview

**30 October 2026 · UNMERGED DRAFT · RESEARCH ONLY · Stage45 NO_GO.**

## Source and ancestry

Stacked on Runtime PR #240 at exact verified HEAD `092b5ca67696fdcb6ff30fa4088ac2997cf4b3af`; preserves the original Next.js/GSAP/R3F three-act experience, camera score, source-policy and reduced-motion behavior. This is not a published customer page.

## Intent and observed baseline

Task 4 successfully verified human-authored camera keyframes on a synthetic grey asset, but visual review found that using only a placeholder is inadequate evidence for the desired material-driven experience. This increment introduces an **isolated opt-in preview** of the real original Blender-authored Aetheris conceptual command module, with editorial charcoal, physically based silver/graphite/alloy/amber materials, bounded directional lighting and a 3-shot camera. The object is an **imagined visual language**, not a physically manufactured or certified Aetheris device, a licensed industrial product, or a reviewed photorealistic hero.

## Build-time control

- `app/blender-preview/page.tsx` remains in a clear non-WebGL unavailable state unless the build explicitly sets `NEXT_PUBLIC_E6_BLENDER_PREVIEW=1`. This route has no navigation link from the existing site.
- Actual source authoring uses the checked-in `blender/author_command_module.py` with Ubuntu-packaged Blender 4.0.2. The original `.blend` and two GLBs are regenerated on a fresh CI runner. The no-UV candidate is accepted only after previous strict ordered-triangle geometry and PBR metadata parity, a size reduction and SHA-256 manifest/source checks.
- `scripts/stage_blender_preview.py` copies the validated candidate only to the isolated ignored `public/e6-blender-preview.glb`. It denies path symlinks, absent source, wrong hash, altered shader/geometry metadata and overwrites. The copy is not a signed supply-chain attestation: hashes establish consistency with checked-in CI source and artifacts, not independent authorship/legal approval.
- Source selection in `Scene3D` is a reviewed two-value enum. No user-entered asset URL, remote GLB fetch, new npm dependency, model call, or external image/texture is introduced. The normal `/` route still uses `/research-panel.glb`.
- The default static export is not expected to contain a preview binary. The opt-in CI export includes only its own locally validated preview GLB, and `next-studio/.gitignore` excludes the generated artifact from source control.

## Verification

Before acceptance: six offline negative/positive provenance and build-gate tests, inherited E6 source/Blender/GLB tests, tracked-lock `npm ci`, Next.js typecheck, and an actual enabled-flag build on the exact PR HEAD. Playwright Chromium/SWIFTSHADER tests desktop/mobile arrival/material/precision/reverse (eight screenshots), reduced-motion static fallback with zero GLB requests (one screenshot), no horizontal overflow and no external resource fetches; root fixture remains unchanged. Preserve a source/preview report and screenshots as a short-lived GitHub Actions artifact. Inspect visuals separately after CI, correcting obvious readability/model placement defects before considering any wider adoption.

## Remaining gates

Still **NO** independent art-direction approval, material/lighting color calibration, actual GPU FPS/VRAM/thermal measurement, Power/ARM device profiling, real production photo/video authenticity evaluation, rigorous WCAG 2.2 AA audit, commercial dependency license approval, release artifact legal review or Stage45 independent certification. No company/agent activation, main merge or production deployment. Stage45 remains NO_GO.
