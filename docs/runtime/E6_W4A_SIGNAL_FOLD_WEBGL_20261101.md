# E6 W4-A — Verified branded GLB in optional real WebGL scene

**1 November 2026 · Research-only draft · Stage 45 NO_GO.**

## Exact stacked lineage

W1 draft PR #255 → W2-A #256 → W2-B #258 → W2-C #259 → W3-A #260 → **W3-B #261** (verified HEAD `77d943bbef46bdba3b653b48ca0cbd9e6855c85c`) → this W4-A child branch. Parent branches and protected runtime/platform `main` are not mutated or merged.

## Real functional capability

The fictional Arcloom Objects Signal Fold concept now gets an **opt-in interactive Three.js/GLTFLoader scene** using the *actual* 72,724-byte source-verified, first-party Blender 4.0.2 GLB from W3-B. A real camera passes through arrival, material and detail phases. Original PBR surfaces are lit by bounded key/fill/rim lighting with ACES tone mapping; this is not CSS art impersonating a 3D scene.

- `w4a_signal_fold_viewer.mjs` is a locally bundled Three.js `0.186.1` and GSAP `3.15.0` client-only scene. Rendering starts **only after a user activates 3D**. Native scrolling stays intact, without Lenis, pin, scroll snap or forced smooth scroll; GSAP ScrollTrigger drives a single deterministic camera progress playhead. Rendering is demand-only on scroll/resize/activation and capped at DPR 1.5. A pause/resume camera control is available.
- `w4a_signal_fold_scene.css` defines the original editorial lighting-stage composition and mobile-first layout without hiding overflow.
- The original authored static chapter narrative and a visible CSS silhouette remain **fully accessible without JavaScript**, with reduced motion active, or when WebGL is unavailable. JavaScript-free/reduced-motion users do not initiate a GLB request. On WebGL context loss the renderer is explicitly disposed and the fallback becomes visible, with no automatic retry loop.
- `w4a_stage_scene.mjs` is an *exact-input*, *write-once* build script. It refuses symlinks/untrusted input, unapproved W3-B proof, asset byte/hash mismatch, dependency-version changes, user-selected paths or overwrites. It copies W3-B into **a new** `target/e6-w4a-arcloom` directory without modifying W2/W3 previews. Local `esbuild@0.25.12` bundles Three + GSAP with inline legal notices. Only the standalone study route gets a scoped `connect-src 'self'` update; strict local `script-src 'self'`, noindex and default-deny CSP remain.
- `test_w4a_scene.mjs` binds generated model/bundle/CSS hashes and checks fallback and all-false authority.
- `w4a_browser_proof.mjs` uses actual Chromium with software SwiftShader: desktop and mobile loaded model/camera/progress/hash, reduced motion with zero GLB loads, no-JavaScript route, no-WebGL emulation, context loss with static fallback, responsive overflow, no external requests and uncaught errors.
- `.github/workflows/runtime-e6-w4a-webgl-story.yml` runs exact SHA, the full inherited W1–W3B tests, actual Blender export, pinned dependencies, isolated bundler, no-overwrite and browser matrix. **Do not assert PASS until the exact-run conclusion and logs are inspected.**

## Local testing / optional

No local IP servers are required for coding or GitHub CI. To reproduce in a separate clean worktree, regenerate the W3-A/W3-B source artifacts according to `E6_W3B_SIGNAL_FOLD_20261101.md`, then, in `tools/enterprise/e6_studio/next-studio`:

```bash
npm ci --ignore-scripts --no-audit --no-fund
npm install --no-save --no-package-lock --ignore-scripts --no-audit --no-fund esbuild@0.25.12
node w4a_stage_scene.mjs
node --test test_w4a_scene.mjs
python3 -m http.server 8771 --bind 127.0.0.1 --directory target/e6-w4a-arcloom
```

Browse `http://127.0.0.1:8771/studies/signal-fold/`. Select **Activate 3D study** to request the actual model. Close via `Ctrl+C`. Never delete previous worktrees or generated previews to resolve `EEXIST` errors.

## Honest remaining gates

W4-A is **real WebGL software-browser research**, not owner-PC physical GPU performance proof, exceptional/hyper-realistic art approval, manufacturing authenticity, independent license certification or production WebGL security certification. W4-B richer camera/lighting design and representative non-WebGL still-image assets, W5 truly model-generated held-out site work, W6 cross-browser, screen reader, mobile GPU and senior designer acceptance remain to be done. The runtime owner/project SSE authorization issue #23, platform direct-backend authorization #251, E6 Task 7/8, production attestation and Stage45 NO_GO stay untouched. No merge, deploy, owner PC agent activation, secret exposure or access escalation.
