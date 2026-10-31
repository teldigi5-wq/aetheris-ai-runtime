# Aetheris E6 Task 7 — Local V2 rAF cohort review

**31 October 2026 · Draft, synthetic CI + owner-only CLI. Not a release gate.**

## Verified owner observations (held privately, NOT checked in)

The owner ran the opt-in native Windows Chrome V2 capture twice on the same Intel UHD Graphics / D3D11 device and 1536×730 CSS viewport, DPR 1.25. The two self-reported V2 JSON files each contain three 360-interval rAF sequences with measured GSAP pinned range and forward/reverse progress 0 → 0.989 → 0. Both self-assessments show `diagnosticValid=true`, `screenBudgetMet=true`; aggregate maxima from privately reviewed files: worst P95 **16.9ms**, worst P99 **17.0ms**, absolute worst interval **21.5ms**, **0 / 2,160 >33.3ms**, no reported long tasks or WebGL context losses.

The optimized Blender research GLB generated in the owner's detailed E6 stage has **83,140 bytes**, SHA-256 `ab9177c28c28a6d32129455e05d1205cc2db3af0daf862a939814f2ceaa4d446`. An older Stage 5 ZIP's different 43,240-byte GLB was correctly rejected by the reviewer; **do not mix stage-specific assets**. The raw reports, renderer strings and local filesystem paths must not be published or committed.

These measurements are **browser requestAnimationFrame callback intervals, not measured GPU execution time or presented frame rate**. Source commit integrity and physical device identity are not authenticated by this report.

## Repeatable local review — no cloud account or secret required

This branch adds `tools/enterprise/e6_studio/next-studio/review_local_raf_evidence.mjs`, a zero-dependency Node 22 CLI and pure review function. It reads the locally built **current** `out/e6-blender-preview.glb` plus two to eight explicit V2 JSON files. Files must be regular, single-link, bounded, unchanged while opened. No network or disk writes are performed; JSON summary is written to stdout **only**. It computes SHA-256 of the actual staged binary and reports; compares it with each report's self-claimed asset SHA; reruns `local_browser_raf_policy.mjs`; compares the recomputed diagnostic/metrics with embedded assessments; rejects duplicate files and timestamps, mixed browser/viewport/renderer cohorts, V1 captures, missing GSAP traversal, software renderer signatures, misreported timestamps, and pacing budget violations.

From the checked-out research repo on WSL2, with both raw V2 files copied **locally** to a private user-controlled folder (not under Git):

```bash
cd ~/projects/aetheris-ai-runtime
nvm use 22
node tools/enterprise/e6_studio/next-studio/review_local_raf_evidence.mjs \
  --asset tools/enterprise/e6_studio/next-studio/out/e6-blender-preview.glb \
  --report "$HOME/e6-private-evidence/<first-v2-capture>.json" \
  --report "$HOME/e6-private-evidence/<second-v2-capture>.json"
```

Substitute **your own actual filenames**; do not type angle-bracket examples literally. If the GLB has not been staged, follow the documented E6 local build procedure. **Never substitute an older 43,240-byte module from a prior stage**. The CLI intentionally does not create `target/` or upload/download files.

A successful local diagnostic returns `kind=E6_LOCAL_RAF_COHORT_REVIEW_V1`, `diagnosticReviewValid=true`, `preliminaryRafScreenMet=true`, and permanently `task7Completed=false`, `task8Completed=false`, `stage45Go=false`. Its self-contained summary contains file hashes and aggregate numerical statistics but **not raw renderer strings, captured frame arrays or user filesystem paths**. Review it for privacy before sharing. A rejection exits nonzero and emits a clear `E6_LOCAL_RAF_REVIEW_BLOCKED` code; do not override it for convenience.

Important limitations: local copies + hashes can detect mismatches and some accidental corruption but cannot prove report authenticity if an adversary controls all inputs. Final-component symlinks/hardlinks and mid-read edits are denied, but parent-directory races and other untrusted local filesystems are not a fully resolved security boundary. Prefer a private immutable evidence folder. The 30-day capture freshness window is inherited from the V2 policy.

## Independent testing and release work still outstanding

1. **Chrome Performance trace during actual 3D motion** with verified pinned progress, Main/Compositor/Frames inspection, clearly documented DevTools overhead and device power mode. Avoid equating browser rAF intervals with GPU time.
2. **Physical GPU/VRAM/thermals/power** with separately corroborated vendor-appropriate tools and sustained workload; existing Windows Chrome advertises Intel UHD Graphics and no active discrete GPU. Do not assume a different laptop's NVIDIA hardware is involved.
3. **Real device/browser matrix** including mobile Android/iOS, varied viewport/DPR, touch/keyboard, reduced-motion, WebGL context loss and static recovery.
4. **E6 Task 8 independent review** for real design composition, accessibility/assistive technology, licensing, threat model, reproducible source provenance, security, and visual sign-off, all by appropriately identified humans.

**No PR merge or production deployment is authorized. Task 7 incomplete, Task 8 incomplete, Stage 45 NO-GO.**
