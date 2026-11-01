# E6 W4-C — progressive cinematic delivery and measurable render budgets

**1 November 2026 · Review-only development · Stage45 NO_GO**

## Exact recovery and lineage

This is a **separate stacked child of W4-B draft PR #263**, fixed source SHA `dd17270bb5da08859c226479ce719974c9b353c2`. Runtime `main` `65a6262717adcd52ac8d8a16ed6f223e299fd74d` and Platform `main` `57ad3e0e602efa6feb275328254151b959a3499b` are protected and must stay unchanged. Prior W1, W2-A/B/C, W3-A/B, W4-A/B previews and draft PRs remain intact and unmerged.

## Problem solved

W4-B proved genuine first-party GLB rendering and three distinct camera viewpoints but delivered a large 738KB-ish uncompressed Three.js+GSAP runtime on page load *even when users never activated 3D*. The Blender static fallback was real, but only **16 CPU Cycles samples** made the research still visibly noisy. W4-C is a bounded, evidence-driven improvement, not an excuse to add decorative motion or claim measured mobile FPS.

## What is implemented

- `blender/w4c_render_signal_fold_still.py`: opens the **same original source-linked seven-part W3-B Blender design** with the existing studio lights and CPU renderer; uses **48 Cycles samples** and tighter orthographic composition. Keeps denoising disabled because the reviewed Ubuntu Blender 4.0.2 build lacks OpenImageDenoiser. Generates a distinct new source-sha-bound `target/e6-studio/w4c-signal-fold/signal-fold-still.png`. Three times as many samples are not a guarantee of perceived visual superiority: human review must decide.
- `w4c_bootstrap.mjs`: a small progressive first-party controller enabling the 3D button without loading Three.js or GSAP. It displays the actual Cycles still immediately and requests the expensive `/assets/signal-fold-engine.mjs` **only after a direct user click**; reduced-motion / no-JS users remain static and incur no 3D runtime/model request. Failed dynamic module import returns to static and permits intentional retry.
- `w4c_demand_viewer.mjs`: lazy-loaded W4-B camera and SHA-checked glTF engine, preserving original camera/keyframe compositions and native scroll. New **device pixel ratio cap 1.25**; resized backing store only when viewport dimensions or pixel density actually change, not on every ScrollTrigger update. This reduces maximum canvas pixel demand relative to DPR 1.5, but actual FPS, battery and thermal gains remain unmeasured on owner hardware.
- `w4c_stage_progressive.mjs`: new **fixed-input, no-overwrite** `target/e6-w4c-arcloom` output. Verifies original W4-B asset/GLB proof and exact 48-sample render/source SHA, PNG format/size and all-false approval before copying. Keeps original W4-B preview unchanged, replaces only this child preview’s studio image and first-party scene scripts, and removes the old eager-scene script from the child output. Hard limits: **bootstrap 12KB** and **optional scene 780KB**, both uncompressed; limits are not claims that those sizes are optimal.
- `test_w4c_progressive.mjs` validates exact source and output hashes, original PNG dimension 800×900, inherited route preservation, initial payload limit and all review/release flags false.
- `w4c_browser_proof.mjs` runs real software-Chromium desktop/mobile/keyboard/reduced-motion/no-JS/no-WebGL/tampered-GLB scenarios. Verifies **zero heavy engine and model requests before activation**, one engine request after click, model SHA before GLTF parsing, actual distinct camera screenshots without scroll-induced test artifacts, no redundant scroll canvas resizing, native content still working, safe fallbacks and no tested external network or browser console errors.
- `.github/workflows/runtime-e6-w4c-progressive.yml` regenerates original Blender 4.0.2 W3-A/B, W4-B and W4-C source scenes; checks inherited full compiler and scene tests, locked Next static build and exact PR head. CI outputs short-lived artifacts, never deploys or merges.

## Reproduce safely

A new clean W4-C detached worktree is required for local manual review; **do not reuse or delete previous W4-B worktrees**. Existing W4-A/B instructions generate all prerequisites. After the W4-B preview exists, generate the new 48-sample Blender still from repository root:

```bash
PYTHONPATH=/usr/lib/python3/dist-packages blender --background --factory-startup --python-use-system-env --python-exit-code 17 --python tools/enterprise/e6_studio/next-studio/blender/w4c_render_signal_fold_still.py
cd tools/enterprise/e6_studio/next-studio
node w4c_stage_progressive.mjs
node --test test_w4c_progressive.mjs
python3 -m http.server 8773 --bind 127.0.0.1 --directory target/e6-w4c-arcloom
```

Open `http://127.0.0.1:8773/studies/signal-fold/` and inspect initially static vs user-activated 3D, responsive layout and reduced motion. `Ctrl+C` stops server. The script requires pinned local `esbuild@0.25.12`, installed from existing project dependencies per W4-B; do not install arbitrary versions or bypass no-overwrite errors. **No local IP is necessary for GitHub CI.**

## Honest remaining gates

This milestone does not satisfy physical laptop/mobile GPU frame-time measurements, subjective cinematic design review, independent WCAG/keyboard audit, visual likeness/product manufacturability, third-party rights certification, model-authored AI website production, production security, or Stage45 release authorization. The E6 task7/8 issues, Runtime owner/project authorization #23 and Platform backend-service authorization #251 remain separate. W4-D can evaluate real-device performance and creative changes; W5 must demonstrate **genuine generative code creation on held-out briefs**; W6 independent review follows. **All production/owner approvals remain false.**
