# E6 W3-B — Brand-specific first-party Blender asset

**1 November 2026 · Stacked research only · Stage 45 NO_GO**

## Provenance / exact branch topology

- Runtime W3-A: PR #260, draft, parent source SHA `8718c0b6ed5863fbbf9632a3221ccac64961adea`, CI independently verified.
- This W3-B change uses its own branch `feat/e6-w3b-signal-fold-original-asset-20261101` and a stacked **draft PR** targeting W3-A, not protected `main`.
- The existing W2-A, W2-B, W2-C and W3-A sources and preview directories are retained without alteration.
- Separate Runtime SSE/history issue #23, Platform authorization issue #251 and Stage45 owner-PC/security gates remain independent and unresolved.

## User-oriented result

W3-A proved the actual Aetheris command-module Blender asset was valid and source-bound, but it is **not an Arcloom product**. W3-B creates a distinct, original human-authored procedural Blender concept for the previously fictional **Signal Fold** object described on Arcloom's `/studies/signal-fold/` page.

The design consists of seven individually authored parts with machined/beveled finishes: porcelain footing, titanium lower wing, inclined graphite fold, upper alloy wing, vermilion signature inlay, porcelain index and elastomer underside. Five source-defined physically based materials are exported by actual reviewed Blender 4.0.2. No third-party media, model downloads, imported textures, cloud models or paid keys. This is a fictional material/geometry exploration, not a claim of a manufactured or commercially validated product.

### Implementation

- `blender/w3b_author_signal_fold.py`: fixed-input, no-network Blender authoring source; produces actual `arcloom-signal-fold.blend`, `arcloom-signal-fold.glb`, and hash-bound authoring manifest in ignored `target/e6-studio/w3b-signal-fold`. Fails rather than overwrites.
- `w3b_verify_signal_fold.py`: reuses existing structural GLB parser; binds source-script, Blender file and GLB SHA-256, byte size, 7 object names, 5 material names/PBR bounds, vertex budget, no external images, no unreviewed extensions, no animations/skins, and all-false release/approval fields.
- `w3b_stage_signal_fold.py`: rejects an invalid W3-A provenance chain and unsafe files, then copies previously staged W3-A preview into a new `target/e6-w3b-arcloom` directory. It adds a separate brand-specific GLB and JSON integrity manifest and an accessible, explicit download/provenance section on `/studies/signal-fold/`. The original W3-A/W2-C preview remains unchanged and repeat staging is denied. The route is **not a WebGL or Three.js renderer**; W4 integration is separate.
- `test_w3b_signal_fold.py`: real geometry/source tests for new authoring and asset contracts, tamper/forgery/material negatives, symlink and bad parent evidence denial, no-overwrite, preview isolation and honest status fields.
- `w3b_browser_proof.mjs`: headless Chromium desktop and mobile with JavaScript disabled; navigates actual Signal Fold page, verifies disclosure, fetches the real original GLB over localhost and recomputes SHA-256 from downloaded bytes, checks horizontal overflow and external requests.
- `.github/workflows/runtime-e6-w3b-signal-fold.yml`: exact-PR-head read-only CI rebuilds W3-A dependencies, then newly authored Blender asset, verifies provenance/negative tests, inherited Next.js build, and browser checks. **Do not mark PASS until reviewing the exact-run result.**

## Local WSL2 preview (optional)

No local IP server is necessary to progress GitHub development. In a **new clean W3-B checkout** (never delete or reuse previous worktrees), execute from repo root:

```bash
PYTHONPATH=/usr/lib/python3/dist-packages blender --background --factory-startup --python-use-system-env --python-exit-code 17 --python tools/enterprise/e6_studio/next-studio/blender/author_command_module.py
PYTHONPATH=/usr/lib/python3/dist-packages blender --background --factory-startup --python-use-system-env --python-exit-code 17 --python tools/enterprise/e6_studio/next-studio/blender/export_no_uv_candidate.py
python3 tools/enterprise/e6_studio/next-studio/blender/compare_glb_variants.py
python3 tools/enterprise/e6_studio/next-studio/blender/verify_detailed_module.py
cd tools/enterprise/e6_studio/next-studio
node w2c_generate_preview.mjs
python3 w3a_stage_provenance.py
cd ../../../../..
PYTHONPATH=/usr/lib/python3/dist-packages blender --background --factory-startup --python-use-system-env --python-exit-code 17 --python tools/enterprise/e6_studio/next-studio/blender/w3b_author_signal_fold.py
python3 tools/enterprise/e6_studio/next-studio/w3b_verify_signal_fold.py
python3 tools/enterprise/e6_studio/next-studio/w3b_stage_signal_fold.py
python3 -m http.server 8770 --bind 127.0.0.1 --directory tools/enterprise/e6_studio/next-studio/target/e6-w3b-arcloom
```

Open Windows Chrome at `http://127.0.0.1:8770/studies/signal-fold/` and use the research GLB/provenance download links. Press `Ctrl+C` to stop. In this preview **there is still no rendered 3D scene**. The source is preserved in GitHub; the binary is generated locally and in CI, not committed as production media.

## Open gates

- W3-B source authenticity and structural integrity are *testable*, but true artistic quality, original rights review, product plausibility, accessibility of future renderer, physical GPU and real customer fit remain unapproved.
- W4: actual GLB load, camera/lighting, coherent scrolling, native scroll fallback, reduced-motion/keyboard/non-WebGL scenarios, material provenance hash validation and device performance. Prefer the existing trusted opt-in R3F/GSAP research implementation; avoid blindly importing a new runtime or weakening static CSP.
- W5: a real model must generate new code/design from held-out briefs; deterministic human-authored scripts do not qualify.
- W6: independent accessibility, security, performance, device and creative reviews, with explicit owner approval.
- E6 Task7/8, E3/E4/E5, runtime authorization #23, platform authorization #251, owner-PC hardware and **Stage45 NO_GO** remain in force. No merge/deploy/release/secret exposure/privilege escalation.
