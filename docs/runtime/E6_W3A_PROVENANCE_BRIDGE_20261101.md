# E6 W3-A — Source-bound original Blender asset staging

**1 November 2026 · Research only · NO_GO · Never production-deploy**

## Source-of-truth lineage

This W3-A bridge is **stacked on draft PR #259** at the independently validated W2-C HEAD `10a433786b246f6bf168e5165b9a664d8f23c9ea`. Original W2-A PR #256, W2-B PR #258, and W2-C PR #259 remain isolated, unchanged, unmerged and not deployed. Runtime and Platform protected `main` must not change.

**Important discovery:** The repository already has original Blender 4.0 authoring source, Blender `.blend` output, an actual exported GLB, an optimized no-UV candidate, byte/mesh/PBR inspectors, provenance manifests, and prior SwiftShader research QA. Reuse these; do not create substitute asset art, use external model downloads, misattribute original ownership or claim real GPU acceptance.

## W3-A implementation

- `tools/enterprise/e6_studio/next-studio/w3a_stage_provenance.py` is a fixed-input, fixed-output, fail-closed bridge. It reads the pre-existing local authoring source, generated `.blend`, original GLB, no-UV GLB, source-bound manifest, geometry parity report, and detailed design report from `target/e6-studio/blender-proof/`. It invokes the existing reviewed `validate_blender_glb.inspect_glb` and `verify_detailed_module.inspect_design`.
- Rejects absent/symlinked/oversized inputs, source and data digest mismatches, non-equivalent mesh/material/triangle conditions, unoptimized candidate, forged asset rights/release approvals, malformed W2-C release evidence, output path reuse, and preview symlinks. Emits the candidate only after all checks pass.
- Copies the **existing generated W2-C site** into a new write-once `target/e6-w3a-arcloom`; the original `target/e6-w2c-arcloom` stays untouched. Inserts only one navigable `/asset-lab/` link into the fictional archive, creates an explicitly research-only provenance page and stages `/assets/aetheris-command-module-no-uv.glb` + `/assets/w3a-provenance.json`.
- **No 3D scene is rendered by W3-A**, and the asset is explicitly *not* a model of the Arcloom concepts. It is a conceptual **Aetheris command-module** from first-party code, not a physical product photograph or existing customer brief. Real cinematic rendering belongs to W4 and proper brand-specific assets must be authored and reviewed.
- `test_w3a_asset_provenance.py`: eight negative/integration tests, including SHA mutations, forged release permissions, symlink refusal, byte matching, first-party provenance, and no-overwrite/site preservation. The tests run only after a **real Blender export**, not against mocked geometry.
- `w3a_browser_proof.mjs`: headless Chromium validates the local asset-lab and bytes of the actual staged GLB against the independently generated SHA in desktop/mobile; external network blocked, JavaScript disabled, no rendering claims.
- `.github/workflows/runtime-e6-w3a-provenance.yml`: exact-head, read-only, SHA-pinned actions, reviewed Ubuntu Blender `4.0.2+dfsg-1ubuntu8`, Python/Node/Next inherited regression, Playwright `1.55.0`, seven-day review artifact retention. **CI only proves what actual run logs attest, not what workflow source intends**.

## WSL2 reproducible local use — optional, not required for continued GitHub work

Use a **new clean detached worktree** from this branch; do not delete or switch the W2-C preview. W3-A requires the first-party Blender proof to be generated locally; it does not fetch models or download web assets.

From the repository root of the new W3-A checkout, with Ubuntu 24.04 Blender 4.0.2 and Python 3 installed:

```bash
PYTHONPATH=/usr/lib/python3/dist-packages blender --background --factory-startup --python-use-system-env --python-exit-code 17 --python tools/enterprise/e6_studio/next-studio/blender/author_command_module.py
PYTHONPATH=/usr/lib/python3/dist-packages blender --background --factory-startup --python-use-system-env --python-exit-code 17 --python tools/enterprise/e6_studio/next-studio/blender/export_no_uv_candidate.py
python3 tools/enterprise/e6_studio/next-studio/blender/compare_glb_variants.py
python3 tools/enterprise/e6_studio/next-studio/blender/verify_detailed_module.py
cd tools/enterprise/e6_studio/next-studio
node w2c_generate_preview.mjs
python3 -m unittest -v test_w3a_asset_provenance
python3 w3a_stage_provenance.py
python3 -m http.server 8769 --bind 127.0.0.1 --directory target/e6-w3a-arcloom
```

Windows Chrome (optional): `http://127.0.0.1:8769/asset-lab/`. Stop with `Ctrl+C`; local servers are unnecessary for GitHub PR/CI. Existing dirs intentionally fail closed on repeat; don't delete unrelated output.

## Honest evidence and remaining work

This research object may have 12 PBR meshes and geometry equivalence, but **does not represent independently certified ownership/license clearance**, prove Blender generated every future asset, or demonstrate photorealism, animation, GPU performance, user acceptance, accessibility compliance, or AI model originality.

**Next:** W3-B brand-specific source-verified asset manifest, approved references, clear usage rights, effective visual assets and original materials; W4 opt-in cinematic 3D browser integration with 2D and reduced-motion fallback; W5 held-out genuine model authored website/code evaluations; W6 multi-device independent acceptance. E6 Task 7/8, Runtime security #23, Platform security #251, Stage45 nine release gates and owner-PC hardware validation remain **NO_GO**. No production deployment, merge, secret collection, authority upgrade or protected main changes are allowed.
