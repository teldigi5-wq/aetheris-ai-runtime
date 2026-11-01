# E6 W4-B — Source-bound cinematic art direction and actual Blender still

**1 November 2026 · Draft / owner-review only · Stage45 NO_GO**

## Checkpoint and non-destructive branch topology

W4-B is a strictly stacked change on **W4-A PR #262** at exact verified parent SHA `d3fb1eaef26f48b910e60ac2383f07fcfa933954`. Prior W1/W2-A/B/C/W3-A/B/W4-A PRs and their separate preview worktrees stay intact. Runtime and Platform protected main cannot be changed by these scripts.

## Engineering change

The W4-A preview proved actual GLB rendering but its static fallback was a CSS silhouette and its camera used a single orbit expression. W4-B makes a defensible **visual research upgrade**, not a claim of photorealistic production acceptance.

1. **Actual Blender still:** `blender/w4b_render_signal_fold_still.py` opens the independently verified original Signal Fold Blender project, adds *studio-only* plinth, area lights, backdrop and camera, and executes **Blender Cycles CPU** at 800×900 (16 samples). It creates a real original-object PNG and sha-bound provenance report, with no imported photographs/textures or web assets. Plinth is a set prop, not added to the original seven-part GLB. Never call this a product photograph.
2. **Three-camera cinematic narrative:** `w4b_cinematic_viewer.mjs` replaces the original W4-A orbit with three bounded independently authored camera poses (silhouette, material and edge), interpolated with smoothstep and triggered only from ordinary GSAP ScrollTrigger native-scroll progress. Three.js glTF mesh continues using the exact source-verified seven-part GLB and local owned lighting; revised studio backing includes a presentation plinth, with no particle effects, noise gimmicks, scroll pin, Lenis, automatic GPU activation, forced smoothing, new customer data or keys.
3. **Runtime content integrity:** Before GLTFLoader parses the GLB, the client fetches the fixed same-origin file, computes SHA-256 using `crypto.subtle`, and compares it to the exact W3-B GLB source hash injected at build time. Tampering, unavailable cryptography or blocked WebGL results in readable static fallback; no remote asset is allowed. This is local research integrity verification, **not** origin authenticity or production supply-chain certification.
4. **Isolated write-once stage:** `w4b_stage_cinematic.mjs` reads the W4-A parent preview and newly generated Cycles source/PNG/manifest, confirms GLB and PNG hashes and reviewed dependencies, and copies into separate `target/e6-w4b-arcloom`. It swaps only the local scene bundle/CSS, uses the real Blender still as the static fallback, preserves all existing case-study chapters, and refuses overwrite. A separate all-false W4-B evidence record is emitted.
5. **Tests and visual record:** Four W4-B fixture integrity tests; six actual software-Chromium conditions: desktop, mobile, reduced-motion, JavaScript-disabled, WebGL unavailable, tampered GLB. Normal cases capture three camera-view screenshots, assert camera values change and pixels differ; tests verify zero outside requests, static Blender PNG hash, no horizontal overflow, explicit context-loss fallback and no unhandled browser errors. Screenshots are short-retention **review evidence** in `target/e6-studio/w4b-visual-evidence`, not independent art-direction approvals.
6. **Exact-head CI:** `.github/workflows/runtime-e6-w4b-cinematic.yml` regenerates the original W3-A/B Blender files and W4-B CPU still, runs inherited website checks, model/manifest validation, original W4-A browser regression and W4-B browser/visual evidence. Require independently checked GitHub workflow status and logs before claiming pass.

## Reproduction / review

No local preview IP is required for GitHub development. A **new** W4-B detached worktree can regenerate the W3-A/B prerequisites using the existing W3-B and W4-A stage documentation and reviewed Blender 4.0.2. Then from repository root:

```bash
PYTHONPATH=/usr/lib/python3/dist-packages blender --background --factory-startup --python-use-system-env --python-exit-code 17 --python tools/enterprise/e6_studio/next-studio/blender/w4b_render_signal_fold_still.py
cd tools/enterprise/e6_studio/next-studio
npm ci --ignore-scripts --no-audit --no-fund
npm install --no-save --no-package-lock --ignore-scripts --no-audit --no-fund esbuild@0.25.12
node w4a_stage_scene.mjs
node w4b_stage_cinematic.mjs
node --test test_w4b_art_direction.mjs
python3 -m http.server 8772 --bind 127.0.0.1 --directory target/e6-w4b-arcloom
```

Open `http://127.0.0.1:8772/studies/signal-fold/` and opt in to **Activate 3D study**. `Ctrl+C` stops the server. Do not delete preexisting worktree or target directories to bypass no-overwrite checks.

## Honest capability boundary

An actual CPU studio still and software-rendered 3D camera views are not proof of premium aesthetic acceptance, genuine photorealism, a physical prototype, hardware GPU performance, real-device responsiveness, complete WCAG 2.2 AA, third-party license certification, or a generated AI website. Those require further visual review and dedicated W4-C/W5/W6 engineering. All owner approval, license certification, visual approval, real GPU measurement, real AI authorship, release/production, Runtime owner-scope Issue #23, Platform backend auth Issue #251, E6 Task7/8 and Stage45 attestations remain gated **NO_GO**. No merge or deployment.
