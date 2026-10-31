# Enterprise E6 Task 7 — Local physical-device frame-pacing evidence

**31 October 2026 · DRAFT EVIDENCE HARNESS · Stage45 NO_GO**

## Purpose and truthful measurement boundary

GitHub-hosted runners and Chromium SwiftShader do **not** measure the actual GPU of the owner. Task 7 can therefore deliver a tested local capture protocol and evaluator, but cannot mark GPU/device performance complete from CI. The new `owner_gpu_benchmark.mjs` must be run **on the owner's actual workstation, with a visible locally installed Google Chrome window**; it never uploads results. It measures browser `requestAnimationFrame` (rAF) intervals while the real page scrolls forward/reverse through the three authored GSAP/React Three Fiber acts. It does **not** measure GPU draw-call duration, GPU utilization, true VRAM use, physical thermals, energy, or a production Core Web Vitals distribution.

## Source and safety policy

- The task is stacked on verified PR #243 (`2e0e724626f969959de529a3f4a65b5ea9a0afaf`). It does not change the camera, design, source geometry, dependency lock, original root page, public hosting, or release gates.
- The CLI requires the literal `--confirm-local-hardware` argument; rejects `CI`/`GITHUB_ACTIONS` and does not execute in GitHub Actions. Chrome launches `headless:false`, `channel:'chrome'` without SwiftShader flags or emulation. This is an owner test, not a remote browser job.
- The loopback-only native Node HTTP server serves the existing `next-studio/out` static-export build, refuses traversal paths, uses no third-party static-server dependency and contacts no remote endpoint. Playwright blocks and counts all non-loopback page requests.
- The prebuilt `out/e6-blender-preview.glb` must match the SHA-256 digest from `target/e6-studio/blender-preview-stage.json`. A missing or stale model is rejected. The current `origin` must point to `teldigi5-wq/aetheris-ai-runtime`, the tracked checkout must have no uncommitted changes, and the exact HEAD commit is captured. Traces older than 30 days are rejected by the policy. Do not edit or substitute a model to force a pass.
- Three runs each collect at least 180 frame intervals during a bidirectional scroll workload; capture minimum/maximum ScrollTrigger progress, reverse scroll, long-task counts and browser errors. Only 1440×900 owner desktop Chrome is the scripted workload. Every result is written locally, never automatically posted to GitHub, Vercel or ChatGPT.
- `owner_gpu_policy.mjs` rejects empty/invalid samples, incomplete three-run evidence, headless/emulated/CI captures, missing unmasked WebGL2 renderer, software renderer strings (SwiftShader, WARP, llvmpipe, lavapipe), suspicious source digests, errors/external requests, missing reverse motion, excessive long tasks and poor frame pacing.
- The default **research** frame-pacing thresholds are rAF p95 ≤33.3 ms, p99 ≤50 ms, at most 5% intervals over 33.3 ms and no long task over 200 ms. They are screening values, **not** a verified physical GPU standard or Core Web Vitals pass. A 60-Hz browser's rAF is not a GPU timer. Actual refresh rate, power mode and foreground tab activity must be recorded separately.
- A renderer identifier string is not cryptographic attestation; a visible Chrome window and unmasked renderer may still reflect a virtual machine or driver translation. Independent device/environment corroboration remains mandatory before owner acceptance.

## Owner local execution (only with real installed Chrome/Blender)

Run from a fresh *checkout of the draft PR #244 branch*. Keep changes outside protected main. Verify you are working in `teldigi5-wq/aetheris-ai-runtime` and not the separate `aetheris-platform` or BoundaryLab repo.

```bash
git fetch origin
git switch feat/e6-owner-gpu-benchmark-20261031
git rev-parse HEAD
python3 -m pip --version
node --version
blender --version
```

Use Python 3.12, Blender 4.0.2 with NumPy, Node 22 and actual local Google Chrome; exact system setup may vary on Windows vs WSL2. On Ubuntu/WSL2 with Ubuntu-packaged Blender use the same `PYTHONPATH=/usr/lib/python3/dist-packages` prefix used by the checked-in CI for the two Blender commands. **WSL2 often exposes a translated/virtual GPU; if your WSL Chrome shows WARP/SwiftShader or software rendering, this cannot qualify as physical hardware evidence.**

```bash
PYTHONPATH=/usr/lib/python3/dist-packages blender --background --factory-startup --python-use-system-env --python-exit-code 17 --python tools/enterprise/e6_studio/next-studio/blender/author_command_module.py
PYTHONPATH=/usr/lib/python3/dist-packages blender --background --factory-startup --python-use-system-env --python-exit-code 17 --python tools/enterprise/e6_studio/next-studio/blender/export_no_uv_candidate.py
python3 tools/enterprise/e6_studio/next-studio/blender/compare_glb_variants.py
python3 tools/enterprise/e6_studio/next-studio/blender/verify_detailed_module.py
cd tools/enterprise/e6_studio/next-studio
npm ci --ignore-scripts --no-audit --no-fund
python3 scripts/build_research_glb.py
python3 scripts/stage_blender_preview.py
npm run typecheck
NEXT_PUBLIC_E6_BLENDER_PREVIEW=1 npm run build
cd ../../../..
npm install --prefix tools/enterprise/e6_studio --no-save --no-package-lock --ignore-scripts --no-audit --no-fund playwright@1.55.0
node tools/enterprise/e6_studio/next-studio/owner_gpu_benchmark.mjs --confirm-local-hardware
```

Do not run the final command in CI. It starts an ordinary visible local Chrome window on your workstation; you may keep it in the foreground. If your machine only has Windows Chrome, run using a compatible **native Windows** local installation/toolchain rather than assuming Chrome is accessible inside WSL2. For PowerShell, set the build flag with `$env:NEXT_PUBLIC_E6_BLENDER_PREVIEW='1'` before `npm run build`, then remove that environment variable afterwards. If a native Chrome launch is unsupported, record the blocker rather than switching to headless Chromium, WebGL software rendering or a cloud machine.

## Generated evidence and review

Local files under `target/e6-studio/owner-gpu-local/`:

- `owner-local-frame-trace.json` contains raw browser frame intervals, no machine serial, device hostname, account name, GPS/IP or screenshots.
- `assessment.json` includes the independently computed research result, per-run p50/p95/p99, dropped interval ratios and explicit permanent `releaseGo:false`, `stage45Go:false`.

The harness refuses to overwrite existing reports. For another run, preserve/rename or archive prior evidence intentionally before re-running; do not delete the original unreviewed trace. If sharing, inspect the renderer string and metadata yourself first; no automatic upload is performed.

## Outstanding Task 7 acceptance

The local desktop rAF trace alone does not prove a **physical GPU acceptance** or finished E6 Task 7. Capture and independently inspect real desktop and mobile **hardware** profiles with OS/browser/build versions, battery/power mode and ambient temperature; measured CPU/GPU frame time distributions using an actual GPU profiler when available; VRAM/memory pressure; sustained 10–15-minute workload with thermal/power logging and repeat runs; device responsiveness under load, context-loss/restore test on actual driver, plus cross-browser/mobile accessibility and quality review. `nvidia-smi` or a trusted OS/hardware monitor may help on supported NVIDIA systems; label its data separately and do not fabricate unavailable counters. No such measurements have been performed by this PR or GitHub CI.

**No independent acceptance, production deployment, protected-main merge, owner hardware signoff, E7/E8/E9 promotion or Stage45 GO is authorized by this harness.**
